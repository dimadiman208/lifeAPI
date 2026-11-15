// LifeAPI ⇄ Binance proxy — READ-ONLY. Holds the API key + secret as Worker secrets, signs requests (HMAC-SHA256)
// and forwards a fixed whitelist of balance/price calls. No trading, no withdrawals, no arbitrary paths.
// Stateless: nothing is stored; only error statuses are logged (never balances).
import { signedQuery } from "./sign.mjs";

// Binance's edge (WAF) often answers 403 with an HTML page to Cloudflare Worker IPs on api.binance.com.
// The alternative official hosts are tried in order; the first that answers JSON is remembered for this isolate.
const HOSTS = ["https://api.binance.com", "https://api-gcp.binance.com", "https://api1.binance.com", "https://api2.binance.com", "https://api3.binance.com", "https://api4.binance.com"];
let hostIdx = 0;

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });

function explain(status, text) {
  let body = {};
  try { body = JSON.parse(text); } catch {}
  const code = body.code;
  if (status === 451) return { status, error: "RESTRICTED_LOCATION", message: "Binance refused this server's location (HTTP 451). Use the Node server on a VPS in an allowed country." };
  if (status === 418 || status === 429) return { status, error: "RATE_LIMIT", message: "Binance rate limit — try again in a few minutes" };
  if (code === -2014 || code === -2015 || code === -2008) return { status: 401, error: "BAD_KEY", message: "Binance rejected the API key (wrong key, IP restriction on, or reading not enabled)" };
  if (code === -1021) return { status: 400, error: "CLOCK", message: "Timestamp outside recvWindow" };
  return { status, error: code != null ? `BINANCE_${code}` : "BINANCE", message: body.msg || `Binance HTTP ${status}` };
}

async function binance(env, method, path, params, signed) {
  const headers = signed ? { "X-MBX-APIKEY": env.BINANCE_API_KEY } : {};
  let last = null;
  for (let attempt = 0; attempt < HOSTS.length; attempt++) {
    const host = HOSTS[(hostIdx + attempt) % HOSTS.length];
    // re-sign per attempt: timestamp must stay fresh
    const qs = signed ? await signedQuery(env.BINANCE_API_SECRET, params) : new URLSearchParams(params).toString();
    const r = method === "POST"
      ? await fetch(`${host}${path}`, { method, headers: { ...headers, "content-type": "application/x-www-form-urlencoded" }, body: qs })
      : await fetch(`${host}${path}${qs ? "?" + qs : ""}`, { method, headers });
    const text = await r.text();
    if (r.ok) { hostIdx = (hostIdx + attempt) % HOSTS.length; return JSON.parse(text); }
    let isJson = true; try { JSON.parse(text); } catch { isJson = false; }
    const e = explain(r.status, text);
    console.log(`BINANCE ${method} ${host}${path} -> ${r.status} ${e.error} ${isJson ? "" : "(non-JSON: " + text.slice(0, 120).replace(/\s+/g, " ") + ")"}`);
    last = e;
    // only the edge/WAF block (403 HTML) or 451 is worth retrying on another host; real API errors are final
    if (!((r.status === 403 && !isJson) || r.status === 451)) throw e;
  }
  if (last && last.status === 403) last.message = "Binance blocks this Cloudflare server (HTTP 403 from Binance's firewall on all API hosts). Run server.mjs on a VPS or home server instead — see README.";
  throw last;
}

/** Simple Earn positions (all pages). Missing permission / no Earn → empty, not an error. */
async function earn(env, kind) {
  const out = [];
  for (let current = 1; current <= 10; current++) {
    let page;
    try { page = await binance(env, "GET", `/sapi/v1/simple-earn/${kind}/position`, { current: String(current), size: "100" }, true); }
    catch (e) { if (e.error === "RATE_LIMIT" || e.error === "BAD_KEY" || e.error === "RESTRICTED_LOCATION") throw e; return { rows: out, error: e.message }; }
    const rows = page.rows || [];
    for (const r of rows) out.push({ asset: r.asset, amount: r.totalAmount ?? r.amount ?? "0" });
    if (rows.length < 100) break;
  }
  return { rows: out };
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    const p = url.pathname.replace(/\/+$/, "") || "/";
    try {
      if (p === "/health") return json({ ok: true, configured: !!(env.BINANCE_API_KEY && env.BINANCE_API_SECRET && env.APP_SHARED_KEY) });
      if (!env.APP_SHARED_KEY) return json({ message: "Server not configured: APP_SHARED_KEY missing" }, 500);
      // trim both: a stray space/newline in the secret or the pasted key shouldn't lock you out
      if (!env.APP_SHARED_KEY || (req.headers.get("x-app-key") || "").trim() !== String(env.APP_SHARED_KEY).trim()) return json({ message: "Forbidden: app key does not match APP_SHARED_KEY" }, 403);
      if (req.method !== "GET") return json({ message: "Method not allowed" }, 405);
      if (!env.BINANCE_API_KEY || !env.BINANCE_API_SECRET) return json({ error: "CONFIG", message: "BINANCE_API_KEY / BINANCE_API_SECRET not set" }, 500);

      // Spot balances (non-zero only). Includes "LD…" tokens for Simple Earn flexible — the app de-duplicates.
      if (p === "/account") {
        const a = await binance(env, "GET", "/api/v3/account", { omitZeroBalances: "true" }, true);
        return json({ canTrade: a.canTrade, canWithdraw: a.canWithdraw, balances: (a.balances || []).map((b) => ({ asset: b.asset, free: b.free, locked: b.locked })) });
      }
      if (p === "/funding") {
        const f = await binance(env, "POST", "/sapi/v1/asset/get-funding-asset", {}, true);
        return json({ balances: (f || []).map((b) => ({ asset: b.asset, free: b.free, locked: b.locked, freeze: b.freeze, withdrawing: b.withdrawing })) });
      }
      if (p === "/earn") {
        const [flexible, locked] = await Promise.all([earn(env, "flexible"), earn(env, "locked")]);
        return json({ flexible: flexible.rows, locked: locked.rows, errors: [flexible.error, locked.error].filter(Boolean) });
      }
      // 24h mini tickers for X/EUR, X/USDT and EURUSDT, filtered server-side so the phone downloads little.
      if (p === "/prices") {
        const assets = (url.searchParams.get("assets") || "").split(",").map((s) => s.trim().toUpperCase()).filter((s) => /^[A-Z0-9]{1,20}$/.test(s)).slice(0, 200);
        const want = new Set(["EURUSDT"]);
        for (const a of assets) { want.add(`${a}EUR`); want.add(`${a}USDT`); }
        const all = await binance(env, "GET", "/api/v3/ticker/24hr", { type: "MINI" }, false);
        const prices = {};
        for (const t of all) if (want.has(t.symbol)) prices[t.symbol] = { last: t.lastPrice, open: t.openPrice };
        return json({ prices });
      }
      return json({ message: "Not found" }, 404);
    } catch (e) {
      if (e && e.status) return json({ error: e.error, message: e.message }, e.status);
      return json({ error: "PROXY", message: String(e && e.message || e) }, 502);
    }
  },
};
