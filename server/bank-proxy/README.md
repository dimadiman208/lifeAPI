# LifeAPI · bank proxy (Enable Banking)

Маленький Cloudflare Worker между приложением и Enable Banking. Нужен потому, что каждый запрос к Enable Banking
подписывается JWT приватным ключом приложения, а ключ нельзя класть в APK.

- Только чтение: счета, балансы, транзакции. Платежей нет.
- Ничего не хранит и не логирует тела запросов (stateless). Банковские данные проходят насквозь и сохраняются только на телефоне.
- Без заголовка `x-app-key` (= `APP_SHARED_KEY`) отвечает 403.

| Метод | Путь | Что делает |
|---|---|---|
| GET | `/health` | проверка; `configured: true`, когда все секреты заданы |
| GET | `/aspsps?country=LV` | список банков |
| POST | `/auth` `{aspsp_name, country, valid_days}` | ссылка на вход в банк |
| GET | `/callback` | сюда банк возвращает пользователя → открывает `lifeapi://bank/callback` |
| POST | `/session` `{code}` | создаёт сессию, возвращает счета |
| GET/DELETE | `/session/:id` | статус / отключение |
| GET | `/accounts/:uid/balances`, `/details`, `/transactions` | данные счёта |

## 1. Ключ

Приватный ключ, который ты скачал при регистрации приложения в Enable Banking (`.pem`). **Не вставляй его никуда руками и не клади в git** (`*.pem` уже в `.gitignore`).

Проверь формат:
```
head -1 путь/к/ключу.pem
```
- `-----BEGIN PRIVATE KEY-----` — подходит как есть.
- `-----BEGIN RSA PRIVATE KEY-----` — сконвертируй:
  ```
  openssl pkcs8 -topk8 -nocrypt -in путь/к/ключу.pem -out ~/key-pkcs8.pem
  ```

## 2. Деплой

```
cd ~/Projects/life-api/server/bank-proxy
npx wrangler login                      # если ещё не логинился
npx wrangler deploy                     # первый деплой создаёт Worker и печатает адрес
npx wrangler secret put ENABLEBANKING_APP_ID                          # вставь Application ID из кабинета Enable Banking
npx wrangler secret put ENABLEBANKING_PRIVATE_KEY < путь/к/ключу.pem  # ключ берётся прямо из файла (PKCS#8)
npx wrangler secret put APP_SHARED_KEY                                # придумай длинную случайную строку, её же введёшь в приложении
```
Случайную строку можно сделать так: `openssl rand -hex 24`.

Адрес будет вида `https://lifeapi-bank.<твой-поддомен>.workers.dev`.

Проверка: открой `https://lifeapi-bank.<твой-поддомен>.workers.dev/health` → `{"ok":true,"configured":true}`.

## 3. Redirect URL в Enable Banking

В кабинете Enable Banking (Applications → твоё приложение) добавь в разрешённые Redirect URLs **точно**:
```
https://lifeapi-bank.<твой-поддомен>.workers.dev/callback
```
Без этого банк не вернёт тебя в приложение.

Приложение в «restricted» режиме (без договора с Enable Banking) видит только счета, привязанные в кабинете
(«Activate by linking accounts»). Привяжи там свой счёт Swedbank, если ещё не сделал.

## 4. В приложении

LifeAPI → Money → Connections → карточка Swedbank (или Money → «Bank»):
1. **Server URL** — адрес Worker'а (без `/health`), **App key** — тот же `APP_SHARED_KEY` → Save.
2. Выбери банк (Swedbank, LV) → **Connect** → вход в банк в браузере → подтверди доступ.
3. Браузер вернёт в приложение. Если нет — скопируй код со страницы в поле «Code» и нажми Finish.
4. Счета появятся в Money с пометкой «Bank», транзакции подтянутся за последние 90 дней.

## Ограничения

- Согласие банка действует ограниченный срок (обычно до 180 дней) — потом «Reconnect».
- Банки ограничивают фоновые запросы (около 4 в сутки на счёт), поэтому автосинхронизация — не чаще раза в 6 часов; «Sync now» — когда угодно.
- Cloudflare может хранить свои технические логи платформы (IP, время запроса) по своей политике; сам Worker тела запросов не пишет.

## Локальная проверка

`node selftest.mjs` — проверяет сборку и подпись JWT на одноразовом ключе (без сети и без твоего ключа).
