# Otehon did/bundle slice (JUN-1036 S4e) — 2026-11-09

Unit: `profile did` (fleet-seed did:key, registry epochs, `--registry` /
`--out`) and the missed read-only `profile bundle` (CID + manifest
print), ported verbatim from the legacy CLI. Internals differ freely
(pure-cljc Ed25519); the surface matches.

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-did/otehon
rm -rf test/scratch
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-09)

```
PASS  basilisp cases: {:cases 313, :fail [], :negative-controls 27, :pass 313, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 23/23 reported FAIL
PASS  bb cases: {:cases 313, :fail [], :negative-controls 27, :pass 313, :runner "babashka 1.12.218"}
PASS  bb must-fail: 23/23 reported FAIL
PASS  nbb cases: {:cases 313, :fail [], :negative-controls 27, :pass 313, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 23/23 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

313 cases (was 303): 8 did (wrote/epoch/all/out/refused/
bad-seed/usage/all-store) + 2 bundle (ok/missing) + 1 no-op seed
case counted in the shared file. must-fail 23/23 (was 21/21: did
wrong-did, bundle wrong-cid). Canon outputs byte-identical.

## Compat notes (legacy `identity.cljk`/`main.cljk`/`p2p.cljk` vs otehon)

- New `ed25519.cljc` (pure): KDF `SHA-256(seed || 0x00 || key-id)`
  via `cas/sha256-digest`, the RFC 8032 seed expansion the legacy
  route gets from `createPrivateKey` (SHA-512 + clamp low 32),
  a clamped scalar times the base point on the twisted Edwards
  curve, `did:key:z` of `0xed01 || pubkey` with a verbatim
  base58btc port. Host touches are UTF-8 encoding and SHA-512
  (`io.cljc`, exact in every standard library); the field ladder
  uses 16-bit limbs so every intermediate is exact on doubles,
  longs and ints. Hot paths use transients (verified on all three
  runners); no host branching in the curve code.
- `did` refuses exit 2 without a 64-hex seed (the passed env only —
  no host fallback, so the control holds even where the variable
  is exported); usage without targets; `--all` sorts registry ids
  and falls back to store names; `--out` writes the public-keys
  EDN (hand-rolled sorted emit — `pr-str` map order is
  runner-dependent — same parsed content as the legacy
  sorted-map print).
- `bundle` prints the CID plus `  <sha12>  <path>` manifest lines
  read-only; a missing home errors adopt-style (exit 0). This verb
  was missing from the S4c denominator; recorded in COMPAT now.
- Did values are identity: all four case DIDs cross-checked
  against an independent node:crypto oracle (same construction
  as the legacy route, different code path):
  `z6Mknsjht...` (p1), `z6Mkq8Pz...` (p1#2),
  `z6Mkfa89...` (S1 p1#2), `z6Mki7PN...` (S1 p2).
- Bundle manifest SHAs recomputed with an independent Python
  oracle — all six match, including the normalized `cron/jobs.json`
  entry.

## Incidents during the slice (found and fixed before green)

- First ladder packed the X coordinate; Ed25519 compresses Y with
  the x-parity bit. Fixed; the standard compressed base point
  `5866…66` now round-trips, and 2B/3B match an affine reference.
- The port first hashed okm directly as the scalar, but the
  legacy route hashes the 32-byte seed with SHA-512 inside
  `createPrivateKey`. Added host SHA-512 (`io/sha512-digest`) and
  the clamp-low-32 expansion; DIDs then matched node exactly.
- A wrong base-point hex constant from memory caught by the
  oracle before first run (verified `Bx`, `By`, `p` limbs and the
  `2^255-21` bit pattern independently).
- A `cond` replaces an illegal `case` with list constants in the
  hex decoder; a `bytes`-named parameter shadowed the
  constructor on Basilisp (renamed).
- `vec` of a `subvec` is not editable on bb (`transient`
  rejects); `norm` now builds `(into [] v)` first.
- Per-derivation cost on Basilisp is ~45 s (oracle path, rare
  admin ceremony); bb/nbb answer in under a second. Cases keep
  derivations to five. Recorded here so a future slice needing
  many derivations knows to budget or window the ladder.

## Open (per `otehon/COMPAT.md`)

- S5 messaging writes (ledger/queue, gateway/send/deliver/peers/chat).
- S6 tool backends, live rail call.
