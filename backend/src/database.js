import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';

const SQLITE_BUSY_TIMEOUT_MS = 5_000;
const DATABASE_SCHEMA_VERSION = 1;
const REVOKED_SESSION_RETENTION_MS = 7 * 24 * 60 * 60 * 1000;
const MAX_ACTIVE_SESSIONS_PER_USER = 5;

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
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    last_login_at INTEGER NOT NULL
  ) STRICT;

  CREATE TABLE IF NOT EXISTS app_sessions (
    token_hash TEXT PRIMARY KEY CHECK (length(token_hash) = 64),
    user_id TEXT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    revoked_at INTEGER
  ) STRICT;

  CREATE INDEX IF NOT EXISTS app_sessions_user_id_idx ON app_sessions(user_id);
  CREATE INDEX IF NOT EXISTS app_sessions_expires_at_idx ON app_sessions(expires_at);
`;

export const createDatabase = (config) => {
  const databasePath = config.databasePath === ':memory:'
    ? ':memory:'
    : path.resolve(config.databasePath);
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
  database.exec(schema);

  const upsertUser = database.prepare(`
    INSERT INTO app_users (
      id, telegram_subject, name, given_name, family_name, username,
      phone_number, phone_verified, picture_url, created_at, updated_at, last_login_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT (telegram_subject) DO UPDATE SET
      name = COALESCE(excluded.name, app_users.name),
      given_name = COALESCE(excluded.given_name, app_users.given_name),
      family_name = COALESCE(excluded.family_name, app_users.family_name),
      username = COALESCE(excluded.username, app_users.username),
      phone_number = COALESCE(excluded.phone_number, app_users.phone_number),
      phone_verified = excluded.phone_verified,
      picture_url = COALESCE(excluded.picture_url, app_users.picture_url),
      updated_at = excluded.updated_at,
      last_login_at = excluded.last_login_at
    RETURNING id
  `);
  const insertSession = database.prepare(`
    INSERT INTO app_sessions (token_hash, user_id, created_at, expires_at, last_seen_at)
    VALUES (?, ?, ?, ?, ?)
  `);
  const revokeSession = database.prepare(`
    UPDATE app_sessions SET revoked_at = ?
    WHERE token_hash = ? AND revoked_at IS NULL
  `);
  const touchSession = database.prepare(`
    UPDATE app_sessions SET last_seen_at = ?
    WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > ?
  `);
  const selectSession = database.prepare(`
    SELECT account.id, account.name, account.given_name, account.family_name,
           account.username, account.phone_number, account.phone_verified,
           account.picture_url, session.expires_at
    FROM app_sessions AS session
    JOIN app_users AS account ON account.id = session.user_id
    WHERE session.token_hash = ?
      AND session.revoked_at IS NULL
      AND session.expires_at > ?
  `);
  const cleanupSessions = database.prepare(`
    DELETE FROM app_sessions
    WHERE expires_at <= ? OR (revoked_at IS NOT NULL AND revoked_at < ?)
  `);
  const pruneUserSessions = database.prepare(`
    DELETE FROM app_sessions
    WHERE user_id = ?
      AND token_hash NOT IN (
        SELECT token_hash
        FROM app_sessions
        WHERE user_id = ? AND revoked_at IS NULL AND expires_at > ?
        ORDER BY created_at DESC, rowid DESC
        LIMIT ?
      )
  `);

  return {
    path: databasePath,
    migrate() {
      // Kept idempotent so future schema versions have a single migration entry point.
      database.exec(schema);
      database.exec(`PRAGMA user_version = ${DATABASE_SCHEMA_VERSION}`);
    },
    ping: () => database.prepare('SELECT 1 AS ok').get(),
    close: () => database.close(),

    upsertTelegramUser(profile) {
      const now = Date.now();
      return upsertUser.get(
        profile.id,
        profile.telegramSubject,
        profile.name,
        profile.givenName,
        profile.familyName,
        profile.username,
        profile.phoneNumber,
        profile.phoneVerified ? 1 : 0,
        profile.picture,
        now,
        now,
        now
      ).id;
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
      return row ? {
        ...row,
        phone_verified: row.phone_verified === 1,
        expires_at: new Date(row.expires_at)
      } : null;
    },

    deleteExpiredSessions() {
      const now = Date.now();
      cleanupSessions.run(now, now - REVOKED_SESSION_RETENTION_MS);
    }
  };
};
