# Otehon S5a state plane (JUN-1036) — 2026-11-09

Unit: the delivery vocabulary (`delivery.cljk`) plus the queue and ledger
sqlite write paths (`delivery_queue.cljk`, `execution_ledger.cljk`),
ported to `.cljc` behind the same function-level API (same fns, args,
tables, statuses, returns). Reads (`runs`/`incidents`) were already
ported in S4b; this slice adds every write: queue enqueue / claim /
finish / recover / prune-tombstone, ledger claim / handoff / adopt /
run / finish / delivery-outcome / incident upsert / get / ack / alert /
list / resolve / repeat-alert gate. Gateway long-run, peer serve and
lane adapters stay later slices.

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-s5/otehon
rm -rf test/scratch
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-09)

```
PASS  basilisp cases: {:cases 341, :fail [], :negative-controls 27, :pass 341, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 28/28 reported FAIL
PASS  bb cases: {:cases 341, :fail [], :negative-controls 27, :pass 341, :runner "babashka 1.12.218"}
PASS  bb must-fail: 28/28 reported FAIL
PASS  nbb cases: {:cases 341, :fail [], :negative-controls 27, :pass 341, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 28/28 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

341 cases (was 313): 5 queue (enqueue/status, claim-next order,
claim/finish fence, recover dead-vs-live, 1005-row prune bound +
tombstone short-circuit) + 10 ledger (claim fence, handoff/adopt flow,
run/finish fence, adopt fence, restart recovery incl. adoption grace,
incident full cycle, withhold matrix, failure-type matrix, rows order,
1005-row terminal overflow) + 13 delivery vocabulary
(normalize/list/lanes/token/classify). must-fail 28/28 (was 23/23:
queue finish-twice, queue claim-next-empty, ledger double-claim,
ledger finish-unknown, ledger ack-unknown). Canon outputs
byte-identical across the three runners.

## Compat notes (legacy vs otehon)

- Same DBs, tables, columns, statuses, id shapes
  (`cron/deliveries.db` deliveries + delivery_tombstones;
  `cron/executions.db` executions + cron_incidents; `completed` /
  `failed` ledger statuses; `j1_<12hex>` incident ids). Timestamps take
  an explicit trailing arg in the port (production passes live time).
- Fencing answers come from a follow-up SELECT (the hio writer returns
  no change count): re-claim, re-finish, re-ack and unknown ids all
  answer exactly like the legacy change counts in the single-writer
  oracle. No BEGIN IMMEDIATE around claim/recover.
- `job_json` keeps the legacy indent-2 layout via `json-str-2`.
- Prune redacts terminal payloads unconditionally; `finish`/`recover`
  return pre-update rows with the status overridden — verbatim legacy.
- `get-incident` selects the same 10 keys as the legacy
  (`acked_at` is a real column; an earlier draft misread it as a select
  typo — corrected before merge, no code change since both spellings
  are the same string).
- `handoff_started_at` is REAL seconds; the bb path inlines numbers
  bare so the grace comparison reads numeric on every runner.
- Repeat-alert cooldown parses `alerted_at` per runner
  (`parse-iso-ms`); unparseable/missing stamps deliver, hours <= 0
  re-alerts — all covered with fixed stamps (a far-future stamp proves
  the within-cooldown hold deterministically).

## One-off byte oracles (not gates)

- `job_json` layout: node v26.0.0 `JSON.stringify(job, null, 2)` for
  `{"id":"j1"}` and `{"id":"j9","deliver":"telegram, local",
  "for_failure":0}` vs port `json-str-2` on all three runners —
  bb and nbb byte-identical to node; Basilisp byte-identical for the
  single-key job and content-equal but hash-ordered for the multi-key
  job (Basilisp maps do not preserve insertion order; shared cases
  assert parsed content).
- Incident signatures: the 10 expected sigs in the matrix were
  precomputed with `sha256sum` over the normalized inputs and match
  the port's `sha256-utf8` path on every runner (all 8 failure types
  green).
- Process-start fingerprint and kill probes ran live on every runner
  inside the adopt/recover cases (macOS ps path on this host); no
  value is asserted, but any probe crash would fail the case.

## Environment note

One harness run during this slice failed with `No space left on
device`: the shared disk was at 100% (901G used) from other agents'
workspace accumulation, then recovered to 50G free while re-checking.
No foreign workspace was touched; the re-run passed. The `__pycache__`
dirs the runners regenerate were removed before commit.
