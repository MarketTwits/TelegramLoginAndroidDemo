import assert from 'node:assert/strict';
import test from 'node:test';
import { normalizeTelegramUserId } from '../src/telegram.js';

test('normalizes Telegram user IDs without losing 52-bit precision', () => {
  assert.equal(normalizeTelegramUserId(987654321), '987654321');
  assert.equal(normalizeTelegramUserId('1099511627775'), '1099511627775');
});

test('rejects malformed or out-of-range Telegram user IDs', () => {
  assert.equal(normalizeTelegramUserId(null), null);
  assert.equal(normalizeTelegramUserId('0'), null);
  assert.equal(normalizeTelegramUserId('-1'), null);
  assert.equal(normalizeTelegramUserId('1.5'), null);
  assert.equal(normalizeTelegramUserId('1099511627776'), null);
});
