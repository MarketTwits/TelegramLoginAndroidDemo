import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import test from 'node:test';
import { createApp } from '../src/app.js';
import { createDatabase } from '../src/database.js';

const config = {
  trustProxy: false,
  authRateLimitPerMinute: 100,
  sessionTtlDays: 30,
  nodeEnv: 'test',
  telegramConfigured: true
};

const telegramProfile = (overrides = {}) => ({
  id: crypto.randomUUID(),
  telegramSubject: '12345',
  name: 'Demo User',
  givenName: 'Demo',
  familyName: 'User',
  username: 'demo',
  phoneNumber: '+10000000000',
  phoneVerified: true,
  picture: 'https://example.test/avatar.jpg',
  ...overrides
});

const startServer = async (context, verifyTelegramToken, customConfig = config) => {
  const database = createDatabase({ databasePath: ':memory:' });
  database.migrate();
  const server = createApp({ config: customConfig, database, verifyTelegramToken })
    .listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  context.after(() => new Promise((resolve) => server.close(() => {
    database.close();
    resolve();
  })));
  return {
    baseUrl: `http://127.0.0.1:${server.address().port}`,
    database
  };
};

const login = async (baseUrl) => {
  const response = await fetch(`${baseUrl}/auth/telegram`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken: 'x'.repeat(40) })
  });
  return { response, body: await response.json() };
};

