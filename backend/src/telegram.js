import crypto from 'node:crypto';
import { createRemoteJWKSet, jwtVerify } from 'jose';

const JWKS_PATH = '/.well-known/jwks.json';
const JWKS_TIMEOUT_MS = 5_000;
const JWKS_COOLDOWN_MS = 30_000;
const CLOCK_TOLERANCE_SECONDS = 5;

const optionalString = (value) => typeof value === 'string' && value.trim() ? value.trim() : null;

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

    const phoneNumber = optionalString(payload.phone_number);
    return {
      id: crypto.randomUUID(),
      telegramSubject,
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

export const publicUserFromVerifiedProfile = (id, profile) => ({
  id,
  ...(profile.name && { name: profile.name }),
  ...(profile.givenName && { givenName: profile.givenName }),
  ...(profile.familyName && { familyName: profile.familyName }),
  ...(profile.username && { username: profile.username }),
  ...(profile.phoneNumber && { phoneNumber: profile.phoneNumber }),
  ...(profile.phoneNumber && { phoneVerified: profile.phoneVerified }),
  ...(profile.picture && { picture: profile.picture })
});
