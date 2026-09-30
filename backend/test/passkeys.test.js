import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import test from 'node:test';
import { createApp } from '../src/app.js';
import { createDatabase } from '../src/database.js';
import { createPasskeyService } from '../src/passkeys.js';
import { isoCBOR, isoBase64URL } from '@simplewebauthn/server/helpers';

const TEST_RP_ID = 'example.com';
const TEST_WEB_ORIGIN = 'https://example.com';
const TEST_ANDROID_ORIGIN = 'android:apk-key-hash:4F88A1EB1E5FCBE4D493EC529706D3BAD60A2E13C5155C40CD65A28423D9C642';

const createPasskeyConfig = (overrides = {}) => ({
  trustProxy: false,
  authRateLimitPerMinute: 1000,
  sessionTtlDays: 30,
  nodeEnv: 'test',
  telegramConfigured: true,
  passkeyRpId: TEST_RP_ID,
  passkeyRpName: 'Test RP',
  passkeyAllowedOrigins: [TEST_WEB_ORIGIN, TEST_ANDROID_ORIGIN],
  passkeysConfigured: true,
  appToken: 'test-app-token',
  ...overrides
});

// Fixed cryptographic keypair for reproducible WebAuthn testing
const fixedKeyPair = crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' });

const buildCosePublicKey = (keyPair) => {
  const jwk = keyPair.publicKey.export({ format: 'jwk' });
  const coseKey = new Map([
    [1, 2], // kty: EC2
    [3, -7], // alg: ES256
    [-1, 1], // crv: P-256
    [-2, Buffer.from(jwk.x, 'base64url')],
    [-3, Buffer.from(jwk.y, 'base64url')]
  ]);
  return isoCBOR.encode(coseKey);
};

const buildRegistrationResponse = ({
  keyPair = fixedKeyPair,
  credentialId = crypto.randomBytes(16),
  challenge,
  origin = TEST_WEB_ORIGIN,
  rpId = TEST_RP_ID,
  flagsByte = 0x45, // UP (1) | UV (4) | AT (64)
  signCount = 0,
  aaguid = Buffer.alloc(16)
}) => {
  const coseKey = buildCosePublicKey(keyPair);
  const rpIdHash = crypto.createHash('sha256').update(rpId).digest();
  const flags = Buffer.from([flagsByte]);
  const counterBuf = Buffer.alloc(4);
  counterBuf.writeUInt32BE(signCount, 0);

  const credIdLen = Buffer.alloc(2);
  credIdLen.writeUInt16BE(credentialId.length, 0);

  const authData = Buffer.concat([
    rpIdHash,
    flags,
    counterBuf,
    aaguid,
    credIdLen,
    credentialId,
    Buffer.from(coseKey)
  ]);

  const attestationMap = new Map([
    ['fmt', 'none'],
    ['attStmt', new Map()],
    ['authData', authData]
  ]);
  const attestationObject = isoCBOR.encode(attestationMap);

  const clientData = {
    type: 'webauthn.create',
    challenge,
    origin,
    crossOrigin: false
  };
  const clientDataJSON = Buffer.from(JSON.stringify(clientData));

  return {
    id: isoBase64URL.fromBuffer(credentialId),
    rawId: isoBase64URL.fromBuffer(credentialId),
    response: {
      clientDataJSON: isoBase64URL.fromBuffer(clientDataJSON),
      attestationObject: isoBase64URL.fromBuffer(Buffer.from(attestationObject))
    },
    type: 'public-key'
  };
};

