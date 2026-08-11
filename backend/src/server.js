import { loadConfig } from './config.js';
import { createDatabase } from './database.js';
import { createTelegramVerifier } from './telegram.js';
import { createApp } from './app.js';

const SESSION_CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000;
const FORCED_SHUTDOWN_TIMEOUT_MS = 10_000;

const config = loadConfig();
const database = createDatabase(config);

database.migrate();
database.deleteExpiredSessions();

const app = createApp({
  config,
  database,
  verifyTelegramToken: createTelegramVerifier(config)
});

const server = app.listen(config.port, '0.0.0.0', () => {
  console.log(`Telegram Sign-In API is listening on port ${config.port}`);
  if (!config.telegramConfigured) {
    console.warn('Telegram authentication is disabled. Set TELEGRAM_CLIENT_ID and restart to enable it.');
  }
});

const sessionCleanup = setInterval(() => {
  try {
    database.deleteExpiredSessions();
  } catch (error) {
    console.error('Could not clean expired sessions:', error);
  }
}, SESSION_CLEANUP_INTERVAL_MS);
sessionCleanup.unref();

const shutdown = (signal) => {
  console.log(`${signal} received, shutting down`);
  clearInterval(sessionCleanup);
  server.close(() => {
    database.close();
    process.exit(0);
  });
  setTimeout(() => process.exit(1), FORCED_SHUTDOWN_TIMEOUT_MS).unref();
};

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
