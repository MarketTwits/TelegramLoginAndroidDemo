# TelegramLoginAndroidDemo

Minimal Android demo of the official Telegram Login SDK:
<https://github.com/TelegramMessenger/telegram-login-android>

The repository contains an Android application and a small Node.js backend for
verifying Telegram ID tokens and managing application sessions. The demo is
presented as **Telegram Bloom**: Telegram authenticates the person while the
application owns a separate editable signal profile.

## Telegram Bloom flow

- First Telegram sign-in creates one internal account keyed only by the stable
  Telegram `sub` claim and routes to a compact Telegram-like profile setup.
- A four-step pager collects the display name, optional international phone,
  current intent and headline, topics, and a server-managed Bloom badge without
  presenting one long form.
- The Telegram profile photo remains the avatar. The Bloom badge is a separate
  badge beside the display name and can be changed from its compact menu.
- Returning sign-ins refresh only Telegram identity metadata. They never
  overwrite the application-owned profile.
- The encrypted Android cache restores completed profiles or interrupted drafts
  before background session validation and keeps completed profiles readable
  while offline.
- Sign-out revokes the server session and clears the local encrypted cache while
  preserving the account and profile in SQLite.
- Account deletion permanently removes the application-owned account, profile,
  and all active sessions. The Android client clears its encrypted cache and
  returns to Telegram sign-in; a later sign-in creates a new internal account.

When the person grants Telegram phone access, the number is offered as the
initial value of the private Bloom profile field. It can be edited or cleared,
is stored in canonical E.164 form, and is formatted by country for display.
The editor applies the country-specific mask as the user types and accepts
structurally possible international numbers without pretending they are verified.
Telegram may return the international OIDC claim without a leading `+`; the
backend normalizes that provider-specific representation before returning it.
Returning Telegram sign-ins do not overwrite it. Both the Telegram claim and
profile phone are returned only to their authenticated owner; request logs and
public badge endpoints never contain them.

## Setup

1. Register the Android app in BotFather using package name
   `com.markettwits.devx.tgsignin` and the SHA-256 certificate used to sign the
   installed app. Use the generated App Link host below.
2. Copy `local.properties.example` to the Git-ignored `local.properties` file
   and provide the Telegram settings plus GitHub Packages credentials.
3. Copy `.env.example` to the Git-ignored `.env` file and set the same Telegram
   Client ID for the backend.

The GitHub token needs only the `read:packages` permission.

Configure the generated host without a scheme or path:

```properties
telegram.redirectHost=app000000001-login.tg.dev
```

Gradle uses this single host for every build type, both in the SDK redirect URI
and in the Android App Links intent filter. CI uses `TELEGRAM_REDIRECT_HOST`.
An installed build must be signed by the certificate registered for this host;
use the release signing key for any build that must complete real Telegram App
Link verification.

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
- `GET /api/profile-badges` returns the versioned public badge catalog. Profiles
  store a stable `badgeId`; TGS/WebP files use immutable, hash-based asset paths.
- `PUT /me/profile` creates or idempotently updates the service profile. Valid
  intents are `BUILDING`, `HELPING`, and `EXPLORING`; one to three supported
  topics are required; headline length is 1–120 characters; `badgeId` must be
  an enabled entry from the current badge catalog. `phoneNumber` is optional;
  when present it must be a valid international number with a country code and
  is normalized to E.164.
- `DELETE /me/account` permanently removes the authenticated account together
  with its service profile and sessions, and returns `204 No Content`.
- `DELETE /auth/session` revokes the current application session.
- `GET /api/health/live` and `GET /api/health/ready` provide container health.

## Verification

```bash
cd backend && npm test
cd .. && ./gradlew testDebugUnitTest assembleDebug
docker compose config
```

## GitHub Actions

`.github/workflows/ci.yml` runs backend and Android checks automatically for
pull requests and pushes to `main`. It never deploys or publishes a release.

Production deployment and Android publishing are separate manual workflows.
Open **Actions**, select the required workflow, and press **Run workflow** on
`main`:

- **Deploy production backend** tests and deploys the exact selected commit,
  then verifies the public readiness endpoint.
- **Publish Android release** reads `versionName` and `versionCode` from
  `gradle/libs.versions.toml`, accepts only a pre-release flag, builds and
  verifies a signed APK, creates the `<versionName>.<versionCode>` tag, uses the
  concise release title `v<versionName>`, and publishes
  `TelegramLoginAndroidDemo-<versionName>.apk` in GitHub Releases. GitHub
  generates the changelog automatically from merged changes using
  `.github/release.yml`.

