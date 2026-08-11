import assert from 'node:assert/strict';
import test from 'node:test';
import { bearerToken, hashSessionToken } from '../src/sessions.js';

test('session token is stored as a deterministic SHA-256 hash', () => {
  const token = 'a'.repeat(43);
  const hash = hashSessionToken(token);
  assert.equal(hash.length, 64);
  assert.notEqual(hash, token);
  assert.equal(hash, hashSessionToken(token));
});

test('bearer token accepts only bounded Bearer credentials', () => {
  const token = 'b'.repeat(43);
  assert.equal(bearerToken({ get: () => `Bearer ${token}` }), token);
  assert.equal(bearerToken({ get: () => 'Basic anything' }), null);
  assert.equal(bearerToken({ get: () => 'Bearer short' }), null);
});
