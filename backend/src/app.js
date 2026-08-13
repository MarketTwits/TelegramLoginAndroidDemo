import crypto from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import express from 'express';
import helmet from 'helmet';
import { rateLimit } from 'express-rate-limit';
import { createSession, bearerToken, hashSessionToken } from './sessions.js';

const publicDirectory = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'public');
const asyncRoute = (handler) => (request, response, next) =>
  Promise.resolve(handler(request, response, next)).catch(next);

const PROFILE_INTENTS = new Set(['BUILDING', 'HELPING', 'EXPLORING']);
const PROFILE_TOPICS = new Set([
  'ANDROID', 'BACKEND', 'DESIGN', 'SECURITY', 'OPEN_SOURCE', 'AI', 'PRODUCT', 'TELEGRAM', 'OTHER'
]);
const AVATAR_SOURCES = new Set(['TELEGRAM', 'BLOOM']);
const PROFILE_EMOJIS = new Set(['🌱', '🚀', '💡', '🛠️', '✨']);
const API_VERSION = 3;
const APP_REVISION = process.env.APP_REVISION?.trim() || 'development';

const accountResponse = (account) => ({
  id: account.id,
  memberNumber: account.member_number,
  onboardingState: account.onboarding_state,
  registeredAt: account.created_at.toISOString(),
  lastLoginAt: account.last_login_at.toISOString(),
  loginCount: account.login_count
});

const telegramResponse = (account) => ({
  ...(account.name && { name: account.name }),
  ...(account.given_name && { givenName: account.given_name }),
  ...(account.family_name && { familyName: account.family_name }),
  ...(account.username && { username: account.username }),
  ...(account.picture_url && { picture: account.picture_url }),
  phoneVerified: account.phone_verified,
  syncedAt: account.telegram_synced_at.toISOString()
});

const profileResponse = (profile) => profile ? ({
  displayName: profile.display_name,
  headline: profile.headline,
  intent: profile.intent,
  topics: profile.topics,
  avatarSource: profile.avatar_source,
  emoji: profile.emoji,
  visualSeed: profile.visual_seed,
  createdAt: profile.created_at.toISOString(),
  updatedAt: profile.updated_at.toISOString()
}) : null;

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
  if (displayName.length < 1 || displayName.length > 80) return null;
  if (headline.length < 1 || headline.length > 120) return null;
  if (!PROFILE_INTENTS.has(body?.intent) || !AVATAR_SOURCES.has(body?.avatarSource)) return null;
  if (!PROFILE_EMOJIS.has(body?.emoji)) return null;
  if (topics.length < 1 || topics.length > 3 || new Set(topics).size !== topics.length) return null;
  if (!topics.every((topic) => PROFILE_TOPICS.has(topic))) return null;
  return {
    displayName, headline, intent: body.intent, topics,
    avatarSource: body.avatarSource, emoji: body.emoji
  };
};

export const createApp = ({ config, database, verifyTelegramToken }) => {
  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', config.trustProxy);
  app.use((request, response, next) => {
    const requestId = crypto.randomUUID();
    const startedAt = process.hrtime.bigint();
    response.set('X-Request-Id', requestId);
    response.set('X-Telegram-Bloom-Api-Version', String(API_VERSION));
    console.info(`[http] --> ${request.method} ${request.path} requestId=${requestId}`);
    response.once('finish', () => {
      const durationMs = Number(process.hrtime.bigint() - startedAt) / 1_000_000;
      console.info(
        `[http] <-- ${response.statusCode} ${request.method} ${request.path} ` +
        `durationMs=${durationMs.toFixed(1)} requestId=${requestId}`
      );
    });
    next();
  });
  app.use(helmet({
    crossOriginOpenerPolicy: { policy: 'same-origin-allow-popups' },
    crossOriginResourcePolicy: { policy: 'same-site' }
  }));
  app.use(express.json({ limit: '16kb', type: 'application/json' }));

  const authLimiter = rateLimit({
    windowMs: 60_000,
    limit: config.authRateLimitPerMinute,
    standardHeaders: 'draft-8',
    legacyHeaders: false
  });

  app.get('/api/health/live', (_request, response) => response.json({ status: 'ok' }));

  app.get('/api/health/ready', asyncRoute(async (_request, response) => {
    await database.ping();
    response.json({
      status: 'ready',
      database: 'connected',
      telegram: config.telegramConfigured ? 'configured' : 'configuration_required',
      apiVersion: API_VERSION,
      revision: APP_REVISION
    });
  }));

  app.post('/auth/telegram', authLimiter, asyncRoute(async (request, response) => {
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

  app.get('/auth/session', asyncRoute(async (request, response) => {
    const session = await authenticatedSession(database, request);
    if (!session) {
      return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    }
    if (rejectDisabledAccount(session, response)) return;
    response.set('Cache-Control', 'no-store').json(
      authenticationStateResponse(session, session.expiresAt)
    );
  }));

  app.put('/me/profile', asyncRoute(async (request, response) => {
    const session = await authenticatedSession(database, request);
    if (!session) {
      return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    }
    if (rejectDisabledAccount(session, response)) return;
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

  app.delete('/me/profile', asyncRoute(async (request, response) => {
    const session = await authenticatedSession(database, request);
    if (!session) {
      return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    }
    if (rejectDisabledAccount(session, response)) return;
    const state = await database.deleteProfile(session.account.id);
    response.set('Cache-Control', 'no-store').json(
      authenticationStateResponse(state, session.expiresAt)
    );
  }));

  app.delete('/auth/session', asyncRoute(async (request, response) => {
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
