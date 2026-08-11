import crypto from 'node:crypto';

const SESSION_TOKEN_BYTES = 32;
const HOURS_PER_DAY = 24;
const MINUTES_PER_HOUR = 60;
const SECONDS_PER_MINUTE = 60;
const MILLISECONDS_PER_SECOND = 1000;
const MIN_BEARER_TOKEN_LENGTH = 32;
const MAX_BEARER_TOKEN_LENGTH = 256;
const BEARER_PREFIX = 'Bearer ';

export const hashSessionToken = (token) =>
  crypto.createHash('sha256').update(token, 'utf8').digest('hex');

export const createSession = async (database, userId, ttlDays) => {
  const token = crypto.randomBytes(SESSION_TOKEN_BYTES).toString('base64url');
  const ttlMilliseconds = ttlDays
    * HOURS_PER_DAY
    * MINUTES_PER_HOUR
    * SECONDS_PER_MINUTE
    * MILLISECONDS_PER_SECOND;
  const expiresAt = new Date(Date.now() + ttlMilliseconds);
  await database.createSession(hashSessionToken(token), userId, expiresAt);
  return { token, expiresAt };
};

export const bearerToken = (request) => {
  const authorization = request.get('authorization');
  if (!authorization?.startsWith(BEARER_PREFIX)) return null;
  const token = authorization.slice(BEARER_PREFIX.length).trim();
  return token.length >= MIN_BEARER_TOKEN_LENGTH && token.length <= MAX_BEARER_TOKEN_LENGTH
    ? token
    : null;
};