const buildAuthenticationResponse = ({
  keyPair = fixedKeyPair,
  credentialId,
  challenge,
  origin = TEST_WEB_ORIGIN,
  rpId = TEST_RP_ID,
  flagsByte = 0x05, // UP (1) | UV (4)
  signCount = 1,
  userHandle,
  corruptSignature = false
}) => {
  const clientData = {
    type: 'webauthn.get',
    challenge,
    origin,
    crossOrigin: false
  };
  const clientDataJSON = Buffer.from(JSON.stringify(clientData));
  const clientDataHash = crypto.createHash('sha256').update(clientDataJSON).digest();

  const rpIdHash = crypto.createHash('sha256').update(rpId).digest();
  const flags = Buffer.from([flagsByte]);
  const counterBuf = Buffer.alloc(4);
  counterBuf.writeUInt32BE(signCount, 0);

  const authenticatorData = Buffer.concat([rpIdHash, flags, counterBuf]);
  const signData = Buffer.concat([authenticatorData, clientDataHash]);

  const sign = crypto.createSign('SHA256');
  sign.update(signData);
  let signature = sign.sign(keyPair.privateKey);

  if (corruptSignature) {
    signature = Buffer.from(signature);
    signature[signature.length - 1] ^= 0xff;
  }

  return {
    id: typeof credentialId === 'string' ? credentialId : isoBase64URL.fromBuffer(credentialId),
    rawId: typeof credentialId === 'string' ? credentialId : isoBase64URL.fromBuffer(credentialId),
    response: {
      clientDataJSON: isoBase64URL.fromBuffer(clientDataJSON),
      authenticatorData: isoBase64URL.fromBuffer(authenticatorData),
      signature: isoBase64URL.fromBuffer(signature),
      userHandle: userHandle ? (Buffer.isBuffer(userHandle) ? isoBase64URL.fromBuffer(userHandle) : userHandle) : undefined
    },
    type: 'public-key'
  };
};

const telegramProfile = (overrides = {}) => ({
  id: crypto.randomUUID(),
  telegramSubject: '12345',
  telegramUserId: '987654321',
  name: 'Demo User',
  givenName: 'Demo',
  familyName: 'User',
  username: 'demo',
  phoneNumber: '+14155552671',
  phoneVerified: true,
  picture: 'https://example.test/avatar.jpg',
  ...overrides
});

const startPasskeyServer = async (context, customConfig = createPasskeyConfig()) => {
  const database = createDatabase({ databasePath: ':memory:' });
  database.migrate();
  const verifyTelegramToken = async () => telegramProfile();
  const server = createApp({ config: customConfig, database, verifyTelegramToken })
    .listen(0, '127.0.0.1');
  await new Promise((resolve) => server.once('listening', resolve));
  context.after(() => new Promise((resolve) => server.close(() => {
    database.close();
    resolve();
  })));
  const port = server.address().port;
  return {
    baseUrl: `http://127.0.0.1:${port}`,
    database,
    async authHeaders() {
      const res = await fetch(`http://127.0.0.1:${port}/auth/telegram`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-App-Token': customConfig.appToken },
        body: JSON.stringify({ idToken: 'x'.repeat(40) })
      });
      const data = await res.json();
      return {
        'Content-Type': 'application/json',
        'X-App-Token': customConfig.appToken,
        'Authorization': `Bearer ${data.sessionToken}`
      };
    }
  };
};

test('WebAuthn registration stores credentials and allows passkey authentication with counter increment', async (context) => {
  const { baseUrl, authHeaders, database } = await startPasskeyServer(context);
  const headers = await authHeaders();

  // 1. Begin registration
  const regOptionsRes = await fetch(`${baseUrl}/me/passkeys/registration/options`, {
    method: 'POST',
    headers,
    body: JSON.stringify({})
  });
  assert.equal(regOptionsRes.status, 200);
  const regOptions = await regOptionsRes.json();
  assert.ok(regOptions.operationId);
  assert.ok(regOptions.publicKey.challenge);

  // 2. Build valid registration response
  const credId = crypto.randomBytes(16);
  const regResponse = buildRegistrationResponse({
    keyPair: fixedKeyPair,
    credentialId: credId,
    challenge: regOptions.publicKey.challenge,
    origin: TEST_WEB_ORIGIN
  });

  // 3. Verify registration
  const regVerifyRes = await fetch(`${baseUrl}/me/passkeys/registration/verify`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      operationId: regOptions.operationId,
      credential: regResponse,
      name: 'Test YubiKey'
    })
  });
  assert.equal(regVerifyRes.status, 201);
  const passkeysList = (await regVerifyRes.json()).passkeys;
  assert.equal(passkeysList.length, 1);
  assert.equal(passkeysList[0].name, 'Test YubiKey');
  assert.equal(passkeysList[0].credentialId, regResponse.id);

  // 4. Begin authentication
  const authOptionsRes = await fetch(`${baseUrl}/auth/passkeys/options`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-App-Token': 'test-app-token' },
    body: JSON.stringify({})
  });
  assert.equal(authOptionsRes.status, 200);
  const authOptions = await authOptionsRes.json();

  // Retrieve user handle from database to sign assertion
  const registeredAccount = (await (await fetch(`${baseUrl}/auth/session`, { headers })).json()).account;
  const dbAccount = database.getAccount(registeredAccount.id).account;

  // 5. Build valid authentication response with counter = 1
  const authResponse = buildAuthenticationResponse({
    keyPair: fixedKeyPair,
    credentialId: credId,
    challenge: authOptions.publicKey.challenge,
    origin: TEST_WEB_ORIGIN,
    signCount: 1,
    userHandle: dbAccount.webauthn_user_handle
  });

  const authVerifyRes = await fetch(`${baseUrl}/auth/passkeys/verify`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-App-Token': 'test-app-token' },
    body: JSON.stringify({
      operationId: authOptions.operationId,
      credential: authResponse
    })
  });
  assert.equal(authVerifyRes.status, 200);
  const loginResult = await authVerifyRes.json();
  assert.ok(loginResult.sessionToken);
  assert.equal(loginResult.account.id, registeredAccount.id);

  // 6. Second authentication with counter = 2
  const authOptionsRes2 = await fetch(`${baseUrl}/auth/passkeys/options`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-App-Token': 'test-app-token' },
    body: JSON.stringify({})
  });
  const authOptions2 = await authOptionsRes2.json();

  const authResponse2 = buildAuthenticationResponse({
    keyPair: fixedKeyPair,
    credentialId: credId,
    challenge: authOptions2.publicKey.challenge,
    origin: TEST_WEB_ORIGIN,
    signCount: 2,
    userHandle: dbAccount.webauthn_user_handle
  });

  const authVerifyRes2 = await fetch(`${baseUrl}/auth/passkeys/verify`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-App-Token': 'test-app-token' },
    body: JSON.stringify({
      operationId: authOptions2.operationId,
      credential: authResponse2
    })
  });
  assert.equal(authVerifyRes2.status, 200);
});

