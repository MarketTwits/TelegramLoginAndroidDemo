import crypto from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import express from 'express';
import helmet from 'helmet';
import { rateLimit } from 'express-rate-limit';
import { createSession, bearerToken, hashSessionToken } from './sessions.js';
import { normalizeInternationalPhoneNumber } from './phoneNumbers.js';
import { createPasskeyService, passkeyResponse } from './passkeys.js';
import {
  DEFAULT_PROFILE_EMOJI,
  isProfileEmoji,
  profileEmojiAssetDirectory,
  profileEmojiCatalogResponse,
  resolveStoredProfileEmoji
} from './profileEmojiSets.js';

const publicDirectory = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'public');
const asyncRoute = (handler) => (request, response, next) =>
  Promise.resolve(handler(request, response, next)).catch(next);

const PROFILE_INTENTS = new Set(['BUILDING', 'HELPING', 'EXPLORING']);
const PROFILE_TOPICS = new Set([
  'ANDROID', 'BACKEND', 'DESIGN', 'SECURITY', 'OPEN_SOURCE', 'AI', 'PRODUCT', 'TELEGRAM', 'OTHER'
]);
const AVATAR_SOURCES = new Set(['TELEGRAM', 'BLOOM']);
const API_VERSION = 8;
const APP_REVISION = process.env.APP_REVISION?.trim() || 'development';
const API_PATH_PREFIXES = ['/api/', '/auth/', '/me/'];
const PROFILE_EMOJI_ASSET_PREFIX = '/assets/profile-emojis/';
const FRESH_AUTHENTICATION_MS = 5 * 60 * 1000;

const isApiClientPath = (requestPath) =>
  API_PATH_PREFIXES.some((prefix) => requestPath.startsWith(prefix)) ||
  requestPath.startsWith(PROFILE_EMOJI_ASSET_PREFIX);

const tokensMatch = (actual, expected) => {
  if (typeof actual !== 'string' || typeof expected !== 'string') return false;
  const actualBytes = Buffer.from(actual, 'utf8');
  const expectedBytes = Buffer.from(expected, 'utf8');
  return actualBytes.length === expectedBytes.length &&
    crypto.timingSafeEqual(actualBytes, expectedBytes);
};

const accountResponse = (account) => ({
  id: account.id,
  memberNumber: account.member_number,
  onboardingState: account.onboarding_state,
  registeredAt: account.created_at.toISOString(),
  lastLoginAt: account.last_login_at.toISOString(),
  loginCount: account.login_count
});

const telegramResponse = (account) => ({
  ...(account.telegram_user_id && { userId: account.telegram_user_id }),
  ...(account.name && { name: account.name }),
  ...(account.given_name && { givenName: account.given_name }),
  ...(account.family_name && { familyName: account.family_name }),
  ...(account.username && { username: account.username }),
  ...(account.picture_url && { picture: account.picture_url }),
  ...(account.phone_number && { phoneNumber: account.phone_number }),
  phoneVerified: account.phone_verified,
  syncedAt: account.telegram_synced_at.toISOString()
});

const profileResponse = (profile) => {
  if (!profile) return null;
  const emojiStatus = resolveStoredProfileEmoji(profile);
  return {
    displayName: profile.display_name,
    headline: profile.headline,
    intent: profile.intent,
    topics: profile.topics,
    avatarSource: profile.avatar_source,
    emojiStatus,
    phoneNumber: profile.phone_number,
    visualSeed: profile.visual_seed,
    createdAt: profile.created_at.toISOString(),
    updatedAt: profile.updated_at.toISOString()
  };
};

const authenticationStateResponse = ({ account, profile }, expiresAt) => ({
  ...(expiresAt && { expiresAt: expiresAt.toISOString() }),
  account: accountResponse(account),
  telegram: telegramResponse(account),
  profile: profileResponse(profile)
});

const authenticatedSession = async (database, request) => {
  const token = bearerToken(request);
  return token ? database.findSession(hashSessionToken(token)) : null;
};

const rejectDisabledAccount = (session, response) => {
  if (session.account.onboarding_state !== 'DISABLED') return false;
  response.status(403).json({ code: 'ACCOUNT_DISABLED', message: 'Account is disabled' });
  return true;
};

