# FatSecret proxy для LifeAPI

FatSecret отдаёт личный дневник питания только по **трёхстороннему OAuth 1.0**, а каждый запрос нужно подписывать
секретным ключом приложения (Consumer Secret). Вшивать его в APK нельзя, поэтому подпись делает этот маленький сервер:
ключи живут только на нём, телефон хранит лишь токен доступа своего пользователя.

```
Телефон ──JSON──▶ прокси (подписывает HMAC-SHA1) ──▶ authentication.fatsecret.com / platform.fatsecret.com
```

Эндпоинты:

| Метод | Путь | Что делает |
|---|---|---|
| POST | `/oauth/start` | берёт request token, возвращает ссылку на страницу одобрения FatSecret |
| GET | `/oauth/callback` | сюда FatSecret возвращает браузер; страница открывает `lifeapi://fatsecret/callback` и показывает код |
| POST | `/oauth/access` | меняет request token + verifier на access token |
| POST | `/api` | подписывает и выполняет метод FatSecret (`food_entries.get.v2`, `food_entries.get_month.v2`) |
| POST | `/public` | поиск по общей базе продуктов без входа в аккаунт (`foods.search`, `food.get.v2`…) — 2-legged OAuth 1.0, только чтение каталога |
| GET | `/health` | проверка, что сервер жив |

## 1. Регистрация в FatSecret

1. Зайди на <https://platform.fatsecret.com>, зарегистрируйся и войди.
2. **Manage API Keys / Generate API Key** — создай приложение (Basic-план бесплатный).
3. Скопируй **Consumer Key** и **Consumer Secret** (в разделе *OAuth 1.0 Credentials*; это не Client ID/Secret от OAuth 2.0).
4. Если в настройках ключа есть **IP Restrictions / Allowed IP addresses**: у Cloudflare Workers нет постоянного IP,
   поэтому либо разреши все адреса (`0.0.0.0/0`, если FatSecret так позволяет), либо используй вариант с VPS
   (пункт 3 ниже) и впиши его IP. Если FatSecret отвечает ошибкой про IP — дело в этом.

## 2. Развернуть на Cloudflare Workers (бесплатно)

Нужен Node.js 18+ и бесплатный аккаунт Cloudflare.

```bash
cd server/fatsecret-proxy
npx wrangler login
npx wrangler secret put FATSECRET_CONSUMER_KEY      # вставь Consumer Key
npx wrangler secret put FATSECRET_CONSUMER_SECRET   # вставь Consumer Secret
npx wrangler secret put APP_SHARED_KEY              # необязательно: любая длинная случайная строка
npx wrangler deploy
```

`wrangler deploy` выведет адрес вида `https://lifeapi-fatsecret.<твой-поддомен>.workers.dev`.
Проверка: открой `<адрес>/health` — должно быть `{"ok":true}`.

`APP_SHARED_KEY` — мягкая защита: без заголовка `x-app-key` прокси не будет подписывать запросы для посторонних.
Это не настоящий секрет (ключ хранится и в телефоне), но отсекает случайных ботов.

## 3. Вариант: свой сервер со статическим IP

```bash
FATSECRET_CONSUMER_KEY=… FATSECRET_CONSUMER_SECRET=… APP_SHARED_KEY=… \
PUBLIC_URL=https://fs.example.com PORT=8787 node server.mjs
```

Поставь перед ним HTTPS (Caddy: `fs.example.com { reverse_proxy localhost:8787 }`). `PUBLIC_URL` — адрес, который
видят телефон и браузер (из него строится callback).

## 4. Подключить в приложении

1. LifeAPI → **Body → Nutrition → карточка FatSecret** (или History & goals).
2. Вставь **Server URL** (адрес воркера) и, если задавал, **App key** → **Save**.
3. **Connect FatSecret** → откроется браузер → войди в FatSecret и нажми **Allow**.
4. Браузер вернёт тебя в приложение. Если не вернул — на странице будет код, введи его в поле **Verification code** → **Finish**.
5. После подключения приложение само синхронизирует последние 7 дней; дальше — **Sync now** (Сегодня / 7 / 30 дней)
   и автосинк сегодняшнего дня при открытии Nutrition (не чаще раза в 15 минут).

## Поиск продуктов (Nutrition → Add food)

Поиск FatSecret в окне добавления еды идёт через `/public` того же прокси — вход в аккаунт FatSecret для него не нужен, достаточно адреса сервера в приложении. Если прокси уже был развёрнут раньше, **задеплой его заново** (`npx wrangler deploy` или перезапусти `node server.mjs`), иначе `/public` вернёт 404 и в поиске будут только Open Food Facts и твои продукты.

Поиск по штрихкоду в FatSecret доступен только на платных тарифах, поэтому штрихкоды ищутся в Open Food Facts.

## Как работает синхронизация

- Каждая запись дневника FatSecret → приём пищи в LifeAPI с меткой **FatSecret** и `extId = fs:<food_entry_id>`.
- Повторная синхронизация обновляет изменённые записи и удаляет записи, удалённые в FatSecret, — без дублей.
- Для диапазонов используется `food_entries.get_month.v2` (какие дни заполнены), затем `food_entries.get.v2` по каждому дню.
- Данные хранятся в локальной базе и доступны без интернета. Ручные записи синхронизация не трогает.
- **Disconnect** удаляет токены; импортированные приёмы можно оставить или удалить.

## Проверка подписи без сети

```bash
node selftest.mjs   # сверяет HMAC-SHA1 подпись с эталонным примером OAuth 1.0
```
