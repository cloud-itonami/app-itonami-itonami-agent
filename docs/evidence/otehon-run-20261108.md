# Otehon run-exec slice (JUN-1036 S2a) — 2026-11-08

Unit: `run-exec` host-side execution of one cron job now, ported
verbatim from the legacy `run-job`/`run-job-core` path (same lines,
same output-doc shapes, same jobs.json record shapes, same exit
codes). Internal implementation differs freely (pure `exec.cljc`
verdicts + host spawn/save/apply in `cli.cljc`/`io.cljc`); the surface
matches. The inference turn itself stays pending (exit 3, S2b):
`run-exec` stops at `:planned` with the `run-plan` canon.

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-s2a/otehon
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-08)

```
PASS  basilisp cases: {:cases 178, :fail [], :negative-controls 27, :pass 178, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 11/11 reported FAIL
PASS  bb cases: {:cases 178, :fail [], :negative-controls 27, :pass 178, :runner "babashka 1.12.218"}
PASS  bb must-fail: 11/11 reported FAIL
PASS  nbb cases: {:cases 178, :fail [], :negative-controls 27, :pass 178, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 11/11 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

178 cases (was 165): 13 new `run/*` cases — no-agent ok/blank,
script not-found/unsafe/error, `wakeAgent=false` gate, monitor
baseline/unchanged/failed, agent-turn pending, job-not-found,
monitor_url refusal, profile-invalid. must-fail 11/11 (was 9/9):
a missing-profile run and an agent turn with no runtime deps.
Canon outputs byte-identical across runners.

## Compat notes (legacy `cron.cljk` vs otehon)

- Monitor runs for every job with a monitor source (no state gating),
  exactly like `run-job`: unchanged output stays silent, a failed
  source records `[CRON_FAILURE] Monitor source failed: …` with the
  `## Error` appendix and the full text as `last_error`.
- Failed runs append `## Error` with the raw error and record the full
  text as `last_error` (never the bare `script_error` token).
- `monitor/monitor-url-unavailable` refuses reads: fetching is S2b.
- Three host bugs fixed while porting: `**` kwargs take one marker on
  Basilisp, `process.env` needs an explicit copy on nbb, `json-str`
  converts via `lisp->py` on Basilisp.

## Open (S2b and later)

- S2b: the inference turn (`run-plan` `:planned` → live rail call,
  delivery lanes, executions ledger).
- S3b remainder: doctor/sessions reads. S4 store writes, S5 messaging,
  S6 tool backends (per `otehon/COMPAT.md`).
