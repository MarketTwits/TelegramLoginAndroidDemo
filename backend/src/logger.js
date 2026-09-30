import crypto from 'node:crypto';

/**
 * Production Structured JSON Logger and Metrics Collector.
 * Redacts credentials, tokens, and sensitive headers.
 */

export const hashIdentifier = (id) => {
  if (typeof id !== 'string' || !id) return undefined;
  return crypto.createHash('sha256').update(id).digest('hex').slice(0, 16);
};

export const createMetricsCollector = () => {
  const httpRequestsTotal = new Map();
  let httpRequestDurationTotalMs = 0;
  let httpRequestCount = 0;

  const passkeyOperationsTotal = new Map();
  const telegramAuthTotal = new Map();

  let webauthnOpsCreated = 0;
  let webauthnOpsExpired = 0;
  let webauthnOpsReplayed = 0;
  let sqliteBusyEvents = 0;

  const inc = (map, key) => map.set(key, (map.get(key) || 0) + 1);

  return {
    recordRequest(method, route, statusCode, durationMs) {
      httpRequestCount += 1;
      httpRequestDurationTotalMs += durationMs;
      const key = `${method} ${route} ${statusCode}`;
      inc(httpRequestsTotal, key);
    },

    recordPasskeyOperation(operation, outcome) {
      const key = `${operation} ${outcome}`;
      inc(passkeyOperationsTotal, key);
    },

    recordTelegramAuth(outcome) {
      inc(telegramAuthTotal, outcome);
    },

    recordWebauthnCreated() { webauthnOpsCreated += 1; },
    recordWebauthnExpired() { webauthnOpsExpired += 1; },
    recordWebauthnReplayed() { webauthnOpsReplayed += 1; },
    recordSqliteBusy() { sqliteBusyEvents += 1; },

    snapshot() {
      const avgDuration = httpRequestCount > 0
        ? Math.round((httpRequestDurationTotalMs / httpRequestCount) * 100) / 100
        : 0;
      return {
        http: {
          totalRequests: httpRequestCount,
          averageDurationMs: avgDuration,
          byRoute: Object.fromEntries(httpRequestsTotal)
        },
        passkeys: {
          byOutcome: Object.fromEntries(passkeyOperationsTotal)
        },
        telegram: {
          byOutcome: Object.fromEntries(telegramAuthTotal)
        },
        webauthnOperations: {
          created: webauthnOpsCreated,
          expired: webauthnOpsExpired,
          replayed: webauthnOpsReplayed
        },
        storage: {
          sqliteBusyEvents
        }
      };
    }
  };
};

export const createLogger = ({ nodeEnv = 'development', revision = 'development' } = {}) => {
  const isJson = process.env.LOG_FORMAT === 'json' || nodeEnv === 'production';

  const write = (level, payload) => {
    const entry = {
      timestamp: new Date().toISOString(),
      level,
      revision,
      ...payload
    };

    if (isJson) {
      const line = JSON.stringify(entry);
      if (level === 'error') {
        process.stderr.write(line + '\n');
      } else {
        process.stdout.write(line + '\n');
      }
    } else {
      const prefix = `[${entry.event || level}]`;
      const details = Object.entries(payload)
        .filter(([k]) => k !== 'event')
        .map(([k, v]) => `${k}=${typeof v === 'object' ? JSON.stringify(v) : v}`)
        .join(' ');
      const formatted = `${prefix} ${details}`.trim();
      if (level === 'error') {
        console.error(formatted);
      } else if (level === 'warn') {
        console.warn(formatted);
      } else {
        console.info(formatted);
      }
    }
  };

  return {
    httpRequestStart({ requestId, method, route }) {
      write('info', {
        event: 'http_request_start',
        requestId,
        method,
        route
      });
    },

    httpRequestEnd({ requestId, method, route, statusCode, durationMs }) {
      write('info', {
        event: 'http_request',
        requestId,
        method,
        route,
        status: statusCode,
        durationMs: Math.round(durationMs * 10) / 10
      });
    },

    passkeyEvent({ requestId, operation, outcome, reason = null, credentialId = null }) {
      const level = outcome === 'success' ? 'info' : (outcome === 'cancelled' ? 'info' : 'warn');
      write(level, {
        event: 'passkey_operation',
        requestId,
        operation,
        outcome,
        ...(reason ? { reason } : {}),
        ...(credentialId ? { credentialIdHash: hashIdentifier(credentialId) } : {})
      });
    },

    telegramAuthEvent({ requestId, outcome, reason = null }) {
      const level = outcome === 'success' ? 'info' : 'warn';
      write(level, {
        event: 'telegram_auth',
        requestId,
        outcome,
        ...(reason ? { reason } : {})
      });
    },

    unhandledError({ requestId, error, route = null, method = null }) {
      write('error', {
        event: 'unhandled_error',
        requestId,
        route,
        method,
        errorName: error?.name || 'Error',
        errorMessage: error?.message || String(error),
        stack: error?.stack
      });
    }
  };
};
