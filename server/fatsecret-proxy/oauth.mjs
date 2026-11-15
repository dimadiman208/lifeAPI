// Shared OAuth 1.0a (RFC 5849) helpers for FatSecret — runtime-agnostic (WebCrypto: Workers, Node ≥ 18).

export const FS = {
  requestToken: 'https://authentication.fatsecret.com/oauth/request_token',
  authorize: 'https://authentication.fatsecret.com/oauth/authorize',
  accessToken: 'https://authentication.fatsecret.com/oauth/access_token',
  api: 'https://platform.fatsecret.com/rest/server.api',
};

/** RFC 3986 percent-encoding (unreserved: A-Z a-z 0-9 - . _ ~). */
export function pct(s) {
  return encodeURIComponent(String(s)).replace(/[!'()*]/g, c => '%' + c.charCodeAt(0).toString(16).toUpperCase());
}

export function baseString(method, url, params) {
  const pairs = Object.entries(params).map(([k, v]) => [pct(k), pct(v)]);
  pairs.sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0));
  const norm = pairs.map(([k, v]) => `${k}=${v}`).join('&');
  return `${method.toUpperCase()}&${pct(url)}&${pct(norm)}`;
}

export async function hmacSha1Base64(key, text) {
  const enc = new TextEncoder();
  const k = await crypto.subtle.importKey('raw', enc.encode(key), { name: 'HMAC', hash: 'SHA-1' }, false, ['sign']);
  const sig = new Uint8Array(await crypto.subtle.sign('HMAC', k, enc.encode(text)));
  let bin = '';
  for (const b of sig) bin += String.fromCharCode(b);
  return btoa(bin);
}

function nonce() {
  const a = new Uint8Array(16);
  crypto.getRandomValues(a);
  return Array.from(a, b => b.toString(16).padStart(2, '0')).join('');
}

/** Returns all params (request + oauth_* + oauth_signature) ready to send. */
export async function sign(method, url, params, consumerKey, consumerSecret, tokenSecret = '', extraOauth = {}) {
  const all = {
    ...params,
    oauth_consumer_key: consumerKey,
    oauth_signature_method: 'HMAC-SHA1',
    oauth_timestamp: Math.floor(Date.now() / 1000).toString(),
    oauth_nonce: nonce(),
    oauth_version: '1.0',
    ...extraOauth,
  };
  const key = `${pct(consumerSecret)}&${pct(tokenSecret || '')}`;
  all.oauth_signature = await hmacSha1Base64(key, baseString(method, url, all));
  return all;
}

export function formEncode(params) {
  return Object.entries(params).map(([k, v]) => `${pct(k)}=${pct(v)}`).join('&');
}

export function parseForm(text) {
  const out = {};
  for (const part of text.split('&')) {
    if (!part) continue;
    const i = part.indexOf('=');
    const k = decodeURIComponent((i < 0 ? part : part.slice(0, i)).replace(/\+/g, ' '));
    out[k] = i < 0 ? '' : decodeURIComponent(part.slice(i + 1).replace(/\+/g, ' '));
  }
  return out;
}

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { 'content-type': 'application/json; charset=utf-8' } });

const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

/**
 * The whole proxy as a fetch handler: (Request, env) → Response.
 * env: FATSECRET_CONSUMER_KEY, FATSECRET_CONSUMER_SECRET, optional APP_SHARED_KEY, optional APP_SCHEME_CALLBACK.
 */
