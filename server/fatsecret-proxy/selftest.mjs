// Offline check of the signing code against RFC 5849 §3.4.1 / the classic OAuth 1.0 example (no FatSecret call).
import { baseString, hmacSha1Base64, pct } from './oauth.mjs';
import assert from 'node:assert';

assert.equal(pct("Ladies + Gentlemen"), 'Ladies%20%2B%20Gentlemen');
assert.equal(pct("An encoded string!"), 'An%20encoded%20string%21');
assert.equal(pct("Dogs, Cats & Mice"), 'Dogs%2C%20Cats%20%26%20Mice');
assert.equal(pct("☃"), '%E2%98%83');

// Twitter's documented OAuth 1.0a example (developer.twitter.com "Creating a signature").
const params = {
  status: 'Hello Ladies + Gentlemen, a signed OAuth request!',
  include_entities: 'true',
  oauth_consumer_key: 'xvz1evFS4wEEPTGEFPHBog',
  oauth_nonce: 'kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg',
  oauth_signature_method: 'HMAC-SHA1',
  oauth_timestamp: '1318622958',
  oauth_token: '370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb',
  oauth_version: '1.0',
};
const bs = baseString('POST', 'https://api.twitter.com/1.1/statuses/update.json', params);
const sig = await hmacSha1Base64(
  `${pct('kAcSOqF21Fu85e7zjz7ZN2U4ZRhfV3WpwPAoE3Z7kBw')}&${pct('LswwdoUaIvS8ltyTt5jkRh4J50vUPVVHtR2YPi5kE')}`, bs);
assert.equal(sig, 'hCtSmYh+iHYCEqBWrE7C7hYmtUk=');
console.log('signature self-test OK');