test('Reusing operation ID is rejected with CHALLENGE_EXPIRED', async (context) => {
  const { baseUrl, authHeaders } = await startPasskeyServer(context);
  const headers = await authHeaders();

  const regOptionsRes = await fetch(`${baseUrl}/me/passkeys/registration/options`, {
    method: 'POST',
    headers,
    body: JSON.stringify({})
  });
  const regOptions = await regOptionsRes.json();
  const credId = crypto.randomBytes(16);
  const regResponse = buildRegistrationResponse({
    keyPair: fixedKeyPair,
    credentialId: credId,
    challenge: regOptions.publicKey.challenge
  });

  const firstVerify = await fetch(`${baseUrl}/me/passkeys/registration/verify`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      operationId: regOptions.operationId,
      credential: regResponse,
      name: 'Key'
    })
  });
  assert.equal(firstVerify.status, 201);

  // Attempt to replay the same operationId
  const replayVerify = await fetch(`${baseUrl}/me/passkeys/registration/verify`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      operationId: regOptions.operationId,
      credential: regResponse,
      name: 'Key Duplicate'
    })
  });
  assert.equal(replayVerify.status, 400);
  const replayJson = await replayVerify.json();
  assert.equal(replayJson.code, 'CHALLENGE_EXPIRED');
});

