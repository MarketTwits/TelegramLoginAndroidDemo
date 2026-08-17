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
  phoneNumber: '+14155552671',
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
  assert.equal(first.body.telegram.phoneNumber, '+14155552671');

  const draft = {
    displayName: 'Bloom Demo',
    headline: 'Building a privacy-first Android application',
    intent: 'BUILDING',
    topics: ['ANDROID', 'SECURITY'],
    avatarSource: 'BLOOM',
    emojiStatus: { setId: 'spotty-persik', emojiId: 'e-0007fab99d521710' },
    phoneNumber: '+1 (415) 555-2671'
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
  assert.deepEqual(savedBody.profile.emojiStatus, {
    setId: 'spotty-persik', emojiId: 'e-0007fab99d521710'
  });
  assert.equal(savedBody.profile.phoneNumber, '+14155552671');
  const seed = savedBody.profile.visualSeed;

  const repeated = await save();
  assert.equal(repeated.status, 200);
  assert.equal((await repeated.json()).profile.visualSeed, seed);

  const editedPhone = await fetch(`${baseUrl}/me/profile`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${first.body.sessionToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ ...draft, phoneNumber: '+44 20 7946 0018' })
  });
  assert.equal((await editedPhone.json()).profile.phoneNumber, '+442079460018');

  currentProfile = telegramProfile({ id: 'unused', username: 'renamed', name: 'Changed Telegram Name' });
  const returning = await login(baseUrl);
  assert.equal(returning.body.account.id, first.body.account.id);
  assert.equal(returning.body.account.loginCount, 2);
  assert.equal(returning.body.telegram.username, 'renamed');
  assert.equal(returning.body.profile.displayName, 'Bloom Demo');
  assert.equal(returning.body.profile.phoneNumber, '+442079460018');
  assert.equal(returning.body.profile.visualSeed, seed);

  const clearedPhone = await fetch(`${baseUrl}/me/profile`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${returning.body.sessionToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ ...draft, phoneNumber: null })
  });
  assert.equal((await clearedPhone.json()).profile.phoneNumber, null);

  const session = await fetch(`${baseUrl}/auth/session`, {
    headers: { Authorization: `Bearer ${returning.body.sessionToken}` }
  });
  assert.equal(session.status, 200);
  const sessionBody = await session.json();
  assert.equal(sessionBody.profile.displayName, 'Bloom Demo');
  assert.equal(sessionBody.profile.phoneNumber, null);

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
      avatarSource: 'BLOOM', emojiStatus: null
    })
  });
  assert.equal(invalid.status, 422);
  assert.equal((await invalid.json()).code, 'INVALID_PROFILE');
  assert.equal((await fetch(`${baseUrl}/me/profile`, { method: 'PUT' })).status, 401);

  const invalidPhone = await fetch(`${baseUrl}/me/profile`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${body.sessionToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      displayName: 'Valid', headline: 'Valid profile', intent: 'BUILDING', topics: ['ANDROID'],
      avatarSource: 'TELEGRAM', emojiStatus: null, phoneNumber: '+999 definitely-not-a-phone'
    })
  });
  assert.equal(invalidPhone.status, 422);

  const withoutPhone = await fetch(`${baseUrl}/me/profile`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${body.sessionToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      displayName: 'Valid', headline: 'Valid profile', intent: 'BUILDING', topics: ['ANDROID'],
      avatarSource: 'TELEGRAM', emojiStatus: null, phoneNumber: null
    })
  });
  assert.equal(withoutPhone.status, 200);
  assert.equal((await withoutPhone.json()).profile.phoneNumber, null);
});

