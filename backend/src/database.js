import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';

const SQLITE_BUSY_TIMEOUT_MS = 5_000;
const DATABASE_SCHEMA_VERSION = 2;
const REVOKED_SESSION_RETENTION_MS = 7 * 24 * 60 * 60 * 1000;
const MAX_ACTIVE_SESSIONS_PER_USER = 5;

const baseUserSchema = `
  CREATE TABLE IF NOT EXISTS app_users (
    id TEXT PRIMARY KEY,
    telegram_subject TEXT NOT NULL UNIQUE,
    name TEXT,
    given_name TEXT,
    family_name TEXT,
    username TEXT,
    phone_number TEXT,
    phone_verified INTEGER NOT NULL DEFAULT 0 CHECK (phone_verified IN (0, 1)),
    picture_url TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    last_login_at INTEGER NOT NULL
  ) STRICT;
`;

const schema = `
  CREATE TABLE IF NOT EXISTS app_users (
    id TEXT PRIMARY KEY,
    telegram_subject TEXT NOT NULL UNIQUE,
    name TEXT,
    given_name TEXT,
    family_name TEXT,
    username TEXT,
    phone_number TEXT,
    phone_verified INTEGER NOT NULL DEFAULT 0 CHECK (phone_verified IN (0, 1)),
    picture_url TEXT,
    onboarding_state TEXT NOT NULL DEFAULT 'PROFILE_REQUIRED'
      CHECK (onboarding_state IN ('PROFILE_REQUIRED', 'PROFILE_COMPLETED', 'DISABLED')),
    member_number INTEGER UNIQUE,
    login_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    last_login_at INTEGER NOT NULL,
    telegram_synced_at INTEGER NOT NULL DEFAULT 0
  ) STRICT;

  CREATE TABLE IF NOT EXISTS app_sessions (
    token_hash TEXT PRIMARY KEY CHECK (length(token_hash) = 64),
    user_id TEXT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    revoked_at INTEGER
  ) STRICT;

  CREATE TABLE IF NOT EXISTS app_profiles (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL UNIQUE REFERENCES app_users(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL,
    headline TEXT NOT NULL,
    intent TEXT NOT NULL CHECK (intent IN ('BUILDING', 'HELPING', 'EXPLORING')),
    topics_json TEXT NOT NULL,
    avatar_source TEXT NOT NULL CHECK (avatar_source IN ('TELEGRAM', 'BLOOM')),
    visual_seed TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  ) STRICT;

  CREATE INDEX IF NOT EXISTS app_sessions_user_id_idx ON app_sessions(user_id);
  CREATE INDEX IF NOT EXISTS app_sessions_expires_at_idx ON app_sessions(expires_at);
  CREATE UNIQUE INDEX IF NOT EXISTS app_users_member_number_idx ON app_users(member_number);
`;

const legacyColumns = [
  ['onboarding_state', "TEXT NOT NULL DEFAULT 'PROFILE_REQUIRED'"],
  ['member_number', 'INTEGER'],
  ['login_count', 'INTEGER NOT NULL DEFAULT 0'],
  ['telegram_synced_at', 'INTEGER NOT NULL DEFAULT 0']
];

const migrateLegacyUsers = (database) => {
  const columns = new Set(database.prepare('PRAGMA table_info(app_users)').all().map((row) => row.name));
  for (const [name, definition] of legacyColumns) {
    if (!columns.has(name)) database.exec(`ALTER TABLE app_users ADD COLUMN ${name} ${definition}`);
  }
  database.exec(`
    UPDATE app_users
    SET member_number = rowid
    WHERE member_number IS NULL;
    UPDATE app_users
    SET telegram_synced_at = updated_at
    WHERE telegram_synced_at = 0;
  `);
};

const normalizeUserRow = (row) => row ? ({
  ...row,
  phone_verified: row.phone_verified === 1,
  created_at: new Date(row.created_at),
  last_login_at: new Date(row.last_login_at),
  telegram_synced_at: new Date(row.telegram_synced_at),
  ...(row.expires_at != null && { expires_at: new Date(row.expires_at) })
}) : null;

