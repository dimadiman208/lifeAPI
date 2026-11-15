# LifeAPI · Binance proxy (только чтение)

Маленький Cloudflare Worker между приложением и Binance. Нужен потому, что каждый запрос к балансам подписывается
секретом API-ключа (HMAC-SHA256), а секрет нельзя класть в APK.

- **Только чтение.** Прокси знает 4 запроса: балансы Spot, Funding, Simple Earn и цены. Ордеров, выводов и любых других путей нет — даже если ключ это позволял бы.
- Ничего не хранит. В лог пишутся только коды ошибок, балансы — никогда.
- Без заголовка `x-app-key` (= `APP_SHARED_KEY`) отвечает 403.

| Путь | Что отдаёт |
|---|---|
| `/health` | проверка; `configured: true`, когда все секреты заданы |
| `/account` | Spot-балансы (ненулевые) |
| `/funding` | Funding-кошелёк |
| `/earn` | Simple Earn: flexible + locked |
| `/prices?assets=BTC,ETH` | цены к EUR / USDT за 24 ч |

## 1. Ключ Binance

Binance → Profile → **API Management** → твой ключ → **Edit restrictions**:
- ✅ **Enable Reading** — единственная галочка.
- ❌ Spot & Margin Trading, ❌ Withdrawals, ❌ всё остальное.
- **IP access restrictions: Unrestricted.** У Cloudflare Worker нет постоянного IP, поэтому привязка к IP не заработает.
  Если хочешь ограничение по IP — запусти `server.mjs` на своём VPS (см. ниже) и впиши его IP.

> Binance может выключить ключ без IP-ограничения с правами шире чтения — поэтому только Reading.

## 2. Деплой

```
cd ~/Projects/life-api/server/binance-proxy
npx wrangler deploy                          # создаёт Worker, печатает адрес https://lifeapi-binance.<ник>.workers.dev
npx wrangler secret put BINANCE_API_KEY      # вставь API Key
npx wrangler secret put BINANCE_API_SECRET   # вставь Secret Key
openssl rand -hex 24                         # придумай ключ приложения
npx wrangler secret put APP_SHARED_KEY       # вставь строку из openssl
```

Проверка: открой `https://lifeapi-binance.<ник>.workers.dev/health` → `{"ok":true,"configured":true}`.

## 3. В приложении

Settings → Integrations → Binance → **Server URL** (адрес Worker) + **App key** (строка из `openssl rand`) → Save → Test → Sync now.

## Если ошибки
- **451 / Restricted location** — Binance не пускает запросы из региона, где выполнился Worker. Запусти `server.mjs` на VPS в разрешённой стране:
  ```
  BINANCE_API_KEY=… BINANCE_API_SECRET=… APP_SHARED_KEY=… PORT=8788 node server.mjs
  ```
  (за HTTPS — Caddy/nginx), и укажи его адрес в приложении.
- **Binance rejected the API key** — не тот ключ, включено ограничение по IP или не стоит Enable Reading.
- **Rate limit** — подожди пару минут; приложение само синхронизирует не чаще раза в 5 минут.

Офлайн-проверка подписи: `node selftest.mjs`.
