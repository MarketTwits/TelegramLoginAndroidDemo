import assert from 'node:assert/strict';
import test from 'node:test';
import { createApp } from '../src/app.js';

test('Telegram login creates, reads and revokes an application session', async (context) => {
  const sessions = new Map();
  const userId = '4f28c230-70d3-452b-99d7-4d7fe8fe9e06';
  const database = {
    ping: async () => undefined,
    upsertTelegramUser: async () => userId,
    createSession: async (tokenHash, id, expiresAt) => sessions.set(tokenHash, { id, expiresAt }),
    findSession: async (tokenHash) => sessions.has(tokenHash) ? {
      id: userId,
      name: 'Demo User',
      given_name: null,
      family_name: null,
      username: 'demo',
      phone_number: null,
      phone_verified: false,
      picture_url: null,
      expires_at: sessions.get(tokenHash).expiresAt
    } : null,
    revokeSession: async (tokenHash) => sessions.delete(tokenHash)
  };
  const config = {
    trustProxy: false,
    authRateLimitPerMinute: 20,
    sessionTtlDays: 30,
    nodeEnv: 'test',
    telegramConfigured: true
  };
  const verifyTelegramToken = async () => ({
    id: 'unused-new-id',
    telegramSubject: '12345',
    name: 'Demo User',
    givenName: null,
    familyName: null,
    username: 'demo',
    phoneNumber: null,
    phoneVerified: false,
    picture: null
  });

  const server = createApp({ config, database, verifyTelegramToken }).listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  context.after(() => new Promise((resolve) => server.close(resolve)));
  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}`;

  const loginResponse = await fetch(`${baseUrl}/auth/telegram`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken: 'x'.repeat(40) })
  });
  assert.equal(loginResponse.status, 200);
  const login = await loginResponse.json();
  assert.equal(login.user.id, userId);
  assert.equal(login.user.username, 'demo');
  assert.ok(login.sessionToken.length >= 32);

  const sessionResponse = await fetch(`${baseUrl}/auth/session`, {
    headers: { Authorization: `Bearer ${login.sessionToken}` }
  });
  assert.equal(sessionResponse.status, 200);
  assert.equal((await sessionResponse.json()).user.name, 'Demo User');

  const logoutResponse = await fetch(`${baseUrl}/auth/session`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${login.sessionToken}` }
  });
  assert.equal(logoutResponse.status, 204);

  const revokedResponse = await fetch(`${baseUrl}/auth/session`, {
    headers: { Authorization: `Bearer ${login.sessionToken}` }
  });
  assert.equal(revokedResponse.status, 401);
});

test('Backend starts without Telegram configuration and reports setup mode', async (context) => {
  const database = {
    ping: async () => undefined,
    findSession: async () => null,
    revokeSession: async () => undefined
  };
  const config = {
    trustProxy: false,
    authRateLimitPerMinute: 20,
    sessionTtlDays: 30,
    nodeEnv: 'test',
    telegramConfigured: false
  };
  const verifyTelegramToken = async () => {
    throw new Error('Verifier must not be called in setup mode');
  };

  const server = createApp({ config, database, verifyTelegramToken }).listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  context.after(() => new Promise((resolve) => server.close(resolve)));
  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}`;

  const healthResponse = await fetch(`${baseUrl}/api/health/ready`);
  assert.equal(healthResponse.status, 200);
  assert.deepEqual(await healthResponse.json(), {
    status: 'ready',
    database: 'connected',
    telegram: 'configuration_required'
  });

  const loginResponse = await fetch(`${baseUrl}/auth/telegram`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken: 'x'.repeat(40) })
  });
  assert.equal(loginResponse.status, 503);
  assert.equal((await loginResponse.json()).code, 'TELEGRAM_NOT_CONFIGURED');
});
