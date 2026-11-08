# Otehon notepad/profile slice (JUN-1036 S4a) — 2026-11-08

Unit: `cron notepad` set/get/delete/list plus `profile list`/`show`,
ported verbatim from the legacy CLI (same caps and refusal texts, same
line formats, same exit codes, same WAL store shape). Internal
implementation differs freely (pure verdicts + host sqlite reads and
writes in `io.cljc`); the surface matches. The notepad section is wired
into run prompts (agent turns only — the legacy no-agent path never
renders it).

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-s4/otehon
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-08)

```
PASS  basilisp cases: {:cases 219, :fail [], :negative-controls 27, :pass 219, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 11/11 reported FAIL
PASS  bb cases: {:cases 219, :fail [], :negative-controls 27, :pass 219, :runner "babashka 1.12.218"}
PASS  bb must-fail: 11/11 reported FAIL
PASS  nbb cases: {:cases 219, :fail [], :negative-controls 27, :pass 219, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 11/11 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

219 cases (was 203): 12 notepad cases (set/get/delete/list,
missing keys, usage refusals, all four caps, full store) and 4
profile cases (list table, show with pinned fields, missing profile,
unknown sub). must-fail stays 11/11. Canon outputs byte-identical
across runners.

## Compat notes (legacy `cron.cljk`/`notepad.cljk`/`main.cljk` vs otehon)

- Notepad writes go through the same `io/sqlite-run!` used by the
  test scaffolding (DML with bound params; bb inlines escaped
  literals). Reads use `io/sqlite-rows`. WAL + DDL preamble runs on
  every access like the legacy `open-db`.
- `:profile show` renders the manifest line with fixed
  runtime/placement/bundle order (a manual join, not `select-keys`:
  select-keys order differs per runner and would break byte
  identity). The home line is machine-specific, so show cases pin
  the stable lines via `:messages-include`.
- `profile list` sorts names like `status` does (the recorded
  divergence from legacy filesystem order).

## Incident: S3b seed-prefix leak (found and fixed in this slice)

The merged S3b commit had rewritten pre-existing `run/*` and
model/fallback case seeds from profile-root paths (`config.yaml`)
to store paths (`profiles/p1/config.yaml`): its prefix script counted
braces inside EDN string literals, so block scans overran into
neighbouring cases. The breakage was masked because the worktree's
stale scratch still held the old root-level files (seed-files only
overwrites, never deletes). The fresh S4a worktree exposed it
(`run/job-not-found`, `run/monitor-url` failing on green HEAD).
Fixed by restoring every pre-S3b case block byte-identical from the
S2b tree (`2c40200`) and re-verifying from wiped scratch. Lesson:
verification runs that matter must start from wiped scratch (and a
cleared Basilisp cache); the harness does not clean either.

## Open (per `otehon/COMPAT.md`)

- S4 remaining: refresh/itonamify/adopt/release/soul/did/organize/
  readiness/hy-*, runs/incidents reads, ledger-backed writes.
- S5 messaging (ledger/queue/delivery), S6 tool backends, live rail call.
