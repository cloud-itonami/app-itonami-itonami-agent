# Otehon decentralized storage slice — measured runs (2026-11-08)

Volume: `cas/peer/market` namespaces + `cas-put/get/verify`,
`peer-push/pull`, `market-match` commands (`itonami-otehon`).
Runners: bb 1.12.218, nbb 1.4.208, basilisp 0.5.1 / python 3.14.7.

## Shared cases (in repo, all runners)

`bin/run-shared-cases`: 97/97 pass on bb, nbb, basilisp; must-fail 5/5;
bb/nbb outputs byte-identical to basilisp. 19 new cases:
`cas-sha-*` (NIST ""/abc + hashlib oracle 5-byte binary + 64-byte
full-block), `cas-manifest-*` (ok/tamper/missing/empty),
`peer-plan-*` (fetch/send/equal/empty), `market-match-*`
(basic/cheapest/nofit/tie), `market-attest-*` (valid/expired/unsigned).

## Live disk demos (/tmp/cas-demo, not committed)

Fixtures: `a.txt` (13 B text), `blob.bin` (1 MiB random),
`edge.bin` (`00 80 FF 01 FE`).

- bb `cas-put` store: `{:stored 3}`.
- nbb `cas-verify`: `{:checked 3, :mismatch [], :missing []}`.
- nbb `cas-get` blob: byte-identical (`cmp` clean). Digest
  `d4ceb21a…` matches python hashlib independently.
- basilisp `cas-get` edge.bin: byte-identical (`00 80 FF` survive latin-1
  round-trip on all three runners).
- basilisp `cas-put` + `peer-push` small store → nbb `cas-verify` clean.
  Plan shows sorted digests; re-push is a no-op (`:applied 0`).
- bb `peer-push` 1 MiB store → peerC: `:applied 3`, verify clean.
- Tamper (first byte of one object → `X`): `cas-verify` →
  `{:mismatch ["edge.bin"], :status :refused}`.
- Registry gate: unknown node → `peer/not-member`; expired grant →
  `peer/not-member`; valid grant + matching prefix → `:ok`.
- `market-match` with an expired attestation → refused listing the node;
  without it → `{:matches [{b1→n1} {b2→n2}], :unmatched []}`.

## Limits found (recorded, not hidden)

- Pure SHA-256 in `.cljc` is slow on big inputs under Basilisp
  (1 MiB object >120 s; bb/nbb finish in seconds). Small files,
  manifests and attestations are instant everywhere. No host crypto
  is used on purpose; native code must use platform hashing.
- K/H0 constants are generated from first principles (prime fractional
  parts, script in session log) and cross-checked three ways: NIST
  vectors, python hashlib, and three-runner agreement.
- Hand-written EDN (registry, asks/bids) may use string keys; the CLI
  normalizes known maps at the boundary (documented in code).
  Canon-written files (manifests) always use keywords.
- `peer-push` copies missing objects; it does not repair tampered
  objects in place (`cas-verify` is the detector). Repair is a next slice.
- Market matching has no signatures verified (`:sig` presence only);
  escrow records are unsigned by design, stated in code.
