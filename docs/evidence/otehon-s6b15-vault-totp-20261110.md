# Verification record — S6b-15 vault TOTP minting — 2026-11-10

JUN-1036 S6b-15: the pure TOTP core of the itonami credential vault,
completing `vault.cljc`. `mint-totp` (RFC 6238, SHA-1, 6 digits, 30s
step) over the stored base32 seed at an explicit `now-ms`, with
private pure SHA-1 (FIPS 180-4), HMAC-SHA1 and `base32-decode`
(tolerant: lowercase, whitespace and `=` padding ignored; any other
character throws the legacy "invalid base32 TOTP seed"). `:vault`
verbs `totp` and `base32` wired through `ops-vault` in `cli.cljc`.
4 shared cases (3 RFC 6238 vectors + 1 base32 tolerance) + 2
must-fail controls; 871 cases + 100 must-fail green on basilisp
0.5.1 / bb 1.12.218 / nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/vault.cljc`: + `sha1-bytes`,
  `hmac-sha1`, `base32-decode`, `mint-totp` (private word ops
  `pow2` / `add32` / `land` / `lor` / `lxor` / `rotl32`).
- `otehon/src/itonami/otehon/cli.cljc`: `ops-vault` grows the
  `totp` / `base32` verbs.
- `otehon/COMPAT.md`: S6b-15 entry + vault table row updated.
- `test/cases.edn` +4, `test/must-fail.edn` +2.

## Portability notes

- Host bit ops may sign-extend (nbb coerces through int32 past the
  sign bit; JVM longs and Python ints do not), so every word op is
  masked (`land` / `lor` / `lxor` reinterpret mod 2^32) and `rotl32`
  is product-split (`(lo * 2^n) + hi`, every product below 2^32).
  No `bit-shift-left/right` on words; `pow2` is repeated
  multiplication (powers of two are exact doubles).
- `mint-totp` takes `now-ms` explicitly; the clock default stays S6.
- The first `base32-decode` draft returned the bit count (`second`
  of the accumulator) instead of the byte vector; the bb probe
  (`GEZDGNBVGY3TQOJQ` -> `[49 ... 48]`) caught it before any gate
  run, and the RFC vectors below pin the fix.
- Longer HMAC keys hash down per the standard (the legacy key is
  always 32 bytes, so this path is structural, not case-pinned).

## Runs

- bb 1.12.218: 871 cases, 0 fail; must-fail 100/100 FAIL (as designed).
- nbb (node v26.3.0): 871 cases, 0 fail; must-fail 100/100 FAIL.
- basilisp 0.5.1: 871 cases, 0 fail; must-fail 100/100 FAIL.
- Canon byte-identical across the three runners.
- Independent oracle: node:crypto HMAC-SHA1 on the ASCII secret
  `12345678901234567890` gives 287082 / 081804 / 005924 for the
  same three timestamps — identical to the pure port.
