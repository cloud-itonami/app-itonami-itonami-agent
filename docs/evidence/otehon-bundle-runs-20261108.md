# Otehon bundle/runs slice (JUN-1036 S4b) — 2026-11-08

Unit: `profile check`/`readiness`/`refresh`/`itonamify`/`rail` plus
`cron runs`/`history` and `cron incidents` list/ack, ported verbatim
from the legacy CLI (same lines, same keep/skip/would shapes, same
exit codes, same ledger row shapes). Internal implementation differs
freely (pure bundle math + host sqlite reads in `io.cljc`); the
surface matches. Bundle CIDs are cross-checked against an independent
Python oracle (sha256 + multihash prefix + base32).

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-s4b/otehon
rm -rf test/scratch src/itonami/otehon/__pycache__
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-08)

```
PASS  basilisp cases: {:cases 251, :fail [], :negative-controls 27, :pass 251, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 14/14 reported FAIL
PASS  bb cases: {:cases 251, :fail [], :negative-controls 27, :pass 251, :runner "babashka 1.12.218"}
PASS  bb must-fail: 14/14 reported FAIL
PASS  nbb cases: {:cases 251, :fail [], :negative-controls 27, :pass 251, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 14/14 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

251 cases (was 219): 4 check (clean/errors/summary/--all), 4
readiness (ready/blocked/malformed-jobs/--all), 5 itonamify
(wrote/keep/force/skip/dry-run), 5 refresh (keep/wrote/skip/usage/
dry-run), 5 rail (wrote/keep/skip/usage/dry-run), 5 runs
(rows/limit-1/empty/unreadable/garbage-limit) and 5 incidents
(list/ack/missing/closed/empty). must-fail 14/14 (was 11/11: one new
entry per new op family). Canon outputs byte-identical across runners.

## Compat notes (legacy `main.cljk`/`profile.cljk`/`host.cljk`/`cron.cljk`/`execution_ledger.cljk` vs otehon)

- New `bundle.cljc`: bundle-files/manifest/cid, default-manifest,
  with-inference-rail, write-manifest!, with-bundle,
  refresh-bundle-cid. JSON render sorts object keys (map iteration
  order differs per runner); only bundle CIDs hash it. Written
  `itonami.edn` sorts keys at every level (same reason); content is
  identical and reads back the same.
- `profile list` keeps the legacy default-first order (the sort itself
  stays the recorded divergence); the model column reads
  `model.default` like the legacy row.
- `readiness` reuses the doctor findings (same inputs, same
  issue-summary); the S6-owned finding kinds (toolsets/tools,
  browser backend, oauth cache) are deferred like in `doctor`.
- `runs` keeps the legacy take-last limit over newest-first rows
  (limit 1 prints the oldest row — verbatim quirk, pinned by a case).
  A missing ledger reads as no rows; reads never create the ledger
  file (the legacy open-db creates it — output-identical, recorded
  in COMPAT). Receipt join comes from `cron/itonami-runs.jsonl`.
- `incidents ack` is fenced on current state (no changes count
  needed): unknown and already-closed signatures refuse identically.
- Shared store-case directories now start empty per case
  (`io/wipe-dir!` + `remove-dir!` in `seed-files!`): a previous
  runner's created files (itonamify's manifest) leaked into the next
  runner's create-if-absent cases through the shared scratch dir.
  The bb/nbb `profile-itonamify-wrote` failure exposed it; the wipe
  fixed all three runners with no change to passing cases.

## Incidents during the slice (found and fixed before green)

- Basilisp has no `sorted-map`/`array-map`/`unsigned-bit-shift-right`.
  `json-text` sorts keys explicitly; `write-manifest!` renders
  through `sorted-edn`; the shift is arithmetic (all values
  non-negative, so it equals the unsigned shift).
- bb/nbb parse the edited `cli.cljc` strictly: one unbalanced paren
  in the new profile block failed both (fixed, then re-verified).
- Nested manifest key order differs per runner after a parse
  (bb hash-maps above 8 entries): `sorted-edn` now sorts recursively
  instead of only at the top level.
- `must-fail.edn` gained one unbalanced `]` in the new block
  (stack-checked with line numbers, fixed).
- Case bugs, not code bugs: the check summary counts
  profiles-with-errors (1, not 14); legacy `would` takes two spaces
  while `wrote` takes three.

## Open (per `otehon/COMPAT.md`)

- S4 remaining: adopt/release/soul/did/organize/import/export/hy-*.
- S5 messaging (ledger/queue writes, gateway/send/deliver/peers/chat).
- S6 tool backends, live rail call.