const normalizeProfileRow = (row) => row?.profile_id ? ({
  id: row.profile_id,
  user_id: row.id,
  display_name: row.display_name,
  headline: row.headline,
  intent: row.intent,
  topics: JSON.parse(row.topics_json),
  avatar_source: row.avatar_source,
  visual_seed: row.visual_seed,
  created_at: new Date(row.profile_created_at),
  updated_at: new Date(row.profile_updated_at)
}) : null;

export const createDatabase = (config) => {
  const databasePath = config.databasePath === ':memory:' ? ':memory:' : path.resolve(config.databasePath);
  if (databasePath !== ':memory:') {
    const databaseDirectory = path.dirname(databasePath);
    fs.mkdirSync(databaseDirectory, { recursive: true, mode: 0o700 });
    fs.chmodSync(databaseDirectory, 0o700);
  }

  const database = new DatabaseSync(databasePath);
  if (databasePath !== ':memory:') fs.chmodSync(databasePath, 0o600);
  database.exec('PRAGMA foreign_keys = ON');
  database.exec(`PRAGMA busy_timeout = ${SQLITE_BUSY_TIMEOUT_MS}`);
  if (databasePath !== ':memory:') database.exec('PRAGMA journal_mode = WAL');
  database.exec('PRAGMA synchronous = NORMAL');
  database.exec(baseUserSchema);
  migrateLegacyUsers(database);
  database.exec(schema);

  const selectUserBySubject = database.prepare(
    'SELECT * FROM app_users WHERE telegram_subject = ?'
  );
  const selectNextMemberNumber = database.prepare(
    'SELECT COALESCE(MAX(member_number), 0) + 1 AS value FROM app_users'
  );
  const insertUser = database.prepare(`
    INSERT INTO app_users (
      id, telegram_subject, name, given_name, family_name, username, phone_number,
      phone_verified, picture_url, onboarding_state, member_number, login_count,
      created_at, updated_at, last_login_at, telegram_synced_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PROFILE_REQUIRED', ?, 1, ?, ?, ?, ?)
  `);
  const updateUserLogin = database.prepare(`
    UPDATE app_users SET
      name = ?, given_name = ?, family_name = ?, username = ?, phone_number = ?,
      phone_verified = ?, picture_url = ?, updated_at = ?, last_login_at = ?,
      telegram_synced_at = ?, login_count = login_count + 1
    WHERE id = ?
  `);
  const disableUser = database.prepare(`
    UPDATE app_users SET onboarding_state = 'DISABLED', updated_at = ? WHERE id = ?
  `);
  const insertSession = database.prepare(`
    INSERT INTO app_sessions (token_hash, user_id, created_at, expires_at, last_seen_at)
    VALUES (?, ?, ?, ?, ?)
  `);
  const revokeSession = database.prepare(`
    UPDATE app_sessions SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL
  `);
  const touchSession = database.prepare(`
    UPDATE app_sessions SET last_seen_at = ?
    WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > ?
  `);
  const accountProfileProjection = `
    SELECT account.*, session.expires_at,
           profile.id AS profile_id, profile.display_name, profile.headline,
           profile.intent, profile.topics_json, profile.avatar_source, profile.visual_seed,
           profile.created_at AS profile_created_at, profile.updated_at AS profile_updated_at
    FROM app_sessions AS session
    JOIN app_users AS account ON account.id = session.user_id
    LEFT JOIN app_profiles AS profile ON profile.user_id = account.id
  `;
  const selectSession = database.prepare(`${accountProfileProjection}
    WHERE session.token_hash = ? AND session.revoked_at IS NULL AND session.expires_at > ?
  `);
  const selectAccountWithProfile = database.prepare(`
    SELECT account.*, NULL AS expires_at,
           profile.id AS profile_id, profile.display_name, profile.headline,
           profile.intent, profile.topics_json, profile.avatar_source, profile.visual_seed,
           profile.created_at AS profile_created_at, profile.updated_at AS profile_updated_at
    FROM app_users AS account
    LEFT JOIN app_profiles AS profile ON profile.user_id = account.id
    WHERE account.id = ?
  `);
  const upsertProfile = database.prepare(`
    INSERT INTO app_profiles (
      id, user_id, display_name, headline, intent, topics_json, avatar_source,
      visual_seed, created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT (user_id) DO UPDATE SET
      display_name = excluded.display_name,
      headline = excluded.headline,
      intent = excluded.intent,
      topics_json = excluded.topics_json,
      avatar_source = excluded.avatar_source,
      updated_at = excluded.updated_at
  `);
  const completeOnboarding = database.prepare(`
    UPDATE app_users SET onboarding_state = 'PROFILE_COMPLETED', updated_at = ? WHERE id = ?
  `);
  const cleanupSessions = database.prepare(`
    DELETE FROM app_sessions WHERE expires_at <= ? OR (revoked_at IS NOT NULL AND revoked_at < ?)
  `);
  const pruneUserSessions = database.prepare(`
    DELETE FROM app_sessions
    WHERE user_id = ? AND token_hash NOT IN (
      SELECT token_hash FROM app_sessions
      WHERE user_id = ? AND revoked_at IS NULL AND expires_at > ?
      ORDER BY created_at DESC, rowid DESC LIMIT ?
    )
  `);

  const readAccount = (row) => row ? ({
    account: normalizeUserRow(row),
    profile: normalizeProfileRow(row)
  }) : null;

  const transaction = (operation) => (...arguments_) => {
    database.exec('BEGIN IMMEDIATE');
    try {
      const result = operation(...arguments_);
      database.exec('COMMIT');
      return result;
    } catch (error) {
      database.exec('ROLLBACK');
      throw error;
    }
  };

  const authenticateTelegramUser = transaction((profile) => {
    const now = Date.now();
    const existing = selectUserBySubject.get(profile.telegramSubject);
    if (existing) {
      if (existing.onboarding_state === 'DISABLED') {
        return readAccount(selectAccountWithProfile.get(existing.id));
      }
      updateUserLogin.run(
        profile.name, profile.givenName, profile.familyName, profile.username,
        profile.phoneNumber, profile.phoneVerified ? 1 : 0, profile.picture,
        now, now, now, existing.id
      );
      return readAccount(selectAccountWithProfile.get(existing.id));
    }
    const memberNumber = selectNextMemberNumber.get().value;
    insertUser.run(
      profile.id, profile.telegramSubject, profile.name, profile.givenName,
      profile.familyName, profile.username, profile.phoneNumber,
      profile.phoneVerified ? 1 : 0, profile.picture, memberNumber,
      now, now, now, now
    );
    return readAccount(selectAccountWithProfile.get(profile.id));
  });

  const saveProfile = transaction((userId, draft, profileId, visualSeed) => {
    const now = Date.now();
    upsertProfile.run(
      profileId, userId, draft.displayName, draft.headline, draft.intent,
      JSON.stringify(draft.topics), draft.avatarSource, visualSeed, now, now
    );
    completeOnboarding.run(now, userId);
    return readAccount(selectAccountWithProfile.get(userId));
  });

  return {
    path: databasePath,
    migrate() {
      migrateLegacyUsers(database);
      database.exec(schema);
      database.exec(`PRAGMA user_version = ${DATABASE_SCHEMA_VERSION}`);
    },
    ping: () => database.prepare('SELECT 1 AS ok').get(),
    close: () => database.close(),

    authenticateTelegramUser,
    upsertTelegramUser(profile) {
      return authenticateTelegramUser(profile).account.id;
    },
    getAccount(userId) {
      return readAccount(selectAccountWithProfile.get(userId));
    },
    saveProfile,
    disableAccount(userId) {
      disableUser.run(Date.now(), userId);
    },

    createSession(tokenHash, userId, expiresAt) {
      const now = Date.now();
      pruneUserSessions.run(userId, userId, now, MAX_ACTIVE_SESSIONS_PER_USER - 1);
      insertSession.run(tokenHash, userId, now, expiresAt.getTime(), now);
    },
    revokeSession(tokenHash) {
      revokeSession.run(Date.now(), tokenHash);
    },
    findSession(tokenHash) {
      const now = Date.now();
      touchSession.run(now, tokenHash, now);
      const row = selectSession.get(tokenHash, now);
      return row ? { ...readAccount(row), expiresAt: new Date(row.expires_at) } : null;
    },
    deleteExpiredSessions() {
      const now = Date.now();
      cleanupSessions.run(now, now - REVOKED_SESSION_RETENTION_MS);
    }
  };
};
