## 2025-02-12 - Prevent Leaking Internal Server Errors in Passkey Endpoints
**Vulnerability:** The passkey verification endpoints returned `error.message` directly in 401 HTTP responses. While known errors (like `CHALLENGE_EXPIRED`) have safe strings, unexpected system errors (like SQLite failures) lacked a specific code and exposed sensitive internal error messages to the client.
**Learning:** Returning `error.message` in a catch-all block without validating the error type creates an information leakage vulnerability, allowing database internals or memory errors to bypass the global, secure `500 INTERNAL_ERROR` handler.
**Prevention:** Mask unexpected errors locally by sending a generic message (e.g., 'An unexpected error occurred') while keeping the safe HTTP status code, ensuring no internal server context is exposed.

## 2026-10-26 - Restrict Error Code Leakage in Passkey Handlers
**Vulnerability:** Passkey verification, reauthentication, and registration endpoints (`/auth/passkeys/verify`, `/me/reauth/passkeys/verify`, `/me/passkeys/registration/verify`) unconditionally returned `error.message` when `error.code` was truthy. If an internal database or system error had a `code` property not mapped in `statusMap`, it would leak internal error context via the client response, defaulting to a 401.
**Learning:** Even if `error.code` is checked, unvalidated error objects thrown by third-party libraries or standard JS errors with unexpected `code` fields can still bypass intended safety fallbacks and leak internal state.
**Prevention:** Always validate `error.code` against a strict allowlist (e.g., `Object.hasOwn(statusMap, code)`) before echoing its associated message to the client. Unlisted error codes must trigger a secure, generic response string and fallback code.
