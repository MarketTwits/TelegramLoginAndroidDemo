import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import test from 'node:test';
import { DatabaseSync } from 'node:sqlite';
import { createDatabase } from '../src/database.js';

const profile = (id, overrides = {}) => ({
  id,
  telegramSubject: 'telegram-subject-1',
  telegramUserId: '987654321',
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
  // A later token without profile scope must not keep exposing a stale scoped claim.
  assert.equal(database.findSession(tokenHash).account.name, null);
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

  assert.equal(database.findSession(tokenHash).account.phone_verified, false);
  database.close();
});

test('SQLite resolves a returning Telegram user by Bot API user ID', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'telegram-signin-user-id-'));
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const database = createDatabase({ databasePath: path.join(directory, 'auth.sqlite') });
  const firstId = database.upsertTelegramUser(profile('user-id-1'));

  const returningId = database.upsertTelegramUser(profile('unused-id', {
    telegramSubject: 'rotated-oidc-subject',
    telegramUserId: '987654321'
  }));

  assert.equal(returningId, firstId);
  assert.equal(database.getAccount(firstId).account.telegram_subject, 'rotated-oidc-subject');
  assert.equal(database.getAccount(firstId).account.telegram_user_id, '987654321');
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

test('SQLite throttles session activity writes while keeping validation current', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'telegram-session-touch-'));
  const databasePath = path.join(directory, 'auth.sqlite');
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const database = createDatabase({ databasePath });
  const userId = database.upsertTelegramUser(profile('user-id-1'));
  const tokenHash = 'c'.repeat(64);
  database.createSession(tokenHash, userId, new Date(Date.now() + 60_000));

  const inspector = new DatabaseSync(databasePath);
  const readLastSeen = () => inspector.prepare(
    'SELECT last_seen_at FROM app_sessions WHERE token_hash = ?'
  ).get(tokenHash).last_seen_at;
  const initialLastSeen = readLastSeen();
  assert.notEqual(database.findSession(tokenHash), null);
  assert.equal(readLastSeen(), initialLastSeen);

  const staleLastSeen = Date.now() - 10 * 60 * 1000;
  inspector.prepare('UPDATE app_sessions SET last_seen_at = ? WHERE token_hash = ?')
    .run(staleLastSeen, tokenHash);
  assert.notEqual(database.findSession(tokenHash), null);
  assert.ok(readLastSeen() > staleLastSeen);

  inspector.close();
  database.close();
});

test('SQLite migration upgrades a version-one user without losing the account', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'telegram-signin-migration-'));
  const databasePath = path.join(directory, 'auth.sqlite');
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const legacy = new DatabaseSync(databasePath);
  legacy.exec(`
    CREATE TABLE app_users (
      id TEXT PRIMARY KEY, telegram_subject TEXT NOT NULL UNIQUE, name TEXT,
      given_name TEXT, family_name TEXT, username TEXT, phone_number TEXT,
      phone_verified INTEGER NOT NULL DEFAULT 0, picture_url TEXT,
      created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, last_login_at INTEGER NOT NULL
    ) STRICT;
    INSERT INTO app_users VALUES (
      'legacy-id', 'legacy-sub', 'Legacy User', NULL, NULL, 'legacy', NULL, 0, NULL,
      1000, 1000, 1000
    );
    PRAGMA user_version = 1;
  `);
  legacy.close();

  const database = createDatabase({ databasePath });
  database.migrate();
  const state = database.authenticateTelegramUser(profile('unused', {
    telegramSubject: 'legacy-sub', name: 'Updated Telegram Name'
  }));
  assert.equal(state.account.id, 'legacy-id');
  assert.equal(state.account.telegram_user_id, '987654321');
  assert.equal(state.account.member_number, 1);
  assert.equal(state.account.onboarding_state, 'PROFILE_REQUIRED');
  assert.equal(state.account.login_count, 1);
  database.close();
});

test('SQLite legacy badge profile migrates to the canonical default and drops old columns', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'telegram-bloom-profile-'));
  const databasePath = path.join(directory, 'auth.sqlite');
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  let database = createDatabase({ databasePath });
  const state = database.authenticateTelegramUser(profile('user-id'));
  const draft = {
    displayName: 'Demo', headline: 'Building a demo', intent: 'BUILDING',
    topics: ['ANDROID'], avatarSource: 'BLOOM',
    emojiStatus: { setId: 'spotty-persik', emojiId: 'e-0007fab99d521710' },
    phoneNumber: '+14155552671'
  };
  database.saveProfile(state.account.id, draft, 'profile-id', 'stable-seed');
  database.close();

  const legacy = new DatabaseSync(databasePath);
  legacy.exec(`
    ALTER TABLE app_profiles ADD COLUMN badge_id TEXT NOT NULL DEFAULT 'festive-flags';
    UPDATE app_profiles SET emoji_set_id = 'classic', emoji_id = 'festive-flags';
    PRAGMA user_version = 6;
  `);
  legacy.close();

  database = createDatabase({ databasePath });
  const returning = database.authenticateTelegramUser(profile('unused'));
  assert.equal(returning.profile.visual_seed, 'stable-seed');
  assert.equal(returning.profile.emoji_set_id, 'spotty-persik');
  assert.equal(returning.profile.emoji_id, 'e-0007fab99d521710');
  assert.equal(returning.profile.display_name, 'Demo');
  assert.equal(returning.profile.phone_number, '+14155552671');
  assert.equal(returning.account.onboarding_state, 'PROFILE_COMPLETED');
  database.close();

  const migrated = new DatabaseSync(databasePath);
  const columns = migrated.prepare('PRAGMA table_info(app_profiles)').all();
  const columnNames = columns.map(({ name }) => name);
  assert.equal(columnNames.includes('badge_id'), false);
  assert.equal(columnNames.includes('emoji'), false);
  assert.equal(columns.find(({ name }) => name === 'emoji_set_id').notnull, 1);
  assert.equal(columns.find(({ name }) => name === 'emoji_id').notnull, 1);
  assert.equal(migrated.prepare('PRAGMA user_version').get().user_version, 9);
  migrated.close();
});

