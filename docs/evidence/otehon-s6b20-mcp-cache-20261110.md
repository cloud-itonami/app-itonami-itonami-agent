# Verification record — S6b-20 mcp oauth-cache decision — 2026-11-10

JUN-1036 S6b-20: the oauth-cache-ready decision core of the MCP
transport, in `mcp.cljc`. `cache-status` decides over already-read
records (token-file reads stay host-side, S6): fresh (a usable
access token past the 60s skew margin) vs issuer-bound
refreshability (refresh token + matching hermes_issuer + https
token endpoint + known auth method + client secret unless method
is none). Corrupt records never read healthy; boundary expiry
counts as expired; `expires_at`/`expires_in` parse with
parseFloat-prefix semantics, defaulting through the mtime+ttl
fallback to 0. The `:mcp` verb `cache-status` wired through
`ops-mcp`. 8 shared cases + 2 must-fail controls; 941 cases + 112
must-fail green on basilisp 0.5.1 / bb 1.12.218 / nbb, canon
byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/mcp.cljc`: + private `finite?` /
  `parse-int-str` / `pow10` / `parse-float`, public `cache-status`.
- `otehon/src/itonami/otehon/cli.cljc`: `:mcp` dispatch grows the
  `cache-status` verb (destructured to 7 args).
- `otehon/COMPAT.md`: S6b-20 entry + mcp table row updated.
- `test/cases.edn` +8, `test/must-fail.edn` +2.

## Portability notes

- `js/Number.isFinite` excludes NaN/±Inf but truthy-checks
  nothing else (parseFloat already returns number-or-NaN):
  `finite?` is number + self-equal + not ±Inf, no host predicate.
- `parseFloat` prefix semantics ("2000x" reads 2000, "Infinity"
  reads ##Inf, garbage reads nil) pinned by cases; hex stays a
  leading-0 read, matching parseFloat.
- bb ratios would diverge from JS doubles (`(/ 1 10)` is 1/10,
  not 0.1): `pow10` and the parse product stay doubles by
  construction — `parse-float` results compare equal on all
  three runners.
- `tokens`/`meta`/`client` may be nil (unreadable files): every
  access is nil-safe and resolves to not-ready, pinned by the
  nil-records behavior (same shape as the corrupt case).
- Token-file reads, stdio/HTTP exchange spawn, discover
  projection/call dispatch stay S6.

## Runs

- bb 1.12.218: 941 cases, 0 fail; must-fail 112/112 FAIL (as designed).
- nbb (node v26.3.0): 941 cases, 0 fail; must-fail 112/112 FAIL.
- basilisp 0.5.1: 941 cases, 0 fail; must-fail 112/112 FAIL.
- Canon byte-identical across the three runners.
