import crypto from 'node:crypto';
import {
  generateAuthenticationOptions,
  generateRegistrationOptions,
  verifyAuthenticationResponse,
  verifyRegistrationResponse
} from '@simplewebauthn/server';

const OPERATION_TTL_MS = 5 * 60 * 1000;
const MAX_PASSKEYS_PER_USER = 10;

const operation = (database, config, purpose, challenge, userId = null, sessionTokenHash = null) => {
  const createdAt = new Date();
  const expiresAt = new Date(createdAt.getTime() + OPERATION_TTL_MS);
  const id = crypto.randomUUID();
  database.createWebAuthnOperation({
    id, purpose, challenge, userId, sessionTokenHash,
    rpId: config.passkeyRpId, createdAt, expiresAt
  });
  return { operationId: id, expiresAt };
};

export const createPasskeyService = ({ config, database }) => ({
  async registrationOptions(userId, sessionTokenHash) {
    const account = database.getAccount(userId)?.account;
    if (!account) return null;
    const existing = database.listPasskeys(userId);
    if (existing.length >= MAX_PASSKEYS_PER_USER) {
      const error = new Error('Passkey limit reached');
      error.code = 'PASSKEY_LIMIT_REACHED';
      throw error;
    }
    const publicKey = await generateRegistrationOptions({
      rpName: config.passkeyRpName,
      rpID: config.passkeyRpId,
      userID: Buffer.from(account.webauthn_user_handle, 'base64url'),
      userName: `member-${account.member_number}`,
      userDisplayName: account.name || `Member ${account.member_number}`,
      attestationType: 'none',
      excludeCredentials: existing.map((credential) => ({
        id: credential.credential_id,
        transports: credential.transports
      })),
      authenticatorSelection: {
        residentKey: 'required',
        userVerification: 'required'
      }
    });
    return {
      ...operation(database, config, 'REGISTRATION', publicKey.challenge, userId, sessionTokenHash),
      publicKey
    };
  },

  async verifyRegistration({ operationId, credential, displayName, sessionTokenHash }) {
    const pending = database.consumeWebAuthnOperation(operationId, 'REGISTRATION');
    if (!pending) {
      const err = new Error('Challenge expired or already used');
      err.code = 'CHALLENGE_EXPIRED';
      throw err;
    }
    if (pending.session_token_hash !== sessionTokenHash) {
      const err = new Error('Session is missing or expired');
      err.code = 'SESSION_INVALID';
      throw err;
    }
    const existing = database.findPasskeyByCredentialId(credential?.id);
    if (existing) {
      const err = new Error('Passkey is already registered');
      err.code = 'CREDENTIAL_ALREADY_REGISTERED';
      throw err;
    }
    let verification;
    try {
      verification = await verifyRegistrationResponse({
        response: credential,
        expectedChallenge: pending.challenge,
        expectedOrigin: config.passkeyAllowedOrigins,
        expectedRPID: pending.rp_id,
        requireUserVerification: true
      });
    } catch (error) {
      console.warn('Rejected passkey registration:', error.message);
      if (error.name === 'UnexpectedRPIDHash') {
        const err = new Error('RP ID rejected');
        err.code = 'RP_ID_REJECTED';
        throw err;
      }
      if (error.message?.includes('response origin')) {
        const err = new Error('Origin rejected');
        err.code = 'ORIGIN_REJECTED';
        throw err;
      }
      if (error.message?.includes('response challenge')) {
        const err = new Error('Challenge mismatch or expired');
        err.code = 'CHALLENGE_EXPIRED';
        throw err;
      }
      const err = new Error('Passkey registration was rejected');
      err.code = 'INVALID_PASSKEY';
      throw err;
    }
    if (!verification.verified || !verification.registrationInfo) {
      const err = new Error('Passkey registration was rejected');
      err.code = 'INVALID_PASSKEY';
      throw err;
    }
    const info = verification.registrationInfo;
    try {
      database.createPasskey(pending.user_id, {
        id: crypto.randomUUID(),
        credentialId: info.credential.id,
        publicKey: Buffer.from(info.credential.publicKey),
        counter: info.credential.counter,
        transports: info.credential.transports,
        aaguid: info.aaguid,
        deviceType: info.credentialDeviceType,
        backedUp: info.credentialBackedUp,
        displayName
      });
    } catch (err) {
      if (err.message?.includes('UNIQUE constraint failed')) {
        const duplicateErr = new Error('Passkey is already registered');
        duplicateErr.code = 'CREDENTIAL_ALREADY_REGISTERED';
        throw duplicateErr;
      }
      throw err;
    }
    return database.listPasskeys(pending.user_id);
  },

  async authenticationOptions() {
    const publicKey = await generateAuthenticationOptions({
      rpID: config.passkeyRpId,
      userVerification: 'required',
      allowCredentials: []
    });
    return {
      ...operation(database, config, 'AUTHENTICATION', publicKey.challenge),
      publicKey
    };
  },

  async reauthenticationOptions(userId, sessionTokenHash) {
    const existing = database.listPasskeys(userId);
    if (existing.length === 0) return null;
    const publicKey = await generateAuthenticationOptions({
      rpID: config.passkeyRpId,
      userVerification: 'required',
      allowCredentials: existing.map((credential) => ({
        id: credential.credential_id,
        transports: credential.transports
      }))
    });
    return {
      ...operation(
        database, config, 'REAUTHENTICATION', publicKey.challenge, userId, sessionTokenHash
      ),
      publicKey
    };
  },

  async verifyAuthentication({ operationId, credential }) {
    const pending = database.consumeWebAuthnOperation(operationId, 'AUTHENTICATION');
    if (!pending) {
      const err = new Error('Challenge expired or already used');
      err.code = 'CHALLENGE_EXPIRED';
      throw err;
    }
    const passkey = database.findPasskeyByCredentialId(credential?.id);
    if (!passkey) {
      const err = new Error('Passkey credential not found');
      err.code = 'CREDENTIAL_NOT_FOUND';
      throw err;
    }
    if (passkey.onboarding_state === 'DISABLED') {
      const err = new Error('Account is disabled');
      err.code = 'ACCOUNT_DISABLED';
      throw err;
    }
    let verification;
    try {
      verification = await verifyAuthenticationResponse({
        response: credential,
        expectedChallenge: pending.challenge,
        expectedOrigin: config.passkeyAllowedOrigins,
        expectedRPID: pending.rp_id,
        credential: {
          id: passkey.credential_id,
          publicKey: new Uint8Array(passkey.public_key),
          counter: passkey.counter,
          transports: passkey.transports
        },
        requireUserVerification: true
      });
    } catch (error) {
      console.warn('Rejected passkey assertion:', error.message);
      if (error.name === 'UnexpectedRPIDHash') {
        const err = new Error('RP ID rejected');
        err.code = 'RP_ID_REJECTED';
        throw err;
      }
      if (error.message?.includes('response origin')) {
        const err = new Error('Origin rejected');
        err.code = 'ORIGIN_REJECTED';
        throw err;
      }
      if (error.message?.includes('response challenge')) {
        const err = new Error('Challenge mismatch or expired');
        err.code = 'CHALLENGE_EXPIRED';
        throw err;
      }
      const err = new Error('Passkey authentication was rejected');
      err.code = 'INVALID_PASSKEY';
      throw err;
    }
    if (!verification.verified) {
      const err = new Error('Passkey authentication was rejected');
      err.code = 'INVALID_PASSKEY';
      throw err;
    }
    const expectedUserHandle = passkey.webauthn_user_handle;
    if (credential.response?.userHandle !== expectedUserHandle) {
      const err = new Error('User handle mismatch');
      err.code = 'INVALID_PASSKEY';
      throw err;
    }
    if (!database.updatePasskeyUsage(
      passkey.credential_id, passkey.counter, verification.authenticationInfo.newCounter
    )) {
      const err = new Error('Counter update failed');
      err.code = 'INVALID_PASSKEY';
      throw err;
    }
    return { userId: passkey.user_id, credentialId: passkey.credential_id, passkeyId: passkey.id };
  },

  async verifyReauthentication({ operationId, credential, sessionTokenHash }) {
    const pending = database.consumeWebAuthnOperation(operationId, 'REAUTHENTICATION');
    if (!pending) {
      const err = new Error('Challenge expired or already used');
      err.code = 'CHALLENGE_EXPIRED';
      throw err;
    }
    if (pending.session_token_hash !== sessionTokenHash) {
      const err = new Error('Session is missing or expired');
      err.code = 'SESSION_INVALID';
      throw err;
    }
    const passkey = database.findPasskeyByCredentialId(credential?.id);
    if (!passkey || passkey.user_id !== pending.user_id) {
      const err = new Error('Passkey credential not found');
      err.code = 'CREDENTIAL_NOT_FOUND';
      throw err;
    }
    if (passkey.onboarding_state === 'DISABLED') {
      const err = new Error('Account is disabled');
      err.code = 'ACCOUNT_DISABLED';
      throw err;
    }
    if (credential.response?.userHandle &&
        credential.response.userHandle !== passkey.webauthn_user_handle) {
      const err = new Error('User handle mismatch');
      err.code = 'INVALID_PASSKEY';
      throw err;
    }
    let verification;
    try {
      verification = await verifyAuthenticationResponse({
        response: credential,
        expectedChallenge: pending.challenge,
        expectedOrigin: config.passkeyAllowedOrigins,
        expectedRPID: pending.rp_id,
        credential: {
          id: passkey.credential_id,
          publicKey: new Uint8Array(passkey.public_key),
          counter: passkey.counter,
          transports: passkey.transports
        },
        requireUserVerification: true
      });
    } catch (error) {
      console.warn('Rejected passkey reauthentication:', error.message);
      if (error.name === 'UnexpectedRPIDHash') {
        const err = new Error('RP ID rejected');
        err.code = 'RP_ID_REJECTED';
        throw err;
      }
      if (error.message?.includes('response origin')) {
        const err = new Error('Origin rejected');
        err.code = 'ORIGIN_REJECTED';
        throw err;
      }
      if (error.message?.includes('response challenge')) {
        const err = new Error('Challenge mismatch or expired');
        err.code = 'CHALLENGE_EXPIRED';
        throw err;
      }
      const err = new Error('Passkey reauthentication was rejected');
      err.code = 'INVALID_PASSKEY';
      throw err;
    }
    if (!verification.verified) {
      const err = new Error('Passkey reauthentication was rejected');
      err.code = 'INVALID_PASSKEY';
      throw err;
    }
    if (!database.updatePasskeyUsage(
      passkey.credential_id, passkey.counter, verification.authenticationInfo.newCounter
    )) {
      const err = new Error('Counter update failed');
      err.code = 'INVALID_PASSKEY';
      throw err;
    }
    return { userId: passkey.user_id };
  }
});

export const passkeyResponse = (credential) => ({
  id: credential.id,
  credentialId: credential.credential_id,
  name: credential.display_name,
  createdAt: credential.created_at.toISOString(),
  lastUsedAt: credential.last_used_at?.toISOString() ?? null,
  aaguid: credential.aaguid,
  deviceType: credential.device_type,
  backedUp: credential.backed_up
});
