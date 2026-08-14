import assert from 'node:assert/strict';
import test from 'node:test';
import { normalizeInternationalPhoneNumber } from '../src/phoneNumbers.js';

test('normalizes user-supplied international phone numbers to E.164', () => {
  assert.equal(normalizeInternationalPhoneNumber('+1 555 123 4567'), '+15551234567');
  assert.equal(normalizeInternationalPhoneNumber('+1 (415) 555-2671'), '+14155552671');
  assert.equal(normalizeInternationalPhoneNumber('+44 20 7946 0018'), '+442079460018');
});

test('accepts Telegram OIDC country code digits without a leading plus', () => {
  assert.equal(normalizeInternationalPhoneNumber('442079460018', true), '+442079460018');
  assert.equal(normalizeInternationalPhoneNumber('442079460018'), null);
});

test('rejects missing and invalid phone values', () => {
  assert.equal(normalizeInternationalPhoneNumber(null, true), null);
  assert.equal(normalizeInternationalPhoneNumber('+999123', true), null);
});
