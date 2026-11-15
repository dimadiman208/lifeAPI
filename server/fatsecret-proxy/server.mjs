// Same proxy for a VPS / home server (Node ≥ 18, no dependencies).
//   FATSECRET_CONSUMER_KEY=… FATSECRET_CONSUMER_SECRET=… [APP_SHARED_KEY=…] [PORT=8787] [PUBLIC_URL=https://fs.example.com] node server.mjs
// Put it behind HTTPS (Caddy / nginx). PUBLIC_URL must be the address the phone and browser use.
import http from 'node:http';
import { webcrypto } from 'node:crypto';
import { handle } from './oauth.mjs';

if (!globalThis.crypto) globalThis.crypto = webcrypto;

const env = {
  FATSECRET_CONSUMER_KEY: process.env.FATSECRET_CONSUMER_KEY,
  FATSECRET_CONSUMER_SECRET: process.env.FATSECRET_CONSUMER_SECRET,
  APP_SHARED_KEY: process.env.APP_SHARED_KEY,
};
const port = Number(process.env.PORT || 8787);
const publicUrl = process.env.PUBLIC_URL || `http://localhost:${port}`;

http.createServer(async (req, res) => {
  try {
    const chunks = [];
    for await (const c of req) chunks.push(c);
    const body = Buffer.concat(chunks);
    const url = new URL(req.url, publicUrl);
    const request = new Request(url, {
      method: req.method,
      headers: Object.fromEntries(Object.entries(req.headers).filter(([, v]) => typeof v === 'string')),
      body: ['GET', 'HEAD'].includes(req.method) ? undefined : body,
    });
    const r = await handle(request, env);
    res.writeHead(r.status, Object.fromEntries(r.headers));
    res.end(Buffer.from(await r.arrayBuffer()));
  } catch (e) {
    res.writeHead(500, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ error: { code: 'server', message: String(e) } }));
  }
}).listen(port, () => console.log(`FatSecret proxy on :${port} (public ${publicUrl})`));