test('grouped profile emoji selection defaults safely and rejects unknown selections', async (context) => {
  const { baseUrl } = await startServer(context, async () => telegramProfile());
  const { body } = await login(baseUrl);
  const headers = {
    Authorization: `Bearer ${body.sessionToken}`,
    'Content-Type': 'application/json'
  };
  const baseDraft = {
    displayName: 'Emoji demo', headline: 'Grouped status emoji', intent: 'BUILDING',
    topics: ['ANDROID'], avatarSource: 'TELEGRAM', phoneNumber: null
  };
  const save = (draft) => fetch(`${baseUrl}/me/profile`, {
    method: 'PUT', headers, body: JSON.stringify(draft)
  });

  const defaulted = await save({ ...baseDraft, emojiStatus: null });
  assert.equal(defaulted.status, 200);
  assert.deepEqual((await defaulted.json()).profile.emojiStatus, {
    setId: 'spotty-persik', emojiId: 'e-0007fab99d521710'
  });

  const catalog = await (await fetch(`${baseUrl}/api/profile-emoji-sets`)).json();
  const neon = catalog.sets.find(({ id }) => id === 'neon');
  const selection = { setId: neon.id, emojiId: neon.emojis[1].id };
  const grouped = await save({ ...baseDraft, emojiStatus: selection });
  assert.equal(grouped.status, 200);
  const groupedProfile = (await grouped.json()).profile;
  assert.deepEqual(groupedProfile.emojiStatus, selection);
  assert.equal(Object.hasOwn(groupedProfile, 'badgeId'), false);

  assert.equal((await save({
    ...baseDraft, emojiStatus: { setId: 'missing', emojiId: 'missing' }
  })).status, 422);

  const omitted = await save(baseDraft);
  assert.deepEqual((await omitted.json()).profile.emojiStatus, {
    setId: 'spotty-persik', emojiId: 'e-0007fab99d521710'
  });
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
    apiVersion: 7, revision: 'development'
  });
  assert.equal(healthResponse.headers.get('x-telegram-bloom-api-version'), '7');
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
      topics: ['ANDROID'], avatarSource: 'BLOOM', emojiStatus: null
    })
  });
  assert.equal(profileResponse.status, 403);

  const repeated = await login(baseUrl);
  assert.equal(repeated.response.status, 403);
  assert.equal(database.getAccount(first.body.account.id).account.login_count, 1);
});

test('profile emoji catalog groups one compact TGS format with verified thumbnails', async (context) => {
  const { baseUrl } = await startServer(
    context,
    async () => telegramProfile(),
    { ...config, nodeEnv: 'production' }
  );
  const response = await fetch(`${baseUrl}/api/profile-emoji-sets`);
  assert.equal(response.status, 200);
  const catalog = await response.json();
  assert.equal(catalog.version, 3);
  assert.equal(catalog.format, 'LOTTIE_TGS');
  assert.deepEqual(catalog.defaultEmoji, {
    setId: 'spotty-persik', emojiId: 'e-0007fab99d521710'
  });
  assert.equal(catalog.sets.length, 5);
  assert.equal(catalog.sets.reduce((total, set) => total + set.emojis.length, 0), 456);
  assert.equal(catalog.sets.some(({ id }) => id === 'classic'), false);

  for (const set of catalog.sets) {
    assert.ok(set.labels.en);
    assert.equal(new Set(set.emojis.map(({ id }) => id)).size, set.emojis.length);
    for (const emoji of set.emojis) {
      assert.equal(typeof emoji.name, 'string');
      assert.ok(emoji.name.length > 1);
      assert.ok(Array.isArray(emoji.keywords));
      assert.ok(emoji.keywords.includes(set.labels.en));
    }
    const thumbnail = set.emojis.find(({ id }) => id === set.thumbnailEmojiId);
    assert.ok(thumbnail);
    assert.match(thumbnail.assetPath, /^\/assets\/profile-emojis\/v3\/[0-9a-f]{64}\.tgs$/);
    const asset = await fetch(`${baseUrl}${thumbnail.assetPath}`);
    assert.equal(asset.status, 200);
    assert.equal(asset.headers.get('content-type'), 'application/x-tgsticker');
    assert.match(asset.headers.get('cache-control'), /immutable/);
    const bytes = Buffer.from(await asset.arrayBuffer());
    assert.equal(bytes.length, thumbnail.sizeBytes);
    assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'), thumbnail.sha256);
  }
});
