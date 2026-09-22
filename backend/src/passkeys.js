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
    if (!pending || pending.session_token_hash !== sessionTokenHash) return null;
    const verification = await verifyRegistrationResponse({
      response: credential,
      expectedChallenge: pending.challenge,
      expectedOrigin: config.passkeyAllowedOrigins,
      expectedRPID: pending.rp_id,
      requireUserVerification: true
    });
    if (!verification.verified || !verification.registrationInfo) return null;
    const info = verification.registrationInfo;
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
    if (!pending) return null;
    const passkey = database.findPasskeyByCredentialId(credential?.id);
    if (!passkey || passkey.onboarding_state === 'DISABLED') return null;
    const verification = await verifyAuthenticationResponse({
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
    if (!verification.verified) return null;
    const expectedUserHandle = passkey.webauthn_user_handle;
    if (credential.response?.userHandle !== expectedUserHandle) return null;
    if (!database.updatePasskeyUsage(
      passkey.credential_id, passkey.counter, verification.authenticationInfo.newCounter
    )) return null;
    return { userId: passkey.user_id, credentialId: passkey.credential_id };
  },

  async verifyReauthentication({ operationId, credential, sessionTokenHash }) {
    const pending = database.consumeWebAuthnOperation(operationId, 'REAUTHENTICATION');
    if (!pending || pending.session_token_hash !== sessionTokenHash) return null;
    const passkey = database.findPasskeyByCredentialId(credential?.id);
    if (!passkey || passkey.user_id !== pending.user_id || passkey.onboarding_state === 'DISABLED') {
      return null;
    }
    if (credential.response?.userHandle &&
        credential.response.userHandle !== passkey.webauthn_user_handle) return null;
    const verification = await verifyAuthenticationResponse({
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
    if (!verification.verified) return null;
    if (!database.updatePasskeyUsage(
      passkey.credential_id, passkey.counter, verification.authenticationInfo.newCounter
    )) return null;
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