test('SQLite stores, lists, renames, uses, and revokes passkeys per account', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'telegram-passkeys-'));
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const database = createDatabase({ databasePath: path.join(directory, 'auth.sqlite') });
  const firstUserId = database.upsertTelegramUser(profile('passkey-user-1'));
  const secondUserId = database.upsertTelegramUser(profile('passkey-user-2', {
    telegramSubject: 'subject-2', telegramUserId: '123456789'
  }));
  const firstHandle = database.getAccount(firstUserId).account.webauthn_user_handle;
  const secondHandle = database.getAccount(secondUserId).account.webauthn_user_handle;
  assert.match(firstHandle, /^[A-Za-z0-9_-]{43}$/);
  assert.match(secondHandle, /^[A-Za-z0-9_-]{43}$/);
  assert.notEqual(firstHandle, secondHandle);
  database.createPasskey(firstUserId, {
    id: 'internal-key-id', credentialId: 'credential-id', publicKey: Buffer.from([1, 2, 3]),
    counter: 0, transports: ['internal'], aaguid: 'aaguid', deviceType: 'multiDevice',
    backedUp: true, displayName: 'Pixel'
  });

  assert.equal(database.listPasskeys(firstUserId).length, 1);
  assert.equal(database.listPasskeys(secondUserId).length, 0);
  assert.equal(database.renamePasskey(secondUserId, 'internal-key-id', 'Stolen'), false);
  assert.equal(database.renamePasskey(firstUserId, 'internal-key-id', 'Phone'), true);
  assert.equal(database.updatePasskeyUsage('credential-id', 0, 2), true);
  assert.equal(database.updatePasskeyUsage('credential-id', 0, 3), false);
  assert.equal(database.findPasskeyByCredentialId('credential-id').counter, 2);
  assert.equal(database.revokePasskey(secondUserId, 'internal-key-id'), null);
  assert.equal(database.revokePasskey(firstUserId, 'internal-key-id').credential_id, 'credential-id');
  assert.equal(database.findPasskeyByCredentialId('credential-id'), null);
  database.close();
});

test('SQLite consumes WebAuthn operations once and rejects expired operations', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'telegram-webauthn-'));
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const database = createDatabase({ databasePath: path.join(directory, 'auth.sqlite') });
  const now = new Date();
  database.createWebAuthnOperation({
    id: 'valid', purpose: 'AUTHENTICATION', challenge: 'challenge', rpId: 'example.test',
    createdAt: now, expiresAt: new Date(now.getTime() + 60_000)
  });
  assert.equal(database.consumeWebAuthnOperation('valid', 'REGISTRATION'), null);
  assert.equal(database.consumeWebAuthnOperation('valid', 'AUTHENTICATION').challenge, 'challenge');
  assert.equal(database.consumeWebAuthnOperation('valid', 'AUTHENTICATION'), null);
  database.createWebAuthnOperation({
    id: 'expired', purpose: 'AUTHENTICATION', challenge: 'old', rpId: 'example.test',
    createdAt: new Date(now.getTime() - 120_000), expiresAt: new Date(now.getTime() - 60_000)
  });
  assert.equal(database.consumeWebAuthnOperation('expired', 'AUTHENTICATION'), null);
  database.close();
});

test('SQLite v4 profile gains an optional phone without losing existing data', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'telegram-bloom-phone-'));
  const databasePath = path.join(directory, 'auth.sqlite');
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  let database = createDatabase({ databasePath });
  const state = database.authenticateTelegramUser(profile('user-id'));
  database.saveProfile(state.account.id, {
    displayName: 'Existing profile', headline: 'Before phone support', intent: 'EXPLORING',
    topics: ['BACKEND'], avatarSource: 'TELEGRAM',
    emojiStatus: { setId: 'spotty-persik', emojiId: 'e-0007fab99d521710' }, phoneNumber: null
  }, 'profile-id', 'stable-seed');
  database.close();

  const legacy = new DatabaseSync(databasePath);
  legacy.exec('ALTER TABLE app_profiles DROP COLUMN phone_number; PRAGMA user_version = 4;');
  legacy.close();

  database = createDatabase({ databasePath });
  database.migrate();
  const returning = database.authenticateTelegramUser(profile('unused'));
  assert.equal(returning.profile.display_name, 'Existing profile');
  assert.equal(returning.profile.phone_number, null);
  assert.equal(returning.profile.emoji_set_id, 'spotty-persik');
  assert.equal(returning.profile.emoji_id, 'e-0007fab99d521710');
  assert.equal(returning.account.onboarding_state, 'PROFILE_COMPLETED');
  database.close();
});