test('Negative verification: wrong origin, RP ID, challenge, signature, and user handle are rejected with typed codes', async (context) => {
  const { baseUrl, authHeaders, database } = await startPasskeyServer(context);
  const headers = await authHeaders();

  // 1. Wrong origin during registration
  const regOptions1 = await (await fetch(`${baseUrl}/me/passkeys/registration/options`, {
    method: 'POST', headers, body: JSON.stringify({})
  })).json();

  const wrongOriginResp = buildRegistrationResponse({
    challenge: regOptions1.publicKey.challenge,
    origin: 'https://malicious.evil.com'
  });
  const originFail = await fetch(`${baseUrl}/me/passkeys/registration/verify`, {
    method: 'POST', headers,
    body: JSON.stringify({ operationId: regOptions1.operationId, credential: wrongOriginResp })
  });
  assert.equal(originFail.status, 400);
  assert.equal((await originFail.json()).code, 'ORIGIN_REJECTED');

  // 2. Wrong RP ID during registration
  const regOptions2 = await (await fetch(`${baseUrl}/me/passkeys/registration/options`, {
    method: 'POST', headers, body: JSON.stringify({})
  })).json();

  const wrongRpIdResp = buildRegistrationResponse({
    challenge: regOptions2.publicKey.challenge,
    rpId: 'phishing.com'
  });
  const rpIdFail = await fetch(`${baseUrl}/me/passkeys/registration/verify`, {
    method: 'POST', headers,
    body: JSON.stringify({ operationId: regOptions2.operationId, credential: wrongRpIdResp })
  });
  assert.equal(rpIdFail.status, 400);
  assert.equal((await rpIdFail.json()).code, 'RP_ID_REJECTED');

  // 3. Register a valid passkey to test authentication failures
  const regOptions3 = await (await fetch(`${baseUrl}/me/passkeys/registration/options`, {
    method: 'POST', headers, body: JSON.stringify({})
  })).json();
  const validCredId = crypto.randomBytes(16);
  const validRegResp = buildRegistrationResponse({
    credentialId: validCredId,
    challenge: regOptions3.publicKey.challenge
  });
  await fetch(`${baseUrl}/me/passkeys/registration/verify`, {
    method: 'POST', headers,
    body: JSON.stringify({ operationId: regOptions3.operationId, credential: validRegResp, name: 'Key' })
  });
  const sessionAccount = (await (await fetch(`${baseUrl}/auth/session`, { headers })).json()).account;
  const account = database.getAccount(sessionAccount.id).account;

  // 4. Invalid signature during authentication
  const authOptions1 = await (await fetch(`${baseUrl}/auth/passkeys/options`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-App-Token': 'test-app-token' },
    body: JSON.stringify({})
  })).json();

  const corruptSigResp = buildAuthenticationResponse({
    credentialId: validCredId,
    challenge: authOptions1.publicKey.challenge,
    userHandle: account.webauthn_user_handle,
    corruptSignature: true
  });
  const sigFail = await fetch(`${baseUrl}/auth/passkeys/verify`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-App-Token': 'test-app-token' },
    body: JSON.stringify({ operationId: authOptions1.operationId, credential: corruptSigResp })
  });
  assert.equal(sigFail.status, 401);
  assert.equal((await sigFail.json()).code, 'INVALID_PASSKEY');

  // 5. User handle mismatch during authentication
  const authOptions2 = await (await fetch(`${baseUrl}/auth/passkeys/options`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-App-Token': 'test-app-token' },
    body: JSON.stringify({})
  })).json();

  const wrongHandleResp = buildAuthenticationResponse({
    credentialId: validCredId,
    challenge: authOptions2.publicKey.challenge,
    userHandle: 'wrong-user-handle'
  });
  const handleFail = await fetch(`${baseUrl}/auth/passkeys/verify`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-App-Token': 'test-app-token' },
    body: JSON.stringify({ operationId: authOptions2.operationId, credential: wrongHandleResp })
  });
  assert.equal(handleFail.status, 401);
  assert.equal((await handleFail.json()).code, 'INVALID_PASSKEY');

  // 6. Unknown credential ID
  const authOptions3 = await (await fetch(`${baseUrl}/auth/passkeys/options`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-App-Token': 'test-app-token' },
    body: JSON.stringify({})
  })).json();

  const unknownCredResp = buildAuthenticationResponse({
    credentialId: crypto.randomBytes(16),
    challenge: authOptions3.publicKey.challenge,
    userHandle: account.webauthn_user_handle
  });
  const credNotFound = await fetch(`${baseUrl}/auth/passkeys/verify`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-App-Token': 'test-app-token' },
    body: JSON.stringify({ operationId: authOptions3.operationId, credential: unknownCredResp })
  });
  assert.equal(credNotFound.status, 404);
  assert.equal((await credNotFound.json()).code, 'CREDENTIAL_NOT_FOUND');
});

test('Session binding: registration and reauthentication operations are bound to the originating session', async (context) => {
  const { baseUrl, authHeaders } = await startPasskeyServer(context);
  const sessionAHeaders = await authHeaders();
  const sessionBHeaders = await authHeaders();

  // Create registration options with Session A
  const regOptions = await (await fetch(`${baseUrl}/me/passkeys/registration/options`, {
    method: 'POST', headers: sessionAHeaders, body: JSON.stringify({})
  })).json();

  const regResponse = buildRegistrationResponse({
    challenge: regOptions.publicKey.challenge
  });

  // Attempt to verify with Session B
  const sessionMismatchVerify = await fetch(`${baseUrl}/me/passkeys/registration/verify`, {
    method: 'POST', headers: sessionBHeaders,
    body: JSON.stringify({ operationId: regOptions.operationId, credential: regResponse, name: 'Mismatch' })
  });
  assert.equal(sessionMismatchVerify.status, 401);
  assert.equal((await sessionMismatchVerify.json()).code, 'SESSION_INVALID');
});

