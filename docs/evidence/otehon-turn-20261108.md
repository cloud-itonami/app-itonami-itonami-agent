# Otehon finished-turn slice (JUN-1036 S2b) — 2026-11-08

Unit: applying a finished agent turn inside `run-exec`, ported verbatim
from the legacy `run-job-core` result path (same statuses, same
output-doc shapes including the `## Error` appendix and peer/placement
lines, same jobs.json record shapes, same usage/run audit rows, same
exit codes). Internal implementation differs freely (pure `exec.cljc`
verdicts + host apply/append in `cli.cljc`); the surface matches. The
live rail call itself stays open (no executor yet: a planned turn
without a canned result still stops at `:planned` with exit 3).

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-s2b/otehon
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-08)

```
PASS  basilisp cases: {:cases 185, :fail [], :negative-controls 27, :pass 185, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 11/11 reported FAIL
PASS  bb cases: {:cases 185, :fail [], :negative-controls 27, :pass 185, :runner "babashka 1.12.218"}
PASS  bb must-fail: 11/11 reported FAIL
PASS  nbb cases: {:cases 185, :fail [], :negative-controls 27, :pass 185, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 11/11 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

185 cases (was 178): 7 new `run/*` turn cases — ok, silent (with a
non-local lane, proving silent skips delivery), error, peer/placement
lines, monitor-changed turn, unresolved-lane `delivery_failed` (exit 0),
adapter-resolved delivery. must-fail stays 11/11. Canon outputs
byte-identical across runners.

## Compat notes (legacy `cron.cljk` vs otehon)

- Statuses and exits follow `run-job-core` exactly: only `error`
  exits 1; `delivery_failed` exits 0 (like the legacy `cron run`).
- `silent` skips delivery entirely, even for non-local lanes.
- A lane with no adapter reports the legacy unresolved message
  (`delivery requested for 'L' but no Kotoba delivery adapter is
  configured`) without touching the queue.
- Deliberate deferral, recorded in `otehon/COMPAT.md`: the
  executions-ledger and delivery-queue ROWS (sqlite) stay slice S5.
  Only their observable inputs, statuses, messages and the audit JSONL
  rows are produced here.

## Open (per `otehon/COMPAT.md`)

- Live rail call; ledger/queue rows (S5); `runs`/`incidents` reads (S4).
- S3b remainder: doctor/sessions reads. S4 store writes, S5 messaging,
  S6 tool backends.
