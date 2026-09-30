import assert from 'node:assert/strict';
import test from 'node:test';
import { createLogger, createMetricsCollector, hashIdentifier } from '../src/logger.js';

test('hashIdentifier generates deterministic truncated sha256 hex', () => {
  const hash1 = hashIdentifier('credential-abc-123');
  const hash2 = hashIdentifier('credential-abc-123');
  assert.equal(hash1, hash2);
  assert.equal(hash1?.length, 16);
  assert.equal(hashIdentifier(''), undefined);
  assert.equal(hashIdentifier(null), undefined);
});

test('createMetricsCollector aggregates requests, durations, passkey and storage events', () => {
  const collector = createMetricsCollector();

  collector.recordRequest('GET', '/api/health/live', 200, 1.5);
  collector.recordRequest('GET', '/api/health/live', 200, 2.5);
  collector.recordRequest('POST', '/auth/telegram', 401, 10.0);

  collector.recordPasskeyOperation('authentication', 'success');
  collector.recordPasskeyOperation('authentication', 'CHALLENGE_EXPIRED');
  collector.recordTelegramAuth('success');
  collector.recordWebauthnCreated();
  collector.recordWebauthnExpired();
  collector.recordSqliteBusy();

  const snap = collector.snapshot();
  assert.equal(snap.http.totalRequests, 3);
  assert.equal(snap.http.averageDurationMs, 4.67);
  assert.equal(snap.http.byRoute['GET /api/health/live 200'], 2);
  assert.equal(snap.http.byRoute['POST /auth/telegram 401'], 1);
  assert.equal(snap.passkeys.byOutcome['authentication success'], 1);
  assert.equal(snap.passkeys.byOutcome['authentication CHALLENGE_EXPIRED'], 1);
  assert.equal(snap.telegram.byOutcome['success'], 1);
  assert.equal(snap.webauthnOperations.created, 1);
  assert.equal(snap.webauthnOperations.expired, 1);
  assert.equal(snap.storage.sqliteBusyEvents, 1);
});

test('createLogger outputs structured JSON when LOG_FORMAT=json', () => {
  const originalEnv = process.env.LOG_FORMAT;
  process.env.LOG_FORMAT = 'json';

  const stdoutWrites = [];
  const stderrWrites = [];
  const origStdout = process.stdout.write;
  const origStderr = process.stderr.write;

  process.stdout.write = (chunk) => {
    stdoutWrites.push(chunk);
    return true;
  };
  process.stderr.write = (chunk) => {
    stderrWrites.push(chunk);
    return true;
  };

  try {
    const logger = createLogger({ nodeEnv: 'production', revision: 'test-rev-1' });

    logger.httpRequestStart({ requestId: 'req-1', method: 'GET', route: '/api/health' });
    logger.passkeyEvent({
      requestId: 'req-1',
      operation: 'authentication',
      outcome: 'failure',
      reason: 'INVALID_PASSKEY',
      credentialId: 'raw-cred-id-secret'
    });
    logger.unhandledError({
      requestId: 'req-1',
      error: new Error('Database locked'),
      route: '/auth/telegram',
      method: 'POST'
    });

    assert.equal(stdoutWrites.length, 2);
    const startLog = JSON.parse(stdoutWrites[0]);
    assert.equal(startLog.event, 'http_request_start');
    assert.equal(startLog.requestId, 'req-1');
    assert.equal(startLog.revision, 'test-rev-1');

    const passkeyLog = JSON.parse(stdoutWrites[1]);
    assert.equal(passkeyLog.event, 'passkey_operation');
    assert.equal(passkeyLog.outcome, 'failure');
    assert.equal(passkeyLog.reason, 'INVALID_PASSKEY');
    // Raw credential ID must NOT be present
    assert.equal(passkeyLog.credentialId, undefined);
    assert.equal(passkeyLog.credentialIdHash, hashIdentifier('raw-cred-id-secret'));

    assert.equal(stderrWrites.length, 1);
    const errorLog = JSON.parse(stderrWrites[0]);
    assert.equal(errorLog.event, 'unhandled_error');
    assert.equal(errorLog.errorMessage, 'Database locked');
  } finally {
    process.stdout.write = origStdout;
    process.stderr.write = origStderr;
    process.env.LOG_FORMAT = originalEnv;
  }
});
