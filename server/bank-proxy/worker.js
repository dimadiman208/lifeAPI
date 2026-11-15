// LifeAPI ⇄ Enable Banking proxy (read-only account information).
// Holds the application's private key as a secret, signs the JWT and forwards a small whitelist of calls.
// Stateless: nothing is stored, request/response bodies are never logged.
import { importKey, makeJwt } from "./jwt.mjs";

const API = "https://api.enablebanking.com";
let cached = null; // { token, exp } — per isolate, refreshed 5 min before expiry
let keyPromise = null;

async function jwt(env) {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.exp - 300 > now) return cached.token;
  keyPromise ??= importKey(env.ENABLEBANKING_PRIVATE_KEY);
  cached = await makeJwt(env.ENABLEBANKING_APP_ID, await keyPromise, now);
  return cached.token;
}

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });

/**
 * PSU headers for "user is present" calls. Enable Banking rule: send ALL headers a bank lists in
 * required_psu_headers, or none — partial sets fail with PSU_HEADER_NOT_PROVIDED. So we always send the full set.
 */
function psuHeaders(req) {
  const h = (n, d) => req.headers.get(n) || d;
  return {
    "psu-ip-address": h("cf-connecting-ip", "0.0.0.0"),
    "psu-user-agent": h("x-psu-user-agent", h("user-agent", "LifeAPI Android")),
    "psu-accept": h("x-psu-accept", "application/json"),
    "psu-accept-charset": "utf-8",
    "psu-accept-encoding": "gzip",
    "psu-accept-language": h("x-psu-accept-language", h("accept-language", "en")),
    "psu-referer": "https://lifeapi.local/",
  };
}

async function eb(env, method, path, body, psu) {
  const headers = { authorization: `Bearer ${await jwt(env)}`, accept: "application/json" };
  if (body !== undefined) headers["content-type"] = "application/json";
  if (psu) Object.assign(headers, psu);
  const r = await fetch(API + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await r.text();
  // Errors only (never success bodies — those hold bank data): status + Enable Banking's error description.
  if (!r.ok) console.log(`EB ${method} ${path.replace(/\/accounts\/[^/]+/, "/accounts/<uid>").split("?")[0]} -> ${r.status} ${text.slice(0, 600)}`);
  return new Response(text || "{}", { status: r.status, headers: { "content-type": "application/json", "cache-control": "no-store" } });
}

function callbackPage(params) {
  const code = params.get("code") || "";
  const state = params.get("state") || "";
  const err = params.get("error") || "";
  const deep = `lifeapi://bank/callback?${params.toString()}`;
  const esc = (s) => s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const html = `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>LifeAPI · bank</title>
<body style="margin:0;background:#0A0B0D;color:#EDEEF0;font:16px/1.5 system-ui,sans-serif;padding:32px 20px;max-width:520px">
<h1 style="font-size:24px">${err ? "Bank access was not granted" : "Back to LifeAPI"}</h1>
${err ? `<p style="color:#FF9A8F">${esc(err)} ${esc(params.get("error_description") || "")}</p>` :
`<p style="color:#A1A4AB">If the app didn't open by itself, tap the button or paste this code into LifeAPI → Money → Bank.</p>
<p><a href="${esc(deep)}" style="display:inline-block;background:#D3F36B;color:#0A0B0D;padding:12px 18px;border-radius:14px;font-weight:600;text-decoration:none">Open LifeAPI</a></p>
<p style="color:#8A8E96;font-size:13px">Code</p><code style="word-break:break-all;background:#121316;padding:10px;border-radius:10px;display:block">${esc(code)}</code>`}
<script>${err ? "" : `setTimeout(function(){location.href=${JSON.stringify(deep)}},150)`}</script>
</body>`;
  return new Response(html, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    const p = url.pathname.replace(/\/+$/, "") || "/";
    try {
      if (p === "/health") return json({ ok: true, configured: !!(env.ENABLEBANKING_APP_ID && env.ENABLEBANKING_PRIVATE_KEY && env.APP_SHARED_KEY) });
      if (p === "/callback") return callbackPage(url.searchParams);

      if (!env.APP_SHARED_KEY) return json({ message: "Server not configured: APP_SHARED_KEY missing" }, 500);
      if (req.headers.get("x-app-key") !== env.APP_SHARED_KEY) return json({ message: "Forbidden" }, 403);
      if (!env.ENABLEBANKING_APP_ID || !env.ENABLEBANKING_PRIVATE_KEY) return json({ message: "Server not configured: Enable Banking secrets missing" }, 500);

      const psu = psuHeaders(req); // full PSU header set, for banks that require it
      let m;
      if (p === "/aspsps" && req.method === "GET") {
        const q = new URLSearchParams();
        q.set("country", url.searchParams.get("country") || "LV");
        q.set("psu_type", "personal");
        return eb(env, "GET", `/aspsps?${q}`);
      }
      if (p === "/auth" && req.method === "POST") {
        const b = await req.json();
        const days = Math.max(1, Math.min(Number(b.valid_days) || 90, 180));
        const body = {
          access: { valid_until: new Date(Date.now() + days * 86400_000).toISOString() },
          aspsp: { name: String(b.aspsp_name), country: String(b.country || "LV") },
          state: String(b.state || crypto.randomUUID()),
          redirect_url: `${url.origin}/callback`,
          psu_type: "personal",
        };
        const r = await eb(env, "POST", "/auth", body);
        if (!r.ok) return r;
        const d = await r.json();
        return json({ url: d.url, state: body.state, valid_until: body.access.valid_until });
      }
      if (p === "/session" && req.method === "POST") {
        const b = await req.json();
        return eb(env, "POST", "/sessions", { code: String(b.code) });
      }
      if ((m = p.match(/^\/session\/([\w-]+)$/))) {
        if (req.method === "GET") return eb(env, "GET", `/sessions/${m[1]}`);
        if (req.method === "DELETE") return eb(env, "DELETE", `/sessions/${m[1]}`, undefined, psu);
      }
      if ((m = p.match(/^\/accounts\/([\w-]+)\/(balances|details|transactions)$/)) && req.method === "GET") {
        const q = new URLSearchParams();
        if (m[2] === "transactions") for (const k of ["date_from", "date_to", "continuation_key", "transaction_status", "strategy"]) {
          const v = url.searchParams.get(k); if (v) q.set(k, v);
        }
        const qs = q.toString();
        // The app tells us when the user is actively looking (pull/Sync now) — banks give more quota to PSU-present calls.
        const present = req.headers.get("x-psu-present") === "1";
        return eb(env, "GET", `/accounts/${m[1]}/${m[2]}${qs ? "?" + qs : ""}`, undefined, present ? psu : undefined);
      }
      return json({ message: "Not found" }, 404);
    } catch (e) {
      return json({ message: `Proxy error: ${e && e.message ? e.message : e}` }, 502);
    }
  },
};
