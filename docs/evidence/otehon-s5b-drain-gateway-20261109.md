# Otehon S5b drain rail + gateway transport core (JUN-1036) — 2026-11-09

Unit: the cron drain rail (`cron.cljk` deliver/drain/settle/abandoned/
finish-run sections) plus the pure core of the messaging gateway
(`gateway.cljk` platform values, delivery transport, adapter/injection
resolution, watcher send seam), ported to `.cljc` behind the same
observable API (same tables, statuses, returns, error strings).
Adapters are injected (declarative stubs in cases); the live agent
loop, promise fan-out, watcher fan-in (batching/timers) and the serve
loop stay host-side / later slices.

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-s5b/otehon
rm -rf test/scratch
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-09)

```
PASS  basilisp cases: {:cases 361, :fail [], :negative-controls 27, :pass 361, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 32/32 reported FAIL
PASS  bb cases: {:cases 361, :fail [], :negative-controls 27, :pass 361, :runner "babashka 1.12.218"}
PASS  bb must-fail: 32/32 reported FAIL
PASS  nbb cases: {:cases 361, :fail [], :negative-controls 27, :pass 361, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 32/32 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

361 cases (was 341): 13 drain (drain-and-settle incl. stub call
record, failed-no-adapter error string, abandoned sweep, 4 finish-run
shapes ok/failure/silent/delivery-fails with output doc + run/usage
audit lines) + 7 gateway (native-wins, relay-fallback, disabled
native/all-disabled, send-ok, discord metadata, blank-chat guard,
throwing adapter warn). must-fail 32/32 (was 28/28: settle-stale
fence, deliver-error surfaces, empty drain reads zero, blind send
answers nil). Canon outputs byte-identical across the three runners.

## Compat notes (legacy vs otehon)

- The drain reads the lane from the queued `job_json` (verbatim:
  a bare `{"id":...}` row with no `deliver` drains vacuously local —
  setups store the full job exactly as `enqueue!` does).
- `settle-queued-delivery!` is fenced on the ledger's latest attempt
  for the job: with no executions row it is a no-op (job untouched),
  covered by the abandoned case.
- Prune redacts terminal payloads (`job_json`/`content`) but keeps
  the rows — verbatim legacy; expectations assert the redacted form.
- `record-run-state!` stamps no per-job `updated_at` (only the
  document root carries one) — verbatim legacy.
- Incident `failure_type` derives from the error text (`delivery
  outcome unknown...` classifies `delivery`); the stored `error`
  column is the literal `"execution failed"` redaction — both verbatim
  legacy.
- Platform/config maps are looked up by string lane with keyword
  `:enabled` flags — verbatim legacy (`nil` config reads enabled).
- The harness `test` invocation is relative (`test/cases.edn`) so
  reported output-file paths stay `test/scratch/...` identical on
  every machine and runner; CLI print paths were already cwd-relative.
- `drain-queued!` runs sequentially where the legacy fans out with
  promises; only terminal rows are observable and they are identical.
- `finish-run!` takes the loop result as a data arg (injected in
  cases); the live agent loop stays host-side.

## Work notes

- One slice-local reader snag: a missing `defn` closer surfaced first
  as an `Unexpected char ']'` downstream (line 2099) and then as EOF;
  fixed by reading each new form with the real Basilisp reader, which
  pointed at `run-drain-step`. No shared code was affected.
- One full-suite run mid-slice (before the setup fixes) reported
  `failed`/telegram rows for the two drain cases whose setups then
  stored a bare `{"id":...}` job_json; every run since (isolated,
  two-case sequence, full suite on all three runners) agrees on the
  vacuous-local `delivered` reading that the legacy lane resolution
  dictates for such rows. The setups were then made faithful (full
  job, as `enqueue!` stores) and the suite is green. The single
  anomalous reading is unproven — suspected a stale Basilisp bytecode
  cache from the paren-fix edits mid-flight — and no longer
  reproducible; it is recorded here rather than asserted.
- The `__pycache__` dirs the runners regenerate were removed before
  commit.