test('new login creates an account, profile PUT is idempotent, and account deletion is final', async (context) => {
  let currentProfile = telegramProfile();
  const { baseUrl } = await startServer(context, async () => currentProfile);

  const first = await login(baseUrl);
  assert.equal(first.response.status, 200);
  assert.match(first.response.headers.get('x-request-id'), /^[0-9a-f-]{36}$/);
  assert.equal(first.body.account.onboardingState, 'PROFILE_REQUIRED');
  assert.equal(first.body.account.memberNumber, 1);
  assert.equal(first.body.account.loginCount, 1);
  assert.equal(first.body.profile, null);
  assert.equal(first.body.telegram.phoneVerified, true);
  assert.equal('phoneNumber' in first.body.telegram, false);

  const draft = {
    displayName: 'Bloom Demo',
    headline: 'Building a privacy-first Android application',
    intent: 'BUILDING',
    topics: ['ANDROID', 'SECURITY'],
    avatarSource: 'BLOOM',
    badgeId: 'festive-flags'
  };
  const save = () => fetch(`${baseUrl}/me/profile`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${first.body.sessionToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(draft)
  });
  const saved = await save();
  assert.equal(saved.status, 200);
  const savedBody = await saved.json();
  assert.equal(savedBody.account.onboardingState, 'PROFILE_COMPLETED');
  assert.deepEqual(savedBody.profile.topics, draft.topics);
  const seed = savedBody.profile.visualSeed;

  const repeated = await save();
  assert.equal(repeated.status, 200);
  assert.equal((await repeated.json()).profile.visualSeed, seed);

  currentProfile = telegramProfile({ id: 'unused', username: 'renamed', name: 'Changed Telegram Name' });
  const returning = await login(baseUrl);
  assert.equal(returning.body.account.id, first.body.account.id);
  assert.equal(returning.body.account.loginCount, 2);
  assert.equal(returning.body.telegram.username, 'renamed');
  assert.equal(returning.body.profile.displayName, 'Bloom Demo');
  assert.equal(returning.body.profile.visualSeed, seed);

  const session = await fetch(`${baseUrl}/auth/session`, {
    headers: { Authorization: `Bearer ${returning.body.sessionToken}` }
  });
  assert.equal(session.status, 200);
  assert.equal((await session.json()).profile.displayName, 'Bloom Demo');

  const deleted = await fetch(`${baseUrl}/me/account`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${returning.body.sessionToken}` }
  });
  assert.equal(deleted.status, 204);
  assert.equal((await fetch(`${baseUrl}/auth/session`, {
    headers: { Authorization: `Bearer ${returning.body.sessionToken}` }
  })).status, 401);

  const registeredAgain = await login(baseUrl);
  assert.notEqual(registeredAgain.body.account.id, first.body.account.id);
  assert.equal(registeredAgain.body.account.onboardingState, 'PROFILE_REQUIRED');
  assert.equal(registeredAgain.body.profile, null);
});

test('profile validation and session authentication return typed public errors', async (context) => {
  const { baseUrl } = await startServer(context, async () => telegramProfile());
  const { body } = await login(baseUrl);
  const invalid = await fetch(`${baseUrl}/me/profile`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${body.sessionToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      displayName: '', headline: 'x', intent: 'UNKNOWN', topics: [],
      avatarSource: 'BLOOM', badgeId: 'outline'
    })
  });
  assert.equal(invalid.status, 422);
  assert.equal((await invalid.json()).code, 'INVALID_PROFILE');
  assert.equal((await fetch(`${baseUrl}/me/profile`, { method: 'PUT' })).status, 401);
});

test('Backend starts without Telegram configuration and reports setup mode', async (context) => {
  const setupConfig = { ...config, telegramConfigured: false };
  const { baseUrl } = await startServer(context, async () => {
    throw new Error('Verifier must not be called in setup mode');
  }, setupConfig);
  const healthResponse = await fetch(`${baseUrl}/api/health/ready`);
  assert.equal(healthResponse.status, 200);
  assert.deepEqual(await healthResponse.json(), {
    status: 'ready', database: 'connected', telegram: 'configuration_required',
    apiVersion: 5, revision: 'development'
  });
  assert.equal(healthResponse.headers.get('x-telegram-bloom-api-version'), '5');
  const response = await fetch(`${baseUrl}/auth/telegram`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken: 'x'.repeat(40) })
  });
  assert.equal(response.status, 503);
});

test('disabled account cannot restore, edit, or create another successful login', async (context) => {
  const { baseUrl, database } = await startServer(context, async () => telegramProfile());
  const first = await login(baseUrl);
  database.disableAccount(first.body.account.id);

  const sessionResponse = await fetch(`${baseUrl}/auth/session`, {
    headers: { Authorization: `Bearer ${first.body.sessionToken}` }
  });
  assert.equal(sessionResponse.status, 403);
  assert.equal((await sessionResponse.json()).code, 'ACCOUNT_DISABLED');

  const profileResponse = await fetch(`${baseUrl}/me/profile`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${first.body.sessionToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      displayName: 'Disabled', headline: 'Must not be saved', intent: 'BUILDING',
      topics: ['ANDROID'], avatarSource: 'BLOOM', badgeId: 'outline'
    })
  });
  assert.equal(profileResponse.status, 403);

  const repeated = await login(baseUrl);
  assert.equal(repeated.response.status, 403);
  assert.equal(database.getAccount(first.body.account.id).account.login_count, 1);
});

test('profile badge catalog exposes immutable verified assets', async (context) => {
  const { baseUrl } = await startServer(
    context,
    async () => telegramProfile(),
    { ...config, nodeEnv: 'production' }
  );
  const response = await fetch(`${baseUrl}/api/profile-badges`);
  assert.equal(response.status, 200);
  const catalog = await response.json();
  assert.equal(catalog.version, 1);
  assert.equal(catalog.defaultBadgeId, 'outline');
  assert.equal(catalog.badges.length, 7);
  assert.equal(new Set(catalog.badges.map(({ id }) => id)).size, 7);
  for (const badge of catalog.badges) {
    assert.match(badge.sha256, /^[0-9a-f]{64}$/);
    const asset = await fetch(`${baseUrl}${badge.assetPath}`);
    assert.equal(asset.status, 200);
    assert.match(asset.headers.get('cache-control'), /max-age=31536000/);
    assert.match(asset.headers.get('cache-control'), /immutable/);
    const bytes = Buffer.from(await asset.arrayBuffer());
    assert.equal(bytes.length, badge.sizeBytes);
    assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'), badge.sha256);
  }
  const tgs = catalog.badges.find(({ kind }) => kind === 'LOTTIE_TGS');
  assert.equal(
    (await fetch(`${baseUrl}${tgs.assetPath}`)).headers.get('content-type'),
    'application/x-tgsticker'
  );
});