const profileDraft = (body) => {
  const displayName = typeof body?.displayName === 'string' ? body.displayName.trim() : '';
  const headline = typeof body?.headline === 'string' ? body.headline.trim() : '';
  const topics = Array.isArray(body?.topics) ? body.topics : [];
  const phoneInput = body?.phoneNumber;
  let phoneNumber = null;
  if (phoneInput != null) {
    if (typeof phoneInput !== 'string' || phoneInput.length > 40) return null;
    const trimmedPhone = phoneInput.trim();
    if (trimmedPhone.length === 0) {
      phoneNumber = null;
    } else {
      phoneNumber = normalizeInternationalPhoneNumber(trimmedPhone);
      if (!phoneNumber) return null;
    }
  }
  if (displayName.length < 1 || displayName.length > 80) return null;
  if (headline.length < 1 || headline.length > 120) return null;
  if (!PROFILE_INTENTS.has(body?.intent) || !AVATAR_SOURCES.has(body?.avatarSource)) return null;
  let emojiStatus;
  if (Object.hasOwn(body ?? {}, 'emojiStatus')) {
    if (body.emojiStatus == null) {
      emojiStatus = { ...DEFAULT_PROFILE_EMOJI };
    } else if (isProfileEmoji(body.emojiStatus)) {
      emojiStatus = { setId: body.emojiStatus.setId, emojiId: body.emojiStatus.emojiId };
    } else {
      return null;
    }
  } else {
    emojiStatus = { ...DEFAULT_PROFILE_EMOJI };
  }
  if (topics.length < 1 || topics.length > 3 || new Set(topics).size !== topics.length) return null;
  if (!topics.every((topic) => PROFILE_TOPICS.has(topic))) return null;
  return {
    displayName, headline, intent: body.intent, topics,
    avatarSource: body.avatarSource,
    emojiStatus,
    phoneNumber
  };
};

