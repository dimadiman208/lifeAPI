// Enable Banking application JWT (RS256). Shared by worker.js and selftest.mjs. WebCrypto only.
const enc = new TextEncoder();

export function b64url(bytes) {
  let s = typeof bytes === "string" ? btoa(unescape(encodeURIComponent(bytes))) : btoa(String.fromCharCode(...new Uint8Array(bytes)));
  return s.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Import a PKCS#8 PEM ("BEGIN PRIVATE KEY"). PKCS#1 ("BEGIN RSA PRIVATE KEY") must be converted first. */
export async function importKey(pem) {
  if (/BEGIN RSA PRIVATE KEY/.test(pem)) {
    throw new Error("Key is PKCS#1. Convert: openssl pkcs8 -topk8 -nocrypt -in key.pem -out key-pkcs8.pem");
  }
  const body = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
}

/** Sign a JWT for Enable Banking. Lifetime must be ≤ 86400 s; we use 1 h. */
export async function makeJwt(appId, key, nowSec = Math.floor(Date.now() / 1000), ttl = 3600) {
  const header = { typ: "JWT", alg: "RS256", kid: appId };
  const payload = { iss: "enablebanking.com", aud: "api.enablebanking.com", iat: nowSec, exp: nowSec + ttl };
  const input = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, enc.encode(input));
  return { token: `${input}.${b64url(sig)}`, exp: nowSec + ttl };
}
