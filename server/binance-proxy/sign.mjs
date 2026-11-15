// HMAC-SHA256 signing for Binance USER_DATA endpoints (WebCrypto — works in Workers and Node ≥ 20).
const enc = new TextEncoder();

export async function hmacHex(secret, data) {
  const key = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("HMAC", key, enc.encode(data));
  return [...new Uint8Array(sig)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Builds "a=1&b=2&timestamp=…&recvWindow=…&signature=…". Order matters: the signature covers the exact string sent. */
export async function signedQuery(secret, params = {}, now = Date.now()) {
  const q = new URLSearchParams({ ...params, timestamp: String(now), recvWindow: "10000" }).toString();
  return `${q}&signature=${await hmacHex(secret, q)}`;
}