export const createApp = ({ config, database, verifyTelegramToken }) => {
  const app = express();
  const passkeys = config.passkeysConfigured ? createPasskeyService({ config, database }) : null;
  app.disable('x-powered-by');
  app.set('trust proxy', config.trustProxy);
  app.use(helmet({
    contentSecurityPolicy: {
      directives: {
        frameAncestors: ["'none'"],
        styleSrc: ["'self'"]
      }
    },
    crossOriginOpenerPolicy: { policy: 'same-origin-allow-popups' },
    crossOriginResourcePolicy: { policy: 'same-site' },
    frameguard: { action: 'deny' }
  }));
  app.use((_request, response, next) => {
    response.set('Permissions-Policy', 'camera=(), geolocation=(), microphone=()');
    next();
  });
  app.use((request, response, next) => {
    const requestId = crypto.randomUUID();
    const startedAt = process.hrtime.bigint();
    response.set('X-Request-Id', requestId);
    if (isApiClientPath(request.path)) {
      response.set('X-Telegram-Bloom-Api-Version', String(API_VERSION));
    }
    const logRequest = !request.path.startsWith(PROFILE_EMOJI_ASSET_PREFIX);
    if (logRequest) {
      console.info(`[http] --> ${request.method} ${request.path} requestId=${requestId}`);
    }
    response.once('finish', () => {
      if (!logRequest && response.statusCode < 400) return;
      const durationMs = Number(process.hrtime.bigint() - startedAt) / 1_000_000;
      console.info(
        `[http] <-- ${response.statusCode} ${request.method} ${request.path} ` +
        `durationMs=${durationMs.toFixed(1)} requestId=${requestId}`
      );
    });
    next();
  });
  app.use(express.json({ limit: '64kb', type: 'application/json' }));

  const apiLimiter = rateLimit({
    windowMs: 60_000,
    limit: 100,
    standardHeaders: 'draft-8',
    legacyHeaders: false
  });

  const authLimiter = rateLimit({
    windowMs: 60_000,
    limit: config.authRateLimitPerMinute,
    standardHeaders: 'draft-8',
    legacyHeaders: false
  });

  const profileLimiter = rateLimit({
    windowMs: 60_000,
    limit: 10,
    standardHeaders: 'draft-8',
    legacyHeaders: false
  });

  const requireAppToken = (request, response, next) => {
    if (!config.appToken) return next();
    const token = request.get('X-App-Token');
    if (!tokensMatch(token, config.appToken)) {
      response.set('Cache-Control', 'no-store');
      return response.status(403).json({ code: 'FORBIDDEN', message: 'Invalid or missing X-App-Token' });
    }
    next();
  };

  const requireJsonBody = (request, response, next) => {
    if (request.is('application/json')) return next();
    response.status(415).json({
      code: 'UNSUPPORTED_MEDIA_TYPE',
      message: 'Content-Type must be application/json'
    });
  };

  const preventSensitiveCaching = (_request, response, next) => {
    response.set('Cache-Control', 'no-store');
    next();
  };

  // Content-addressed emoji assets are immutable public files. They must not consume the
  // API request budget: one visible picker page legitimately needs multiple small TGS files.
  app.use('/assets/profile-emojis', express.static(profileEmojiAssetDirectory, {
    maxAge: config.nodeEnv === 'production' ? '1y' : 0,
    immutable: config.nodeEnv === 'production',
    etag: true,
    setHeaders(response, filePath) {
      if (filePath.endsWith('.tgs')) response.type('application/x-tgsticker');
    }
  }));

  app.use(['/api', '/auth', '/me'], apiLimiter);
  app.use(['/auth', '/me'], preventSensitiveCaching);

  app.get('/api/health/live', (_request, response) => response.json({ status: 'ok' }));

  app.get('/.well-known/assetlinks.json', (_request, response) => {
    if (!config.passkeyAndroidPackage || !config.passkeyAndroidCertSha256?.length) {
      return response.status(404).json({ code: 'NOT_FOUND', message: 'Resource not found' });
    }
    response.type('application/json').json([{
      relation: ['delegate_permission/common.get_login_creds'],
      target: {
        namespace: 'android_app',
        package_name: config.passkeyAndroidPackage,
        sha256_cert_fingerprints: config.passkeyAndroidCertSha256
      }
    }]);
  });

  app.get('/api/profile-emoji-sets', (_request, response) => {
    response.set('Cache-Control', 'public, max-age=300, must-revalidate');
    response.json(profileEmojiCatalogResponse());
  });

  app.get('/api/health/ready', requireAppToken, asyncRoute(async (_request, response) => {
    await database.ping();
    response.set('Cache-Control', 'no-store').json({
      status: 'ready',
      database: 'connected',
      telegram: config.telegramConfigured ? 'configured' : 'configuration_required',
      passkeys: config.passkeysConfigured ? 'configured' : 'configuration_required',
      apiVersion: API_VERSION,
      revision: APP_REVISION
    });
  }));

  app.post('/auth/telegram', requireAppToken, requireJsonBody, authLimiter, asyncRoute(async (request, response) => {
    if (!config.telegramConfigured) {
      return response.status(503).json({
        code: 'TELEGRAM_NOT_CONFIGURED',
        message: 'Set TELEGRAM_CLIENT_ID and restart the backend to enable Telegram authentication'
      });
    }
    const idToken = request.body?.idToken;
    if (typeof idToken !== 'string' || idToken.length < 32 || idToken.length > 16_000) {
      return response.status(400).json({ code: 'INVALID_REQUEST', message: 'idToken is required' });
    }

    let telegramProfile;
    try {
      telegramProfile = await verifyTelegramToken(idToken);
    } catch (error) {
      console.warn('Rejected Telegram ID token:', error.code ?? error.message);
      return response.status(401).json({
        code: 'INVALID_TELEGRAM_TOKEN',
        message: 'Telegram authorization was rejected'
      });
    }

    const state = await database.authenticateTelegramUser(telegramProfile);
    if (state.account.onboarding_state === 'DISABLED') {
      return response.status(403).json({ code: 'ACCOUNT_DISABLED', message: 'Account is disabled' });
    }
    const session = await createSession(database, state.account.id, config.sessionTtlDays);
    response.set('Cache-Control', 'no-store').json({
      sessionToken: session.token,
      ...authenticationStateResponse(state, session.expiresAt)
    });
  }));

  app.post('/auth/passkeys/options', requireAppToken, requireJsonBody, authLimiter, asyncRoute(async (_request, response) => {
    if (!passkeys) {
      return response.status(503).json({ code: 'PASSKEYS_NOT_CONFIGURED', message: 'Passkey authentication is not configured' });
    }
    const options = await passkeys.authenticationOptions();
    response.json({ ...options, expiresAt: options.expiresAt.toISOString() });
  }));

  app.post('/auth/passkeys/verify', requireAppToken, requireJsonBody, authLimiter, asyncRoute(async (request, response) => {
    if (!passkeys) {
      return response.status(503).json({ code: 'PASSKEYS_NOT_CONFIGURED', message: 'Passkey authentication is not configured' });
    }
    const { operationId, credential } = request.body ?? {};
    if (typeof operationId !== 'string' || !credential || typeof credential !== 'object') {
      return response.status(400).json({ code: 'INVALID_REQUEST', message: 'operationId and credential are required' });
    }
    let verified;
    try {
      verified = await passkeys.verifyAuthentication({ operationId, credential });
    } catch (error) {
      console.warn('Rejected passkey assertion:', error.message);
    }
    if (!verified) {
      return response.status(401).json({ code: 'INVALID_PASSKEY', message: 'Passkey authentication was rejected' });
    }
    const state = await database.getAccount(verified.userId);
    const session = await createSession(database, verified.userId, config.sessionTtlDays, 'PASSKEY');
    response.json({ sessionToken: session.token, ...authenticationStateResponse(state, session.expiresAt) });
  }));

  app.get('/auth/session', requireAppToken, asyncRoute(async (request, response) => {
    const session = await authenticatedSession(database, request);
    if (!session) {
      return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    }
    if (rejectDisabledAccount(session, response)) return;
    response.set('Cache-Control', 'no-store').json(
      authenticationStateResponse(session, session.expiresAt)
    );
  }));

  app.get('/me/passkeys', requireAppToken, asyncRoute(async (request, response) => {
    const session = await authenticatedSession(database, request);
    if (!session) return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    if (rejectDisabledAccount(session, response)) return;
    response.json({ passkeys: database.listPasskeys(session.account.id).map(passkeyResponse) });
  }));

  app.post('/me/reauth/passkeys/options', requireAppToken, requireJsonBody, authLimiter, asyncRoute(async (request, response) => {
    const token = bearerToken(request);
    const tokenHash = token ? hashSessionToken(token) : null;
    const session = tokenHash ? await database.findSession(tokenHash) : null;
    if (!session) return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    if (rejectDisabledAccount(session, response)) return;
    if (!passkeys) return response.status(503).json({ code: 'PASSKEYS_NOT_CONFIGURED', message: 'Passkeys are not configured' });
    const options = await passkeys.reauthenticationOptions(session.account.id, tokenHash);
    if (!options) return response.status(409).json({ code: 'PASSKEY_REQUIRED', message: 'This account has no passkey for reauthentication' });
    response.json({ ...options, expiresAt: options.expiresAt.toISOString() });
  }));

  app.post('/me/reauth/telegram', requireAppToken, requireJsonBody, authLimiter, asyncRoute(async (request, response) => {
    const token = bearerToken(request);
    const tokenHash = token ? hashSessionToken(token) : null;
    const session = tokenHash ? await database.findSession(tokenHash) : null;
    if (!session) return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    if (rejectDisabledAccount(session, response)) return;
    if (!config.telegramConfigured) return response.status(503).json({ code: 'TELEGRAM_NOT_CONFIGURED', message: 'Telegram authentication is not configured' });
    const idToken = request.body?.idToken;
    if (typeof idToken !== 'string' || idToken.length < 32 || idToken.length > 16_000) {
      return response.status(400).json({ code: 'INVALID_REQUEST', message: 'idToken is required' });
    }
    let telegramProfile;
    try {
      telegramProfile = await verifyTelegramToken(idToken);
    } catch (error) {
      return response.status(401).json({ code: 'INVALID_TELEGRAM_TOKEN', message: 'Telegram authorization was rejected' });
    }
    if (telegramProfile.telegramUserId !== session.account.telegram_user_id) {
      return response.status(403).json({ code: 'ACCOUNT_MISMATCH', message: 'Telegram account does not match the current session' });
    }
    try {
      const accepted = database.reauthenticateWithProof(
        tokenHash,
        session.account.id,
        crypto.createHash('sha256').update(idToken, 'utf8').digest('hex')
      );
      if (!accepted) return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    } catch (error) {
      if (error.errcode === 1555) {
        return response.status(409).json({ code: 'REAUTHENTICATION_REPLAYED', message: 'This proof has already been used' });
      }
      throw error;
    }
    response.status(204).end();
  }));

  app.post('/me/reauth/passkeys/verify', requireAppToken, requireJsonBody, authLimiter, asyncRoute(async (request, response) => {
    const token = bearerToken(request);
    const tokenHash = token ? hashSessionToken(token) : null;
    const session = tokenHash ? await database.findSession(tokenHash) : null;
    if (!session) return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    if (rejectDisabledAccount(session, response)) return;
    if (!passkeys) return response.status(503).json({ code: 'PASSKEYS_NOT_CONFIGURED', message: 'Passkeys are not configured' });
    const { operationId, credential } = request.body ?? {};
    if (typeof operationId !== 'string' || !credential || typeof credential !== 'object') {
      return response.status(400).json({ code: 'INVALID_REQUEST', message: 'operationId and credential are required' });
    }
    let verified;
    try {
      verified = await passkeys.verifyReauthentication({ operationId, credential, sessionTokenHash: tokenHash });
    } catch (error) {
      console.warn('Rejected passkey reauthentication:', error.message);
    }
    if (!verified || !database.reauthenticateSession(tokenHash, verified.userId)) {
      return response.status(401).json({ code: 'INVALID_PASSKEY', message: 'Passkey reauthentication was rejected' });
    }
    response.status(204).end();
  }));

  app.post('/me/passkeys/registration/options', requireAppToken, requireJsonBody, authLimiter, asyncRoute(async (request, response) => {
    const token = bearerToken(request);
    const session = token ? await database.findSession(hashSessionToken(token)) : null;
    if (!session) return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    if (rejectDisabledAccount(session, response)) return;
    if (!passkeys) return response.status(503).json({ code: 'PASSKEYS_NOT_CONFIGURED', message: 'Passkeys are not configured' });
    if (Date.now() - session.reauthenticatedAt.getTime() > FRESH_AUTHENTICATION_MS) {
      return response.status(403).json({ code: 'REAUTHENTICATION_REQUIRED', message: 'Sign in again before changing passkeys' });
    }
    let options;
    try {
      options = await passkeys.registrationOptions(session.account.id, hashSessionToken(token));
    } catch (error) {
      if (error.code === 'PASSKEY_LIMIT_REACHED') {
        return response.status(409).json({ code: error.code, message: error.message });
      }
      throw error;
    }
    response.json({ ...options, expiresAt: options.expiresAt.toISOString() });
  }));

  app.post('/me/passkeys/registration/verify', requireAppToken, requireJsonBody, authLimiter, asyncRoute(async (request, response) => {
    const token = bearerToken(request);
    const session = token ? await database.findSession(hashSessionToken(token)) : null;
    if (!session) return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    if (rejectDisabledAccount(session, response)) return;
    if (!passkeys) return response.status(503).json({ code: 'PASSKEYS_NOT_CONFIGURED', message: 'Passkeys are not configured' });
    const { operationId, credential } = request.body ?? {};
    const displayName = typeof request.body?.name === 'string' ? request.body.name.trim() : 'Passkey';
    if (typeof operationId !== 'string' || !credential || displayName.length < 1 || displayName.length > 80) {
      return response.status(400).json({ code: 'INVALID_REQUEST', message: 'Invalid passkey registration response' });
    }
    let registered;
    try {
      registered = await passkeys.verifyRegistration({
        operationId, credential, displayName, sessionTokenHash: hashSessionToken(token)
      });
    } catch (error) {
      console.warn('Rejected passkey registration:', error.message);
    }
    if (!registered) return response.status(401).json({ code: 'INVALID_PASSKEY', message: 'Passkey registration was rejected' });
    response.status(201).json({ passkeys: registered.map(passkeyResponse) });
  }));

  app.patch('/me/passkeys/:id', requireAppToken, requireJsonBody, asyncRoute(async (request, response) => {
    const session = await authenticatedSession(database, request);
    if (!session) return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    if (rejectDisabledAccount(session, response)) return;
    if (Date.now() - session.reauthenticatedAt.getTime() > FRESH_AUTHENTICATION_MS) {
      return response.status(403).json({ code: 'REAUTHENTICATION_REQUIRED', message: 'Sign in again before changing passkeys' });
    }
    const name = typeof request.body?.name === 'string' ? request.body.name.trim() : '';
    if (name.length < 1 || name.length > 80) return response.status(422).json({ code: 'INVALID_PASSKEY_NAME', message: 'Passkey name must contain 1 to 80 characters' });
    if (!database.renamePasskey(session.account.id, request.params.id, name)) return response.status(404).json({ code: 'PASSKEY_NOT_FOUND', message: 'Passkey not found' });
    response.json({ passkeys: database.listPasskeys(session.account.id).map(passkeyResponse) });
  }));

  app.delete('/me/passkeys/:id', requireAppToken, asyncRoute(async (request, response) => {
    const session = await authenticatedSession(database, request);
    if (!session) return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    if (rejectDisabledAccount(session, response)) return;
    if (Date.now() - session.reauthenticatedAt.getTime() > FRESH_AUTHENTICATION_MS) {
      return response.status(403).json({ code: 'REAUTHENTICATION_REQUIRED', message: 'Sign in again before changing passkeys' });
    }
    const revoked = database.revokePasskey(session.account.id, request.params.id);
    if (!revoked) return response.status(404).json({ code: 'PASSKEY_NOT_FOUND', message: 'Passkey not found' });
    response.json({ credentialId: revoked.credential_id, rpId: config.passkeyRpId });
  }));

  app.put('/me/profile', requireAppToken, profileLimiter, asyncRoute(async (request, response) => {
    const session = await authenticatedSession(database, request);
    if (!session) {
      return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    }
    if (rejectDisabledAccount(session, response)) return;
    if (!request.is('application/json')) {
      return response.status(415).json({
        code: 'UNSUPPORTED_MEDIA_TYPE',
        message: 'Content-Type must be application/json'
      });
    }
    const draft = profileDraft(request.body);
    if (!draft) {
      return response.status(422).json({
        code: 'INVALID_PROFILE',
        message: 'Profile fields do not satisfy the required format'
      });
    }
    const state = await database.saveProfile(
      session.account.id,
      draft,
      session.profile?.id ?? crypto.randomUUID(),
      session.profile?.visual_seed ?? crypto.randomBytes(16).toString('hex')
    );
    response.set('Cache-Control', 'no-store').json(authenticationStateResponse(state, session.expiresAt));
  }));

  app.delete('/me/account', requireAppToken, asyncRoute(async (request, response) => {
    const session = await authenticatedSession(database, request);
    if (!session) {
      return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    }
    if (rejectDisabledAccount(session, response)) return;
    await database.deleteAccount(session.account.id);
    response.set('Cache-Control', 'no-store').status(204).end();
  }));

  app.delete('/auth/session', requireAppToken, asyncRoute(async (request, response) => {
    const token = bearerToken(request);
    if (token) await database.revokeSession(hashSessionToken(token));
    response.status(204).end();
  }));

  app.use(express.static(publicDirectory, {
    extensions: ['html'],
    maxAge: config.nodeEnv === 'production' ? '1h' : 0,
    etag: true
  }));
  app.use((_request, response) => {
    response.status(404).json({ code: 'NOT_FOUND', message: 'Resource not found' });
  });
  app.use((error, _request, response, _next) => {
    if (error?.type === 'entity.parse.failed') {
      return response.status(400).json({ code: 'INVALID_JSON', message: 'Request body must be valid JSON' });
    }
    if (error?.type === 'entity.too.large') {
      return response.status(413).json({ code: 'PAYLOAD_TOO_LARGE', message: 'Request body is too large' });
    }
    console.error('Unhandled request error:', error);
    response.status(500).json({ code: 'INTERNAL_ERROR', message: 'The server could not process the request' });
  });

  return app;
};