Both manual jobs are restricted to `main` in addition to their environment
protection rules. Production deployments are serialized. If a new container
does not become ready, the server rebuilds the previous revision. The readiness
response includes the exact deployed Git commit in its `revision` field.
Versioning follows the Sportsouce model. Before a release, update both entries
in `gradle/libs.versions.toml`: keep `versionName` as SemVer `X.Y.Z` and increase
the positive integer `versionCode`. Gradle is the source of truth; the workflow
reads the generated APK metadata and rejects malformed or duplicate releases.

### Server deployment environment

Create a GitHub environment named `production`, restrict its deployment branch
to `main`, and keep all host identity, network coordinates, filesystem paths,
and health endpoints exclusively in encrypted Environment Secrets:

- `DEPLOY_HOST`, `DEPLOY_USER`, and `DEPLOY_PORT`
- `DEPLOY_APP_DIRECTORY` and `DEPLOY_STACK_DIRECTORY`
- `DEPLOY_LOCAL_HEALTH_URL` and `PRODUCTION_HEALTH_URL`
- `DEPLOY_SSH_PRIVATE_KEY`: the complete private deployment key
- `DEPLOY_KNOWN_HOSTS`: the independently verified SSH host-key line

Add the repository variable `PACKAGES_USERNAME` with the GitHub username that
owns the package token, and the repository secret `PACKAGES_READ_TOKEN` with a
classic GitHub PAT limited to `read:packages`. The Android CI job uses these only
to resolve the official Telegram Login SDK from GitHub Packages.

One-time server preparation uses values known only to the operator:

```bash
sudo usermod -aG docker DEPLOY_USER
sudo install -d -o DEPLOY_USER -g DEPLOY_USER -m 700 /home/DEPLOY_USER/.ssh
sudo -u DEPLOY_USER touch /home/DEPLOY_USER/.ssh/authorized_keys
sudo chmod 600 /home/DEPLOY_USER/.ssh/authorized_keys
```

Append the public half of the dedicated deployment key to
the deployment user's `authorized_keys`, then start a new SSH session so Docker
group membership is applied. Confirm that deployment can run without a password:

```bash
git -C "$DEPLOY_APP_DIRECTORY" fetch origin main
docker compose -f "$DEPLOY_STACK_DIRECTORY/compose.yml" config --quiet
test -f "$DEPLOY_STACK_DIRECTORY/.env"
curl --fail "$DEPLOY_LOCAL_HEALTH_URL"
```

The production `TELEGRAM_CLIENT_ID` remains only in the server-side stack
`.env`; it is not copied into GitHub.
Before storing `DEPLOY_KNOWN_HOSTS`, compare `ssh-keyscan` output with the
fingerprint printed directly on the server by
`sudo ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`.

### Android release environment

Local release builds use the ignored root files `keystore.jks` and
`keystore.properties`. The properties file has the standard keys
`storeFile`, `storePassword`, `keyAlias`, and `keyPassword`; `storeFile` is
resolved relative to the repository root. If signing environment variables are
present, they override the local file so CI never needs either ignored file.

Create a protected GitHub environment named `android-release`, restrict it to
`main`, and add these Environment Secrets:

- `ANDROID_KEYSTORE_BASE64`: the complete `keystore.jks` encoded as Base64
- `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`:
  values matching `keystore.properties`
- `ANDROID_SIGNING_SHA256`: expected release certificate SHA-256 fingerprint;
  separators and letter case do not matter
- `TELEGRAM_CLIENT_ID`, `TELEGRAM_REDIRECT_HOST`, `TELEGRAM_BACKEND_URL`:
  production Android build configuration

On macOS, the keystore can be uploaded without creating another local file:

```bash
base64 -i keystore.jks | gh secret set ANDROID_KEYSTORE_BASE64 --env android-release
```

The workflow restores the keystore only inside the temporary runner directory,
checks that the configured alias exists, verifies the completed APK with
`apksigner`, and compares its certificate to `ANDROID_SIGNING_SHA256` before it
can create a GitHub Release. `keystore.jks` and `keystore.properties` remain
ignored and must never be committed.

## Network diagnostics

Android writes sanitized request diagnostics to Logcat with the
`TelegramBloomHttp` tag. Logs contain the HTTP method, URL without query
parameters, status, duration, public backend error code, and `X-Request-Id`.
ID tokens, application session tokens, authorization headers, and payloads are
never logged.

```bash
adb logcat -s TelegramBloomHttp
```

The backend logs matching request start/completion records as `[http]`, with
status, duration, and the same request ID. This makes a reported 5xx response
traceable without exposing credentials.
