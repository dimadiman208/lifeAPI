// Offline checks: HMAC matches Binance's documented example; worker loads; guard + whitelist behave.
import { hmacHex, signedQuery } from "./sign.mjs";
import worker from "./worker.js";

let ok = 0, bad = 0;
const t = (name, cond) => { if (cond) { ok++; console.log("ok  ", name); } else { bad++; console.log("FAIL", name); } };

// Example from Binance "SIGNED Endpoint Examples" (HMAC Keys).
const secret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j";
const query = "symbol=LTCBTC&side=BUY&type=LIMIT&timeInForce=GTC&quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559";
t("HMAC-SHA256 matches Binance example", (await hmacHex(secret, query)) === "c8db56825ae71d6d79447849e617115f4a920fa2acdcab2b053c4b2838bd6b71");
const sq = await signedQuery("s", { omitZeroBalances: "true" }, 1700000000000);
t("signed query has timestamp, recvWindow, signature last", /omitZeroBalances=true&timestamp=1700000000000&recvWindow=10000&signature=[0-9a-f]{64}$/.test(sq));

const env = { APP_SHARED_KEY: "k", BINANCE_API_KEY: "a", BINANCE_API_SECRET: "b" };
const call = (path, init = {}) => worker.fetch(new Request("https://x.workers.dev" + path, init), env);
t("health open", (await call("/health")).status === 200);
t("no app key → 403", (await call("/account")).status === 403);
t("POST rejected", (await call("/account", { method: "POST", headers: { "x-app-key": "k" } })).status === 405);
t("unknown path → 404", (await call("/api/v3/order", { headers: { "x-app-key": "k" } })).status === 404);
console.log(`\n${ok} passed, ${bad} failed`);
process.exit(bad ? 1 : 0);
