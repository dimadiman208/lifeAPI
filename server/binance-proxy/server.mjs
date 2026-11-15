// Same proxy for a VPS with a fixed IP (if you want Binance's IP restriction ON, or Cloudflare gets HTTP 451).
// Run behind HTTPS (Caddy/nginx):  BINANCE_API_KEY=… BINANCE_API_SECRET=… APP_SHARED_KEY=… PORT=8788 node server.mjs
import http from "node:http";
import worker from "./worker.js";

const env = { BINANCE_API_KEY: process.env.BINANCE_API_KEY, BINANCE_API_SECRET: process.env.BINANCE_API_SECRET, APP_SHARED_KEY: process.env.APP_SHARED_KEY };
http.createServer(async (req, res) => {
  const headers = new Headers();
  for (const [k, v] of Object.entries(req.headers)) if (typeof v === "string") headers.set(k, v);
  const r = await worker.fetch(new Request(`http://localhost${req.url}`, { method: req.method, headers }), env);
  res.writeHead(r.status, Object.fromEntries(r.headers));
  res.end(await r.text());
}).listen(Number(process.env.PORT || 8788), () => console.log("binance proxy on", process.env.PORT || 8788));
