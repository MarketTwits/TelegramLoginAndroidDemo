import assert from 'node:assert/strict';
import test from 'node:test';
import { loadConfig } from '../src/config.js';

const CONFIGURATION_KEYS = [
  'NODE_ENV',
  'APP_TOKEN',
  'PORT',
  'TELEGRAM_ISSUER',
  'TELEGRAM_ALLOWED_ALGORITHMS'
];

const withEnvironment = (values, block) => {
  const previous = Object.fromEntries(CONFIGURATION_KEYS.map((key) => [key, process.env[key]]));
  try {
    for (const key of CONFIGURATION_KEYS) delete process.env[key];
    Object.assign(process.env, values);
    block();
  } finally {
    for (const key of CONFIGURATION_KEYS) {
      if (previous[key] === undefined) delete process.env[key];
      else process.env[key] = previous[key];
    }
  }
};

test('configuration rejects malformed numeric values', { concurrency: false }, () => {
  withEnvironment({ PORT: '8080invalid' }, () => {
    assert.throws(() => loadConfig(), /PORT must be an integer/);
  });
});

test('configuration normalizes a trailing issuer slash', { concurrency: false }, () => {
  withEnvironment({ TELEGRAM_ISSUER: 'https://oauth.telegram.org/' }, () => {
    assert.equal(loadConfig().telegramIssuer, 'https://oauth.telegram.org');
  });
});

test('configuration rejects an insecure Telegram issuer', { concurrency: false }, () => {
  withEnvironment({ TELEGRAM_ISSUER: 'http://oauth.example.test' }, () => {
    assert.throws(() => loadConfig(), /must be an absolute HTTPS URL/);
  });
});

test('configuration rejects an empty algorithm list', { concurrency: false }, () => {
  withEnvironment({ TELEGRAM_ALLOWED_ALGORITHMS: ' , ' }, () => {
    assert.throws(() => loadConfig(), /must contain at least one algorithm/);
  });
});

test('production configuration fails closed without an app token', { concurrency: false }, () => {
  withEnvironment({ NODE_ENV: 'production' }, () => {
    assert.throws(() => loadConfig(), /APP_TOKEN is required/);
  });
});

test('production configuration accepts a non-empty app token', { concurrency: false }, () => {
  withEnvironment({ NODE_ENV: 'production', APP_TOKEN: 'production-token' }, () => {
    assert.equal(loadConfig().appToken, 'production-token');
  });
});
