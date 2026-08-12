# TelegramLoginAndroidDemo

Minimal Android demo of the official Telegram Login SDK:
<https://github.com/TelegramMessenger/telegram-login-android>

The repository contains an Android application and a small Node.js backend for
verifying Telegram ID tokens and managing application sessions. The demo is
presented as **Telegram Bloom**: Telegram authenticates the person while the
application owns a separate editable signal profile.

## Telegram Bloom flow

- First Telegram sign-in creates one internal account keyed only by the stable
  Telegram `sub` claim and routes to a one-screen profile setup.
- The service stores display name, current intent, headline, up to three topics,
  avatar choice, membership number, and a stable generated Bloom visual.
- Returning sign-ins refresh only Telegram identity metadata. They never
  overwrite the application-owned profile.
- The encrypted Android cache restores completed profiles or interrupted drafts
  before background session validation and keeps completed profiles readable
  while offline.
- Sign-out revokes the server session and clears the local encrypted cache while
  preserving the account and profile in SQLite.

The complete phone number is private identity metadata. API and profile UI only
expose whether Telegram verified it.

## Setup

1. Register the Android app in BotFather using package name
   `com.markettwits.devx.tgsignin`. Add the SHA-256 certificates used to sign
   debug and release builds; BotFather generates a separate App Link host for
   each registered native app.
2. Copy `local.properties.example` to the Git-ignored `local.properties` file
   and provide the Telegram settings plus GitHub Packages credentials.
3. Copy `.env.example` to the Git-ignored `.env` file and set the same Telegram
   Client ID for the backend.

The GitHub token needs only the `read:packages` permission.

Configure the generated hosts without a scheme or path:

```properties
telegram.redirectHost.debug=app000000001-login.tg.dev
telegram.redirectHost.release=app000000002-login.tg.dev
```

Gradle selects the matching host for each build type and uses it consistently
in both the SDK redirect URI and the Android App Links intent filter. CI can use
`TELEGRAM_REDIRECT_HOST_DEBUG` and `TELEGRAM_REDIRECT_HOST_RELEASE` instead.
The legacy `telegram.redirectHost` / `TELEGRAM_REDIRECT_HOST` setting remains a
fallback for existing installations, but it assigns the same host to both
build types.

## Run

Start the backend:

```bash
docker compose up --build -d
```

Alternatively, run it directly with Node.js 22.13 or newer:

```bash
./scripts/run-backend-local.sh
```

The local script keeps SQLite at `backend/data/telegram-signin.sqlite`, installs
dependencies only when required, validates the Node.js version, waits for the
readiness endpoint, and configures `adb reverse` for a connected Android device.
Re-running it while a compatible backend is already listening on the configured
port is safe.

Then sync the project in Android Studio and run the `app` configuration.

With the local script, Android reaches the backend at `http://127.0.0.1:8080`
through ADB port forwarding. If several devices are connected, set
`ANDROID_SERIAL` before running the script.

## API

All authenticated endpoints use `Authorization: Bearer <sessionToken>`.

- `POST /auth/telegram` with `{ "idToken": "..." }` verifies Telegram and
  returns `sessionToken`, `expiresAt`, `account`, `telegram`, and `profile`.
- `GET /auth/session` restores the account, onboarding state, Telegram summary,
  and optional service profile.
- `PUT /me/profile` creates or idempotently updates the service profile. Valid
  intents are `BUILDING`, `HELPING`, and `EXPLORING`; one to three supported
  topics are required; headline length is 1–120 characters.
- `DELETE /auth/session` revokes the current application session.
- `GET /api/health/live` and `GET /api/health/ready` provide container health.

## Verification

```bash
cd backend && npm test
cd .. && ./gradlew testDebugUnitTest assembleDebug
docker compose config
```
