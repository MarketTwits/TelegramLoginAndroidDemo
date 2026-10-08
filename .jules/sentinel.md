## 2023-10-08 - [Timing Attack] Timing Leak in App Token Comparison
**Vulnerability:** The `tokensMatch` function used an early-return check (`actualBytes.length === expectedBytes.length`) before calling `crypto.timingSafeEqual()`, introducing a timing attack side-channel that leaked the exact length of the valid API token.
**Learning:** `crypto.timingSafeEqual` prevents byte-by-byte timing attacks but requires its inputs to be exactly the same length. Comparing lengths manually prior to using it reveals the secret length.
**Prevention:** Hash user-provided secrets and expected secrets with a strong, fast algorithm (like SHA-256) *before* applying constant-time string comparison so both sides are guaranteed equal length, preventing any length-based leak.
