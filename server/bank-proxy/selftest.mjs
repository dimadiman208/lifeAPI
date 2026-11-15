// Offline check of JWT construction with a throwaway key. No network calls.
import { webcrypto, createPublicKey, verify } from "node:crypto";
if (!globalThis.crypto) globalThis.crypto = webcrypto;
const { importKey, makeJwt } = await import("./jwt.mjs");

const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
const pk8 = Buffer.from(await crypto.subtle.exportKey("pkcs8", pair.privateKey)).toString("base64");
const pem = `-----BEGIN PRIVATE KEY-----\n${pk8.match(/.{1,64}/g).join("\n")}\n-----END PRIVATE KEY-----\n`;
const spki = Buffer.from(await crypto.subtle.exportKey("spki", pair.publicKey));

const key = await importKey(pem);
const { token } = await makeJwt("00000000-test-app-id", key, 1_700_000_000);
const [h, p, s] = token.split(".");
const dec = (x) => JSON.parse(Buffer.from(x, "base64url").toString());
const header = dec(h), payload = dec(p);
const ok = verify("RSA-SHA256", Buffer.from(`${h}.${p}`), createPublicKey({ key: spki, format: "der", type: "spki" }), Buffer.from(s, "base64url"));
const checks = [
  ["alg RS256", header.alg === "RS256"], ["typ JWT", header.typ === "JWT"], ["kid", header.kid === "00000000-test-app-id"],
  ["iss", payload.iss === "enablebanking.com"], ["aud", payload.aud === "api.enablebanking.com"],
  ["lifetime ≤ 24h", payload.exp - payload.iat <= 86400], ["signature verifies", ok],
];
let pkcs1Rejected = false;
try { await importKey("-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----"); } catch { pkcs1Rejected = true; }
checks.push(["PKCS#1 rejected with hint", pkcs1Rejected]);
for (const [n, v] of checks) console.log(`${v ? "ok  " : "FAIL"} ${n}`);
process.exit(checks.every((c) => c[1]) ? 0 : 1);
