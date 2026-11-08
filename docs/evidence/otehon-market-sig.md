# Otehon market signature slice — measured runs (2026-11-09)

HMAC-SHA256 attestation signatures (`market/sign-attestation`,
`valid-signature?`, `match-signed`) + `market-sign` command and
`market-match --secrets`. Runners: bb 1.12.218, nbb 1.4.208
(node v26), basilisp 0.5.1 / python 3.14.7.

## Cross-implementation oracle

Pure `.cljc` HMAC agrees with python hashlib on the pinned vector:
payload `n1|4|8|100|5|2026-12-31|k`, secret `s3cr3t` →
`5291e3ab009432768e0df01deb37a538b4690c7e28fb6245ba668468f84a9efb`
on both. The implementation was written first, hashlib checked it,
and the shared cases pin the value on all three runners.

## Shared cases (in repo, all runners)

`bin/run-shared-cases`: 113/113 pass on bb, nbb, basilisp;
must-fail 5/5; bb/nbb outputs byte-identical to basilisp.
7 new cases: `market-sign-basic` (pinned sig), `market-sign-unencodable`
(CJK node → nil sig, fail-closed), `market-verify-valid`,
`market-verify-tampered` (price 5→6, same sig → false),
`market-verify-unknown-issuer`, `market-match-signed-ok`,
`market-match-signed-badsig` (whole match refused,
`market/signature-invalid`, nodes `["n2"]`).

## Live disk demos (/tmp/sig-demo, not committed)

- `market-sign att1.edn --issuer-secret s3cr3t` mints the pinned sig.
- `market-match asks bids --today 2026-11-09 --secrets secrets.edn`
  → `:ok` with the match. Without `--secrets` → legacy presence-only
  path, same match (backward compatible).
- Ask with price tampered 9→99, same sig → refused
  `market/signature-invalid`, exit 1.
- Non-latin-1 node → `market-sign` refused `market/unsignable`, exit 1.
- Bug found live and fixed: `market-sign` first parsed the flag from
  the wrong arg slice (usage, exit 64); fixed to read `--issuer-secret`
  from the full tail, re-verified (suite re-run green after the fix).

## Contract

- Signed payload is the fixed field order
  `node|cpu|mem-gib|disk-gib|price|expires|issuer`, latin-1 only.
  Missing fields or non-latin-1 text never get signed; unknown issuers
  and secret mismatches never verify.
- Secrets cross the CLI boundary only (`--issuer-secret`, `--secrets`
  file); they never land in manifests, asks, cases, or evidence.
- `--secrets` absent → legacy presence-only matching (documented,
  kept for unsigned callers). The gate refuses the whole match, not
  just the bad ask (fail-closed, same shape as expiry refusals).
- What stays open: asymmetric signatures (no portable ed25519 in
  `.cljc` — strangers still cannot be verified, only admitted issuers),
  escrow-record signing, retention/GC, multi-peer fallback.
