const MAX_TCP_PORT = 65_535;
const DEFAULT_PORT = 8080;
const DEFAULT_SESSION_TTL_DAYS = 30;
const DEFAULT_AUTH_RATE_LIMIT_PER_MINUTE = 20;
const DEFAULT_TELEGRAM_ISSUER = 'https://oauth.telegram.org';
const DEFAULT_TELEGRAM_ALGORITHMS = 'RS256,ES256,EdDSA,ES256K';
const DEFAULT_PASSKEY_RP_NAME = 'Telegram Login Demo';

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

const passkeyOrigins = () => (process.env.PASSKEY_ALLOWED_ORIGINS || '')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean);

const commaSeparated = (name) => (process.env[name] || '')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean);

const androidOriginForFingerprint = (fingerprint) => {
  const digest = Buffer.from(fingerprint.replaceAll(':', ''), 'hex').toString('base64url');
  return `android:apk-key-hash:${digest}`;
};

const passkeyConfiguration = () => {
  const rpId = process.env.PASSKEY_RP_ID?.trim() || null;
  const origins = passkeyOrigins();
  const androidPackage = process.env.PASSKEY_ANDROID_PACKAGE?.trim() || null;
  const androidCertSha256 = commaSeparated('PASSKEY_ANDROID_CERT_SHA256');
  const supplied = [rpId, origins.length > 0, androidPackage, androidCertSha256.length > 0]
    .filter(Boolean).length;
  if (supplied > 0 && supplied < 4) {
    throw new Error(
      'PASSKEY_RP_ID, PASSKEY_ALLOWED_ORIGINS, PASSKEY_ANDROID_PACKAGE, and ' +
      'PASSKEY_ANDROID_CERT_SHA256 must be configured together'
    );
  }
  if (rpId && (!/^(?=.{1,253}$)(?!-)[a-z0-9.-]+(?<!-)$/.test(rpId) || rpId.includes('..'))) {
    throw new Error('PASSKEY_RP_ID must be a lowercase DNS hostname without a scheme, port, or path');
  }
  if (androidPackage && !/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/.test(androidPackage)) {
    throw new Error('PASSKEY_ANDROID_PACKAGE must be a valid Android application ID');
  }
  if (androidCertSha256.some((value) => !/^(?:[0-9A-F]{2}:){31}[0-9A-F]{2}$/.test(value))) {
    throw new Error('PASSKEY_ANDROID_CERT_SHA256 must contain uppercase colon-delimited SHA-256 fingerprints');
  }
  const androidOrigins = new Set(androidCertSha256.map(androidOriginForFingerprint));
  const invalidOrigin = origins.some((origin) => {
    if (origin.startsWith('android:apk-key-hash:')) return !androidOrigins.has(origin);
    try {
      const url = new URL(origin);
      const hostMatchesRp = url.hostname === rpId || url.hostname.endsWith(`.${rpId}`);
      return url.protocol !== 'https:' || url.origin !== origin || !hostMatchesRp;
    } catch {
      return true;
    }
  });
  if (invalidOrigin || (supplied === 4 && [...androidOrigins].some((origin) => !origins.includes(origin)))) {
    throw new Error(
      'PASSKEY_ALLOWED_ORIGINS must contain valid HTTPS RP origins and an Android origin ' +
      'matching each configured signing certificate'
    );
  }
  return { rpId, origins, androidPackage, androidCertSha256, configured: supplied === 4 };
};

export const loadConfig = () => {
  const nodeEnv = process.env.NODE_ENV?.trim() || 'development';
  const telegramClientId = process.env.TELEGRAM_CLIENT_ID?.trim() || null;
  const appToken = process.env.APP_TOKEN?.trim() || null;
  const passkey = passkeyConfiguration();
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
    passkeyRpId: passkey.rpId,
    passkeyRpName: process.env.PASSKEY_RP_NAME?.trim() || DEFAULT_PASSKEY_RP_NAME,
    passkeyAllowedOrigins: passkey.origins,
    passkeyAndroidPackage: passkey.androidPackage,
    passkeyAndroidCertSha256: passkey.androidCertSha256,
    passkeysConfigured: passkey.configured,
    sessionTtlDays: positiveInteger('SESSION_TTL_DAYS', DEFAULT_SESSION_TTL_DAYS),
    authRateLimitPerMinute: positiveInteger(
      'AUTH_RATE_LIMIT_PER_MINUTE',
      DEFAULT_AUTH_RATE_LIMIT_PER_MINUTE
    ),
    appToken,
    trustProxy: trustProxy()
  };
};
