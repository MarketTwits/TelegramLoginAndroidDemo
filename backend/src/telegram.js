import crypto from 'node:crypto';
import { createRemoteJWKSet, jwtVerify } from 'jose';
import { normalizeInternationalPhoneNumber } from './phoneNumbers.js';

const JWKS_PATH = '/.well-known/jwks.json';
const JWKS_TIMEOUT_MS = 5_000;
const JWKS_COOLDOWN_MS = 30_000;
const CLOCK_TOLERANCE_SECONDS = 5;
const MAX_TELEGRAM_USER_ID = 0xffffffffffn;

const optionalString = (value) => typeof value === 'string' && value.trim() ? value.trim() : null;

export const normalizeTelegramUserId = (value) => {
  const normalized = typeof value === 'number' && Number.isSafeInteger(value)
    ? String(value)
    : optionalString(value);
  if (!normalized || !/^[1-9]\d*$/.test(normalized)) return null;
  return BigInt(normalized) <= MAX_TELEGRAM_USER_ID ? normalized : null;
};

export const createTelegramVerifier = (config) => {
  const keys = createRemoteJWKSet(
    new URL(`${config.telegramIssuer}${JWKS_PATH}`),
    { timeoutDuration: JWKS_TIMEOUT_MS, cooldownDuration: JWKS_COOLDOWN_MS }
  );

  return async (idToken) => {
    const { payload } = await jwtVerify(idToken, keys, {
      issuer: config.telegramIssuer,
      audience: config.telegramClientId,
      algorithms: config.telegramAlgorithms,
      clockTolerance: CLOCK_TOLERANCE_SECONDS
    });

    const telegramSubject = optionalString(payload.sub);
    if (!telegramSubject) throw new Error('Telegram ID token has no subject');
    const telegramUserId = normalizeTelegramUserId(payload.id);
    if (!telegramUserId) throw new Error('Telegram ID token has no valid user ID');

    const rawPhoneNumber = optionalString(payload.phone_number);
    const phoneNumber = normalizeInternationalPhoneNumber(rawPhoneNumber, true);
    console.info(
      '[telegram] token verified ' +
      `phoneClaimPresent=${rawPhoneNumber != null} ` +
      `phoneClaimAccepted=${phoneNumber != null} ` +
      `phoneVerified=${payload.phone_number_verified === true}`
    );
    return {
      id: crypto.randomUUID(),
      telegramSubject,
      telegramUserId,
      name: optionalString(payload.name),
      givenName: optionalString(payload.given_name),
      familyName: optionalString(payload.family_name),
      username: optionalString(payload.preferred_username),
      phoneNumber,
      phoneVerified: payload.phone_number_verified === true,
      picture: optionalString(payload.picture)
    };
  };
};
