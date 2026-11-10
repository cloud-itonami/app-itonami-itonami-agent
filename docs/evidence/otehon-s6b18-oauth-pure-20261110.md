# Verification record — S6b-18 oauth pure units — 2026-11-10

JUN-1036 S6b-18: the pure heads of the itonami MCP OAuth login,
extending the S6b-2 `store-name` sliver of `oauth.cljc`. `verify-
challenge` (RFC 7636 S256) over a pure SHA-256 (FIPS 180-4) plus a
pure URL-safe base64; `pkce-pair` for an explicit verifier;
`auth-params` (scope only when non-blank); `discovery-record`
(well-known wins, MCP-host fallback, device endpoint nil when
unadvertised); `registration-body` + `registration-record`
(RFC 7591); `token-record` (known fields + computed expiry + issuer
binding at an explicit now). `:oauth` verbs wired through
`ops-oauth` in `cli.cljc` (the older `connections` "store-name"
alias stays for its existing cases). 10 shared cases + 3
must-fail controls; 928 cases + 108 must-fail green on basilisp
0.5.1 / bb 1.12.218 / nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/oauth.cljc`: extended — private
  `pow2` / `add32` / `land` / `lxor` / `rotr32` / `shr32` /
  `sha256-bytes` / `utf8-bytes` / `b64url-bytes`, public
  `verify-challenge` / `pkce-pair` / `auth-params` /
  `discovery-record` / `registration-body` /
  `registration-record` / `token-record` (+ pre-existing
  `store-name`).
- `otehon/src/itonami/otehon/cli.cljc`: `:oauth` dispatch +
  `ops-oauth` (the already-required `oauth` alias reused).
- `otehon/COMPAT.md`: S6b-18 entry + oauth table row updated.
- `test/cases.edn` +10, `test/must-fail.edn` +3.

## Portability notes

- SHA-256 keeps the vault SHA-1 exactness discipline (mod-2^32
  adds, masked word ops, split rotates) plus `shr32` for the
  schedule sigmas; the 64-bit length is a 4+4 high/low split.
- Verifier bytes are ASCII-indexed (`utf8-bytes`); RFC 7636
  verifiers are ASCII-only, anything else throws fail-closed
  instead of inheriting per-runner UTF-8 semantics.
- Base64 works three bytes at a time (intermediates below 2^24);
  the final group is right-padded with zero bits.
- `token-record` parses `expires_in` with parseInt-prefix
  semantics, defaulting to 3600 (mirrors the legacy `isNaN`
  default); the module-local two-line parse keeps oauth.cljc
  self-contained (browser.cljc owns the shared parse-digits).
- Three implementation bugs were caught before any gate run, all
  via the node:crypto independent oracle: (1) the length field
  was emitted as 4 high + 8 low bytes instead of 64-bit
  (sha256("abc") mismatch); (2) the schedule σ0/σ1 missed their
  SHR halves (same oracle); (3) base64 placed every group at the
  24-bit high end instead of its own width (the RFC 7636 vector
  matched 40/43 chars — last group only).
- Random verifier generation, fetch, the loopback listener, URL
  serialization, the token endpoint, 0600 file writes, clock reads
  and the login/device flows stay S6.

## Runs

- bb 1.12.218: 928 cases, 0 fail; must-fail 108/108 FAIL (as designed).
- nbb (node v26.3.0): 928 cases, 0 fail; must-fail 108/108 FAIL.
- basilisp 0.5.1: 928 cases, 0 fail; must-fail 108/108 FAIL.
- Canon byte-identical across the three runners.
- Independent oracles: node:crypto sha256("abc"),
  base64url(sha256("abc")), HMAC-adjacent RFC 7636 vector
  E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM — all identical
  to the pure port.