export async function handle(req, env) {
  const url = new URL(req.url);
  const ck = env.FATSECRET_CONSUMER_KEY, cs = env.FATSECRET_CONSUMER_SECRET;
  if (!ck || !cs) return json({ error: { code: 'server_config', message: 'FATSECRET_CONSUMER_KEY / FATSECRET_CONSUMER_SECRET are not set on the server' } }, 500);
  const appCallback = env.APP_SCHEME_CALLBACK || 'lifeapi://fatsecret/callback';

  // Browser lands here after the user approves on fatsecret.com — no app key (it's a browser).
  if (req.method === 'GET' && url.pathname === '/oauth/callback') {
    const token = url.searchParams.get('oauth_token') || '';
    const verifier = url.searchParams.get('oauth_verifier') || '';
    const target = `${appCallback}?oauth_token=${pct(token)}&oauth_verifier=${pct(verifier)}`;
    const html = `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>LifeAPI · FatSecret</title>
<body style="margin:0;background:#0A0B0D;color:#EDEEF0;font:16px system-ui,sans-serif;display:flex;min-height:100vh;align-items:center;justify-content:center">
<div style="max-width:360px;padding:24px;text-align:center">
<h2 style="margin:0 0 8px">FatSecret connected</h2>
<p style="color:#A1A4AB">Return to LifeAPI to finish. If the app didn't open, tap the button or enter this code in the app:</p>
<p style="font:600 24px monospace;letter-spacing:.08em;color:#D3F36B">${esc(verifier)}</p>
<a href="${esc(target)}" style="display:block;padding:14px;border-radius:16px;background:#D3F36B;color:#0A0B0D;font-weight:600;text-decoration:none">Open LifeAPI</a>
</div><script>location.href=${JSON.stringify(target)}</script></body>`;
    return new Response(html, { headers: { 'content-type': 'text/html; charset=utf-8' } });
  }

  if (req.method === 'GET' && url.pathname === '/health') return json({ ok: true });

  if (env.APP_SHARED_KEY && req.headers.get('x-app-key') !== env.APP_SHARED_KEY) {
    return json({ error: { code: 'app_key', message: 'Wrong or missing app key' } }, 401);
  }
  if (req.method !== 'POST') return json({ error: { code: 'not_found', message: 'Not found' } }, 404);

  let body = {};
  try { body = await req.json(); } catch { /* empty body is fine for /oauth/start */ }

  try {
    if (url.pathname === '/oauth/start') {
      const callback = `${url.origin}/oauth/callback`;
      const p = await sign('POST', FS.requestToken, {}, ck, cs, '', { oauth_callback: callback });
      const r = await fetch(FS.requestToken, {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded' },
        body: formEncode(p),
      });
      const text = await r.text();
      const f = parseForm(text);
      if (!r.ok || !f.oauth_token) return json({ error: { code: 'request_token', message: `FatSecret: ${text.slice(0, 300)}` } }, 502);
      return json({
        authorize_url: `${FS.authorize}?oauth_token=${pct(f.oauth_token)}`,
        token: f.oauth_token,
        token_secret: f.oauth_token_secret,
      });
    }

    if (url.pathname === '/oauth/access') {
      const { token, token_secret, verifier } = body;
      if (!token || !verifier) return json({ error: { code: 'bad_request', message: 'token and verifier are required' } }, 400);
      const p = await sign('GET', FS.accessToken, {}, ck, cs, token_secret || '', { oauth_token: token, oauth_verifier: verifier });
      const r = await fetch(`${FS.accessToken}?${formEncode(p)}`);
      const text = await r.text();
      const f = parseForm(text);
      if (!r.ok || !f.oauth_token) return json({ error: { code: 'access_token', message: `FatSecret: ${text.slice(0, 300)}` } }, 502);
      return json({ token: f.oauth_token, token_secret: f.oauth_token_secret });
    }

    // Public food database (no user token): 2-legged OAuth 1.0 — signed with the consumer secret only.
    // Only read-only catalogue methods are allowed, so the proxy can't be used to reach anyone's diary.
    if (url.pathname === '/public') {
      const { params } = body;
      const allowed = new Set(['foods.search', 'foods.search.v2', 'foods.search.v3', 'food.get', 'food.get.v2', 'food.get.v3', 'food.get.v4', 'foods.autocomplete', 'foods.autocomplete.v2']);
      if (!params || !allowed.has(params.method)) {
        return json({ error: { code: 'bad_request', message: 'params.method must be one of: ' + [...allowed].join(', ') } }, 400);
      }
      const clean = {};
      for (const [k, v] of Object.entries(params)) if (!k.startsWith('oauth_')) clean[k] = String(v);
      const p = await sign('POST', FS.api, clean, ck, cs, '');
      const r = await fetch(FS.api, {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded' },
        body: formEncode(p),
      });
      const text = await r.text();
      return new Response(text, { status: r.status, headers: { 'content-type': r.headers.get('content-type') || 'application/json' } });
    }

    if (url.pathname === '/api') {
      const { token, token_secret, params } = body;
      if (!token || !token_secret || !params || !params.method) {
        return json({ error: { code: 'bad_request', message: 'token, token_secret and params.method are required' } }, 400);
      }
      const clean = {};
      for (const [k, v] of Object.entries(params)) if (!k.startsWith('oauth_')) clean[k] = String(v);
      const p = await sign('POST', FS.api, clean, ck, cs, token_secret, { oauth_token: token });
      const r = await fetch(FS.api, {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded' },
        body: formEncode(p),
      });
      const text = await r.text();
      return new Response(text, { status: r.status, headers: { 'content-type': r.headers.get('content-type') || 'application/json' } });
    }
  } catch (e) {
    return json({ error: { code: 'upstream', message: String(e && e.message || e) } }, 502);
  }
  return json({ error: { code: 'not_found', message: 'Not found' } }, 404);
}
