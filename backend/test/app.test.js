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
  telegramUserId: '987654321',
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

test('public landing page is minimal and does not expose the API inventory', async (context) => {
  const { baseUrl } = await startServer(context, async () => telegramProfile());
  const response = await fetch(baseUrl);
  const html = await response.text();

  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-security-policy'), /default-src 'self'/);
  assert.match(response.headers.get('content-security-policy'), /frame-ancestors 'none'/);
  assert.equal(response.headers.get('x-frame-options'), 'DENY');
  assert.equal(response.headers.get('x-telegram-bloom-api-version'), null);
  assert.match(html, /Telegram Sign-In API/);
  assert.match(html, /noindex, nofollow/);
  assert.doesNotMatch(html, /\/auth\/|\/me\/|\/api\/health|SQLite|JWKS|app\.js/);
});

test('protected readiness rejects missing and incorrect app tokens', async (context) => {
  const protectedConfig = { ...config, appToken: 'test-app-token' };
  const { baseUrl } = await startServer(
    context,
    async () => telegramProfile(),
    protectedConfig
  );

  assert.equal((await fetch(`${baseUrl}/api/health/ready`)).status, 403);
  assert.equal((await fetch(`${baseUrl}/api/health/ready`, {
    headers: { 'X-App-Token': 'incorrect' }
  })).status, 403);
  assert.equal((await fetch(`${baseUrl}/api/health/ready`, {
    headers: { 'X-App-Token': protectedConfig.appToken }
  })).status, 200);
});

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
  assert.equal(first.body.telegram.userId, '987654321');

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

  const unsupportedMediaType = await fetch(`${baseUrl}/me/profile`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${body.sessionToken}`, 'Content-Type': 'text/plain' },
    body: '{}'
  });
  assert.equal(unsupportedMediaType.status, 415);
  assert.equal((await unsupportedMediaType.json()).code, 'UNSUPPORTED_MEDIA_TYPE');

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
    passkeys: 'configuration_required', appToken: 'configuration_required', apiVersion: 8, revision: 'development'
  });
  assert.equal(healthResponse.headers.get('x-telegram-bloom-api-version'), '8');
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

  const passkeyRequests = [
    ['/me/passkeys', 'GET'],
    ['/me/reauth/passkeys/options', 'POST'],
    ['/me/reauth/telegram', 'POST'],
    ['/me/passkeys/registration/options', 'POST'],
    ['/me/passkeys/registration/verify', 'POST'],
    ['/me/passkeys/missing', 'PATCH'],
    ['/me/passkeys/missing', 'DELETE']
  ];
  for (const [path, method] of passkeyRequests) {
    const response = await fetch(`${baseUrl}${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${first.body.sessionToken}`,
        ...(method === 'POST' || method === 'PATCH' ? { 'Content-Type': 'application/json' } : {})
      },
      ...(method === 'POST' || method === 'PATCH' ? { body: '{}' } : {})
    });
    assert.equal(response.status, 403, `${method} ${path}`);
    assert.equal((await response.json()).code, 'ACCOUNT_DISABLED');
  }

  const repeated = await login(baseUrl);
  assert.equal(repeated.response.status, 403);
  assert.equal(database.getAccount(first.body.account.id).account.login_count, 1);
});

test('Telegram reauthentication is account-bound and rejects replayed proofs', async (context) => {
  let verifiedProfile = telegramProfile();
  const { baseUrl } = await startServer(context, async () => verifiedProfile);
  const authenticated = await login(baseUrl);
  const request = () => fetch(`${baseUrl}/me/reauth/telegram`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${authenticated.body.sessionToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ idToken: 'r'.repeat(40) })
  });

  assert.equal((await request()).status, 204);
  assert.equal((await request()).status, 409);
  verifiedProfile = telegramProfile({ telegramUserId: '123456789' });
  const mismatch = await fetch(`${baseUrl}/me/reauth/telegram`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${authenticated.body.sessionToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ idToken: 'm'.repeat(40) })
  });
  assert.equal(mismatch.status, 403);
  assert.equal((await mismatch.json()).code, 'ACCOUNT_MISMATCH');
});

test('Digital Asset Links exposes only configured Android signing identities', async (context) => {
  const fingerprint = Array(32).fill('AA').join(':');
  const configured = {
    ...config,
    passkeyAndroidPackage: 'com.example.app',
    passkeyAndroidCertSha256: [fingerprint]
  };
  const { baseUrl } = await startServer(context, async () => telegramProfile(), configured);
  const response = await fetch(`${baseUrl}/.well-known/assetlinks.json`);
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /^application\/json/);
  assert.deepEqual(await response.json(), [{
    relation: ['delegate_permission/common.get_login_creds'],
    target: {
      namespace: 'android_app',
      package_name: 'com.example.app',
      sha256_cert_fingerprints: [fingerprint]
    }
  }]);
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

  const repeatedAssetPath = catalog.sets[0].emojis[0].assetPath;
  for (let index = 0; index < 101; index += 1) {
    const asset = await fetch(`${baseUrl}${repeatedAssetPath}`);
    assert.equal(asset.status, 200, `immutable asset request ${index + 1} was rate limited`);
    await asset.arrayBuffer();
  }
  const liveAfterAssets = await fetch(`${baseUrl}/api/health/live`);
  assert.equal(liveAfterAssets.status, 200, 'asset traffic must not consume the API rate limit');
});

test('app tokens rotation: both current and previous tokens are accepted', async (context) => {
  const { baseUrl } = await startServer(
    context,
    async () => telegramProfile(),
    { ...config, appTokens: ['current-token-v2', 'previous-token-v1'] }
  );

  const res1 = await fetch(`${baseUrl}/api/health/ready`, {
    headers: { 'X-App-Token': 'current-token-v2' }
  });
  assert.equal(res1.status, 200);
  const data1 = await res1.json();
  assert.equal(data1.appToken, 'configured');

  const res2 = await fetch(`${baseUrl}/api/health/ready`, {
    headers: { 'X-App-Token': 'previous-token-v1' }
  });
  assert.equal(res2.status, 200);

  const res3 = await fetch(`${baseUrl}/api/health/ready`, {
    headers: { 'X-App-Token': 'expired-token-v0' }
  });
  assert.equal(res3.status, 403);

  const res4 = await fetch(`${baseUrl}/api/health/ready`);
  assert.equal(res4.status, 403);
});

test('observability: GET /api/metrics returns aggregated metrics and tracks operations', async (context) => {
  const { baseUrl } = await startServer(
    context,
    async () => telegramProfile(),
    { ...config, appToken: 'metric-test-token' }
  );

  // Without token, returns 403
  const unauthorized = await fetch(`${baseUrl}/api/metrics`);
  assert.equal(unauthorized.status, 403);

  // With token, returns metrics
  const initial = await fetch(`${baseUrl}/api/metrics`, {
    headers: { 'X-App-Token': 'metric-test-token' }
  });
  assert.equal(initial.status, 200);
  const data = await initial.json();
  assert.equal(data.status, 'ok');
  assert.ok(data.metrics.http);
  assert.ok(data.metrics.passkeys);
  assert.ok(data.metrics.telegram);
  assert.ok(data.metrics.webauthnOperations);
  assert.ok(data.metrics.storage);

  // Perform a Telegram login to generate metrics
  const loginRes = await fetch(`${baseUrl}/auth/telegram`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-App-Token': 'metric-test-token'
    },
    body: JSON.stringify({ idToken: 'y'.repeat(40) })
  });
  assert.equal(loginRes.status, 200);

  // Inspect metrics again
  const after = await fetch(`${baseUrl}/api/metrics`, {
    headers: { 'X-App-Token': 'metric-test-token' }
  });
  const afterData = await after.json();
  assert.equal(afterData.metrics.telegram.byOutcome.success, 1);
  assert.ok(afterData.metrics.http.totalRequests >= 3);
});

test('session management: list sessions, revoke others, and delete single session', async (context) => {
  const { baseUrl } = await startServer(context, async () => telegramProfile());

  // Login session 1
  const login1 = await fetch(`${baseUrl}/auth/telegram`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken: 'x'.repeat(40), deviceLabel: 'Pixel Phone' })
  });
  const data1 = await login1.json();
  const token1 = data1.sessionToken;

  // Login session 2 (same user)
  const login2 = await fetch(`${baseUrl}/auth/telegram`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken: 'x'.repeat(40), deviceLabel: 'Desktop Chrome' })
  });
  const data2 = await login2.json();
  const token2 = data2.sessionToken;

  // List sessions with token 1
  const listRes = await fetch(`${baseUrl}/me/sessions`, {
    headers: { Authorization: `Bearer ${token1}` }
  });
  assert.equal(listRes.status, 200);
  const { sessions } = await listRes.json();
  assert.equal(sessions.length, 2);
  const currentS = sessions.find((s) => s.current);
  const otherS = sessions.find((s) => !s.current);
  assert.equal(currentS.deviceLabel, 'Pixel Phone');
  assert.equal(otherS.deviceLabel, 'Desktop Chrome');

  // Both tokens work
  const check1 = await fetch(`${baseUrl}/auth/session`, { headers: { Authorization: `Bearer ${token1}` } });
  const check2 = await fetch(`${baseUrl}/auth/session`, { headers: { Authorization: `Bearer ${token2}` } });
  assert.equal(check1.status, 200);
  assert.equal(check2.status, 200);

  // Revoke session 2 by ID using token 1
  const deleteOther = await fetch(`${baseUrl}/me/sessions/${otherS.id}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token1}` }
  });
  assert.equal(deleteOther.status, 204);

  // Token 2 is now revoked immediately
  const check2After = await fetch(`${baseUrl}/auth/session`, { headers: { Authorization: `Bearer ${token2}` } });
  assert.equal(check2After.status, 401);

  // Login session 3
  const login3 = await fetch(`${baseUrl}/auth/telegram`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken: 'x'.repeat(40), deviceLabel: 'Tablet' })
  });
  const data3 = await login3.json();
  const token3 = data3.sessionToken;

  // Revoke others using token 1
  const revokeOthersRes = await fetch(`${baseUrl}/me/sessions/revoke-others`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token1}` }
  });
  assert.equal(revokeOthersRes.status, 200);
  const revokeData = await revokeOthersRes.json();
  assert.equal(revokeData.revokedCount, 1);

  // Token 3 is now revoked
  const check3After = await fetch(`${baseUrl}/auth/session`, { headers: { Authorization: `Bearer ${token3}` } });
  assert.equal(check3After.status, 401);

  // Token 1 still works
  const check1Final = await fetch(`${baseUrl}/auth/session`, { headers: { Authorization: `Bearer ${token1}` } });
  assert.equal(check1Final.status, 200);

  // Deleting unknown session returns 404
  const notFoundRes = await fetch(`${baseUrl}/me/sessions/nonexistent-session-id`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token1}` }
  });
  assert.equal(notFoundRes.status, 404);
});


