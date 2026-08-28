const MAX_TCP_PORT = 65_535;
const DEFAULT_PORT = 8080;
const DEFAULT_SESSION_TTL_DAYS = 30;
const DEFAULT_AUTH_RATE_LIMIT_PER_MINUTE = 20;
const DEFAULT_TELEGRAM_ISSUER = 'https://oauth.telegram.org';
const DEFAULT_TELEGRAM_ALGORITHMS = 'RS256,ES256,EdDSA,ES256K';

const positiveInteger = (name, fallback, maximum = Number.MAX_SAFE_INTEGER) => {
  const rawValue = process.env[name]?.trim() || String(fallback);
  const value = Number(rawValue);
  if (!Number.isSafeInteger(value) || value <= 0 || value > maximum) {
    throw new Error(`${name} must be an integer between 1 and ${maximum}`);
  }
  return value;
};

const absoluteHttpsUrl = (name, fallback) => {
  const rawValue = process.env[name]?.trim() || fallback;
  let value;
  try {
    value = new URL(rawValue);
  } catch (error) {
    throw new Error(`${name} must be an absolute HTTPS URL`, { cause: error });
  }
  if (value.protocol !== 'https:') {
    throw new Error(`${name} must be an absolute HTTPS URL`);
  }
  return value.toString().replace(/\/$/, '');
};

const telegramAlgorithms = () => {
  const values = (process.env.TELEGRAM_ALLOWED_ALGORITHMS || DEFAULT_TELEGRAM_ALGORITHMS)
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
  if (values.length === 0) {
    throw new Error('TELEGRAM_ALLOWED_ALGORITHMS must contain at least one algorithm');
  }
  return values;
};

const trustProxy = () => {
  const raw = process.env.TRUST_PROXY?.trim();
  if (!raw || raw === 'false') return false;
  if (raw === 'true') return 1;
  const hops = Number.parseInt(raw, 10);
  if (!Number.isSafeInteger(hops) || hops < 0) {
    throw new Error('TRUST_PROXY must be false, true, or a non-negative hop count');
  }
  return hops;
};

export const loadConfig = () => {
  const nodeEnv = process.env.NODE_ENV?.trim() || 'development';
  const telegramClientId = process.env.TELEGRAM_CLIENT_ID?.trim() || null;
  const appToken = process.env.APP_TOKEN?.trim() || null;
  if (nodeEnv === 'production' && !appToken) {
    throw new Error('APP_TOKEN is required when NODE_ENV=production');
  }
  return {
    nodeEnv,
    port: positiveInteger('PORT', DEFAULT_PORT, MAX_TCP_PORT),
    databasePath: process.env.SQLITE_DATABASE_PATH?.trim() || './data/telegram-signin.sqlite',
    telegramClientId,
    telegramConfigured: telegramClientId !== null,
    telegramIssuer: absoluteHttpsUrl('TELEGRAM_ISSUER', DEFAULT_TELEGRAM_ISSUER),
    telegramAlgorithms: telegramAlgorithms(),
    sessionTtlDays: positiveInteger('SESSION_TTL_DAYS', DEFAULT_SESSION_TTL_DAYS),
    authRateLimitPerMinute: positiveInteger(
      'AUTH_RATE_LIMIT_PER_MINUTE',
      DEFAULT_AUTH_RATE_LIMIT_PER_MINUTE
    ),
    appToken,
    trustProxy: trustProxy()
  };
};
