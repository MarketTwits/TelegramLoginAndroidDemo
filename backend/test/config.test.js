import assert from 'node:assert/strict';
import test from 'node:test';
import { loadConfig } from '../src/config.js';

const CONFIGURATION_KEYS = [
  'NODE_ENV',
  'APP_TOKEN',
  'PORT',
  'TELEGRAM_ISSUER',
  'TELEGRAM_ALLOWED_ALGORITHMS',
  'PASSKEY_RP_ID',
  'PASSKEY_ALLOWED_ORIGINS',
  'PASSKEY_ANDROID_PACKAGE',
  'PASSKEY_ANDROID_CERT_SHA256'
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

test('passkey configuration is all-or-nothing and validates identifiers', { concurrency: false }, () => {
  const fingerprint = Array(32).fill('AA').join(':');
  const androidOrigin = `android:apk-key-hash:${Buffer.from('AA'.repeat(32), 'hex').toString('base64url')}`;
  withEnvironment({ PASSKEY_RP_ID: 'login.example.test' }, () => {
    assert.throws(() => loadConfig(), /must be configured together/);
  });
  withEnvironment({
    PASSKEY_RP_ID: 'https://login.example.test/path',
    PASSKEY_ALLOWED_ORIGINS: 'android:apk-key-hash:abc',
    PASSKEY_ANDROID_PACKAGE: 'com.example.app',
    PASSKEY_ANDROID_CERT_SHA256: fingerprint
  }, () => assert.throws(() => loadConfig(), /lowercase DNS hostname/));
  withEnvironment({
    PASSKEY_RP_ID: 'login.example.test',
    PASSKEY_ALLOWED_ORIGINS: androidOrigin,
    PASSKEY_ANDROID_PACKAGE: 'com.example.app',
    PASSKEY_ANDROID_CERT_SHA256: fingerprint
  }, () => assert.equal(loadConfig().passkeysConfigured, true));
  withEnvironment({
    PASSKEY_RP_ID: 'login.example.test',
    PASSKEY_ALLOWED_ORIGINS: 'android:apk-key-hash:wrong,https://evil.example/path',
    PASSKEY_ANDROID_PACKAGE: 'com.example.app',
    PASSKEY_ANDROID_CERT_SHA256: fingerprint
  }, () => assert.throws(() => loadConfig(), /matching each configured signing certificate/));
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