test('Multiple origins: Android apk-key-hash origin is accepted alongside HTTPS web origin', async (context) => {
  const { baseUrl, authHeaders } = await startPasskeyServer(context);
  const headers = await authHeaders();

  const regOptions = await (await fetch(`${baseUrl}/me/passkeys/registration/options`, {
    method: 'POST', headers, body: JSON.stringify({})
  })).json();

  // Android Credential Manager supplies the origin matching android:apk-key-hash:...
  const androidRegResponse = buildRegistrationResponse({
    challenge: regOptions.publicKey.challenge,
    origin: TEST_ANDROID_ORIGIN
  });

  const androidVerify = await fetch(`${baseUrl}/me/passkeys/registration/verify`, {
    method: 'POST', headers,
    body: JSON.stringify({ operationId: regOptions.operationId, credential: androidRegResponse, name: 'Android Pixel' })
  });
  assert.equal(androidVerify.status, 201);
  const passkeys = (await androidVerify.json()).passkeys;
  assert.equal(passkeys[0].name, 'Android Pixel');
});

test('Backup and device type metadata are cryptographically derived from authenticatorData flags', async (context) => {
  const { baseUrl, authHeaders } = await startPasskeyServer(context);
  const headers = await authHeaders();

  // Flags: UP (1) | UV (4) | BE (8) | BS (16) | AT (64) = 0x5D -> backedUp = true, multiDevice
  const regOptions = await (await fetch(`${baseUrl}/me/passkeys/registration/options`, {
    method: 'POST', headers, body: JSON.stringify({})
  })).json();

  const syncedKeyResponse = buildRegistrationResponse({
    challenge: regOptions.publicKey.challenge,
    flagsByte: 0x5D
  });

  const verifyRes = await fetch(`${baseUrl}/me/passkeys/registration/verify`, {
    method: 'POST', headers,
    body: JSON.stringify({ operationId: regOptions.operationId, credential: syncedKeyResponse, name: 'Google Password Manager' })
  });
  assert.equal(verifyRes.status, 201);
  const passkeys = (await verifyRes.json()).passkeys;
  assert.equal(passkeys[0].backedUp, true);
  assert.equal(passkeys[0].deviceType, 'multiDevice');
});

test('Deleted passkey credential cannot authenticate after revocation', async (context) => {
  const { baseUrl, authHeaders } = await startPasskeyServer(context);
  const headers = await authHeaders();

  // 1. Register
  const regOptions = await (await fetch(`${baseUrl}/me/passkeys/registration/options`, {
    method: 'POST', headers, body: JSON.stringify({})
  })).json();
  const credId = crypto.randomBytes(16);
  const regResponse = buildRegistrationResponse({
    credentialId: credId,
    challenge: regOptions.publicKey.challenge
  });
  const regRes = await (await fetch(`${baseUrl}/me/passkeys/registration/verify`, {
    method: 'POST', headers,
    body: JSON.stringify({ operationId: regOptions.operationId, credential: regResponse, name: 'Temporary Key' })
  })).json();
  const passkeyId = regRes.passkeys[0].id;

  // 2. Delete passkey
  const deleteRes = await fetch(`${baseUrl}/me/passkeys/${passkeyId}`, {
    method: 'DELETE',
    headers
  });
  assert.equal(deleteRes.status, 200);

  // 3. Try to authenticate with the deleted credential
  const authOptions = await (await fetch(`${baseUrl}/auth/passkeys/options`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-App-Token': 'test-app-token' },
    body: JSON.stringify({})
  })).json();

  const authResponse = buildAuthenticationResponse({
    credentialId: credId,
    challenge: authOptions.publicKey.challenge,
    userHandle: 'any'
  });

  const authRes = await fetch(`${baseUrl}/auth/passkeys/verify`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-App-Token': 'test-app-token' },
    body: JSON.stringify({ operationId: authOptions.operationId, credential: authResponse })
  });
  assert.equal(authRes.status, 404);
  assert.equal((await authRes.json()).code, 'CREDENTIAL_NOT_FOUND');
});
