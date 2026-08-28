import { loadConfig } from './config.js';
import { createDatabase } from './database.js';
import { createTelegramVerifier } from './telegram.js';
import { createApp } from './app.js';

const SESSION_CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000;
const FORCED_SHUTDOWN_TIMEOUT_MS = 10_000;
const REQUEST_TIMEOUT_MS = 30_000;
const HEADERS_TIMEOUT_MS = 10_000;
const KEEP_ALIVE_TIMEOUT_MS = 5_000;
const MAX_REQUESTS_PER_SOCKET = 1_000;

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
server.requestTimeout = REQUEST_TIMEOUT_MS;
server.headersTimeout = HEADERS_TIMEOUT_MS;
server.keepAliveTimeout = KEEP_ALIVE_TIMEOUT_MS;
server.maxRequestsPerSocket = MAX_REQUESTS_PER_SOCKET;

const sessionCleanup = setInterval(() => {
  try {
    database.deleteExpiredSessions();
  } catch (error) {
    console.error('Could not clean expired sessions:', error);
  }
}, SESSION_CLEANUP_INTERVAL_MS);
sessionCleanup.unref();

let shutdownStarted = false;
const shutdown = (signal) => {
  if (shutdownStarted) return;
  shutdownStarted = true;
  console.log(`${signal} received, shutting down`);
  clearInterval(sessionCleanup);
  const forcedShutdown = setTimeout(() => {
    server.closeAllConnections();
    process.exit(1);
  }, FORCED_SHUTDOWN_TIMEOUT_MS);
  forcedShutdown.unref();
  server.close((error) => {
    clearTimeout(forcedShutdown);
    database.close();
    process.exit(error ? 1 : 0);
  });
};

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
