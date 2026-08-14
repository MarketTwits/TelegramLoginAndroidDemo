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
  assert.equal(state.account.member_number, 1);
  assert.equal(state.account.onboarding_state, 'PROFILE_REQUIRED');
  assert.equal(state.account.login_count, 1);
  database.close();
});

test('SQLite v3 profile migrates its legacy emoji to a stable badge id', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'telegram-bloom-profile-'));
  const databasePath = path.join(directory, 'auth.sqlite');
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  let database = createDatabase({ databasePath });
  const state = database.authenticateTelegramUser(profile('user-id'));
  const draft = {
    displayName: 'Demo', headline: 'Building a demo', intent: 'BUILDING',
    topics: ['ANDROID'], avatarSource: 'BLOOM', badgeId: 'festive-flags',
    phoneNumber: '+14155552671'
  };
  database.saveProfile(state.account.id, draft, 'profile-id', 'stable-seed');
  database.close();

  const legacy = new DatabaseSync(databasePath);
  legacy.exec(`
    ALTER TABLE app_profiles DROP COLUMN badge_id;
    ALTER TABLE app_profiles ADD COLUMN emoji TEXT NOT NULL DEFAULT '🚀';
    PRAGMA user_version = 3;
  `);
  legacy.close();

  database = createDatabase({ databasePath });
  const returning = database.authenticateTelegramUser(profile('unused'));
  assert.equal(returning.profile.visual_seed, 'stable-seed');
  assert.equal(returning.profile.badge_id, 'festive-flags');
  assert.equal(returning.profile.display_name, 'Demo');
  assert.equal(returning.profile.phone_number, '+14155552671');
  assert.equal(returning.account.onboarding_state, 'PROFILE_COMPLETED');
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
    topics: ['BACKEND'], avatarSource: 'TELEGRAM', badgeId: 'outline', phoneNumber: null
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
  assert.equal(returning.account.onboarding_state, 'PROFILE_COMPLETED');
  database.close();
});
