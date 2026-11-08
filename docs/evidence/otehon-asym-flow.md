# Otehon asymmetric flow slice — measured runs (2026-11-09)

`market/verify-asymmetric` (injected crypto) + `verify-hmac-sig` (demo
suite) + public `signature-payload` (native handoff) + `market-verify`
command. Runners: bb 1.12.218, nbb 1.4.208 (node v26),
basilisp 0.5.1 / python 3.14.7.

## Why injected crypto

Pure `.cljc` ed25519 was measured infeasible (/tmp/asym-probe):
20000 schoolbook 256-bit mults take 1.5 s (bb), 2.1 s (nbb),
256 s (basilisp, ~12.8 ms/mult). One signature needs thousands of
mults plus reduction — minutes per op on basilisp, which must run
every shared case. So otehon defines the verification FLOW and the
canonical payload bytes; the native route supplies the crypto fn.
The HMAC suite from PR #223 stays as the runnable demonstration of
that exact call shape: (payload sig key) -> truthy.

## Shared cases (in repo, all runners)

`bin/run-shared-cases`: 141/141 pass on bb, nbb, basilisp;
must-fail 5/5; bb/nbb outputs byte-identical to basilisp.
7 new: `asym-valid-allow` (pins the exact crypto call args —
payload vector, sig, pubkey), `asym-deny`, `asym-expired` (crypto
never invoked — `:called-with nil`, pins gating order),
`asym-unknown-issuer`, `asym-hmac` (real HMAC path valid),
`asym-hmac-wrong`, `asym-unsignable`. The atom capture proving
call args runs on all three runners.

## Live disk demos (/tmp/asym-demo, not committed)

- `market-sign` mints the pinned sig; `market-verify signed.edn
  --suite-secret s3cr3t` → `{:valid true}`.
- Price tampered 5→50, same sig → refused
  `market/signature-invalid`, exit 1. Wrong secret → same refusal.
- Demo-script bug (broken shell quoting produced unquoted EDN and a
  parse crash) fixed by writing the fixture via heredoc; the crash
  itself confirmed `read-edn-safe`-style strictness is load-bearing
  for hand-written files.

## Contract (native handoff)

- `signature-payload` is THE bytes to sign: `field|field|...`,
  latin-1, fixed field order, nil when unencodable. Native signs and
  verifies exactly these bytes with its own keys.
- `verify-asymmetric` checks shape, expiry, issuer-presence, and
  encodability BEFORE invoking crypto; unknown issuer and
  unencodable payload never reach the key.
- otehon key maps stay opaque strings; private key material never
  enters `.cljc`, manifests, asks, cases, or evidence.
- `--suite-secret` on a command line leaks to `ps`: demo-only, same
  caveat as `--issuer-secret`. The native route uses its keyring.
- What stays open: native supply of ed25519 verify-fn; escrow-record
  signing.
