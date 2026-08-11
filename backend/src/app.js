import path from 'node:path';
import { fileURLToPath } from 'node:url';
import express from 'express';
import helmet from 'helmet';
import { rateLimit } from 'express-rate-limit';
import { createSession, bearerToken, hashSessionToken } from './sessions.js';
import { publicUserFromVerifiedProfile } from './telegram.js';

const publicDirectory = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'public');
const asyncRoute = (handler) => (request, response, next) =>
  Promise.resolve(handler(request, response, next)).catch(next);

const storedUser = (row) => ({
  id: row.id,
  ...(row.name && { name: row.name }),
  ...(row.given_name && { givenName: row.given_name }),
  ...(row.family_name && { familyName: row.family_name }),
  ...(row.username && { username: row.username }),
  ...(row.phone_number && { phoneNumber: row.phone_number }),
  ...(row.phone_number && { phoneVerified: row.phone_verified }),
  ...(row.picture_url && { picture: row.picture_url })
});

export const createApp = ({ config, database, verifyTelegramToken }) => {
  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', config.trustProxy);
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

  app.get('/api/health/live', (_request, response) => {
    response.json({ status: 'ok' });
  });

  app.get('/api/health/ready', asyncRoute(async (_request, response) => {
    await database.ping();
    response.json({
      status: 'ready',
      database: 'connected',
      telegram: config.telegramConfigured ? 'configured' : 'configuration_required'
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

    let profile;
    try {
      profile = await verifyTelegramToken(idToken);
    } catch (error) {
      console.warn('Rejected Telegram ID token:', error.code ?? error.message);
      return response.status(401).json({
        code: 'INVALID_TELEGRAM_TOKEN',
        message: 'Telegram authorization was rejected'
      });
    }

    const userId = await database.upsertTelegramUser(profile);
    const session = await createSession(database, userId, config.sessionTtlDays);
    response
      .set('Cache-Control', 'no-store')
      .status(200)
      .json({
        user: publicUserFromVerifiedProfile(userId, profile),
        sessionToken: session.token,
        expiresAt: session.expiresAt.toISOString()
      });
  }));

  app.get('/auth/session', asyncRoute(async (request, response) => {
    const token = bearerToken(request);
    const session = token ? await database.findSession(hashSessionToken(token)) : null;
    if (!session) {
      return response.status(401).json({ code: 'SESSION_INVALID', message: 'Session is missing or expired' });
    }
    response.set('Cache-Control', 'no-store').json({
      user: storedUser(session),
      expiresAt: session.expires_at.toISOString()
    });
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
