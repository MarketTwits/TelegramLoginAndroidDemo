import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import test from 'node:test';
import { createDatabase } from '../src/database.js';

const profile = (id, overrides = {}) => ({
  id,
  telegramSubject: 'telegram-subject-1',
  name: 'Demo User',
  givenName: 'Demo',
  familyName: 'User',
  username: 'demo',
  phoneNumber: null,
  phoneVerified: false,
  picture: null,
  ...overrides
});

test('SQLite persists users and supports revocable sessions', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'telegram-signin-sqlite-'));
  const databasePath = path.join(directory, 'auth.sqlite');
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));

  let database = createDatabase({ databasePath });
  database.migrate();
  const userId = database.upsertTelegramUser(profile('user-id-1'));
  assert.equal(userId, 'user-id-1');
  database.close();

  database = createDatabase({ databasePath });
  database.migrate();
  const persistedId = database.upsertTelegramUser(profile('unused-new-id', { name: null }));
  assert.equal(persistedId, 'user-id-1');

  const tokenHash = 'a'.repeat(64);
  database.createSession(tokenHash, persistedId, new Date(Date.now() + 60_000));
  assert.equal(database.findSession(tokenHash).name, 'Demo User');
  database.revokeSession(tokenHash);
  assert.equal(database.findSession(tokenHash), null);
  database.close();
});

test('SQLite follows the latest explicit phone verification claim', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'telegram-signin-sqlite-'));
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const database = createDatabase({ databasePath: path.join(directory, 'auth.sqlite') });
  const userId = database.upsertTelegramUser(profile('user-id-1', {
    phoneNumber: '+10000000000',
    phoneVerified: true
  }));
  database.upsertTelegramUser(profile('unused-id', {
    phoneNumber: '+10000000000',
    phoneVerified: false
  }));
  const tokenHash = 'b'.repeat(64);
  database.createSession(tokenHash, userId, new Date(Date.now() + 60_000));

  assert.equal(database.findSession(tokenHash).phone_verified, false);
  database.close();
});

test('SQLite limits active sessions for one user', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'telegram-signin-sqlite-'));
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const database = createDatabase({ databasePath: path.join(directory, 'auth.sqlite') });
  const userId = database.upsertTelegramUser(profile('user-id-1'));
  const tokens = Array.from({ length: 6 }, (_, index) =>
    (index + 1).toString(16).repeat(64)
  );
  for (const token of tokens) {
    database.createSession(token, userId, new Date(Date.now() + 60_000));
  }

  assert.equal(database.findSession(tokens[0]), null);
  assert.notEqual(database.findSession(tokens.at(-1)), null);
  database.close();
});
