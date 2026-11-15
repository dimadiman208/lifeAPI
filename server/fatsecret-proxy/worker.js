// Cloudflare Worker entry. Secrets: FATSECRET_CONSUMER_KEY, FATSECRET_CONSUMER_SECRET, optional APP_SHARED_KEY.
import { handle } from './oauth.mjs';

export default {
  fetch: (request, env) => handle(request, env),
};
