# AWAI-543 query-plan evidence (2026-11-10)

Unit 1: pure Otehon namespaces for itonami-agent on awai fs + sqlite
query planning. Branch `jun784/awai-543-itonami-awai-fs-query-plan-cljc`
from main `3caea41`.

## What

- `otehon/src/itonami/otehon/store_fs.cljc` — tier/placement verdicts.
  Mirrors `kotoba/itonami/storage.kotoba` verdicts/codes (JUN-1061);
  hot (`worktree`, `git-common`, `build`) stays local, warm/cold need
  awai fs, missing mount is a hard error.
- `otehon/src/itonami/otehon/query_plan.cljc` — point / index-scan /
  range-prune / refused planner over the itonami sqlite shapes
  (`cron_notepad`, `deliveries`, `executions`, `cron_incidents`).
  No `visible?` input: visibility applies after materialization
  (ayatori / kotobase.bridge rule).
- `otehon/src/itonami/otehon/scan_budget.cljc` — block/byte/match gate.
  Counts, never wall clock (ayatori.pack bench rule). Limits inclusive.
- `cli.cljc` — `:store-fs` / `:query-plan` / `:scan-budget` shared-case
  ops only. `io.cljc` untouched, no `#?@`.
- `test/cases.edn` +38 (987 total), `test/must-fail.edn` +3 (117 total).

## Measured

- `bb -e` EDN parse: cases 987, must-fail 117.
- bb smoke of new namespaces: `placement/awai-fs`, point plan,
  over-blocks refusal, cli loads. All as expected.
- `bin/run-shared-cases bb` (2026-11-10): 985/987 pass, must-fail
  117/117. The 2 failures are pre-existing heavy sqlite CLI spawns,
  untouched by this diff: `:queue/prune-tombstone-bound` and
  `:ledger/terminal-overflow`, both
  `{:crash "sqlite3 failed: timed out after 30000ms"}` on the 1005-row
  seed via the bb `sqlite3` CLI path (`io.cljc` 30s spawn cap).
  All 38 new AWAI-543 cases pass on bb.
- New-ops smoke `/tmp/awai543-smoke.edn` (9 cases): bb, nbb, basilisp
  each 9/9 exit 0; outputs byte-identical after runner-line strip
  (bb==nbb, bb==basilisp).
- Versions: babashka 1.12.218, nbb on node v26.0.0,
  basilisp 0.5.1 / python 3.14.7, sqlite3 CLI present (`select 1`
  answers `[{"1":1}]`).
- nbb full suite: 987/987 pass, must-fail 117/117, runner nbb on
  node v26.0.0 (2026-11-10).
- basilisp full suite: 987/987 pass, must-fail 117/117, runner
  basilisp 0.5.1 / python 3.14.7 (2026-11-10).
- bb full suite on clean base (stashed unit, 954 cases): 952/954 pass
  with the SAME 2 failures (`:queue/prune-tombstone-bound`,
  `:ledger/terminal-overflow`, same 30s sqlite3 CLI timeout). Proves the
  2 bb timeouts are pre-existing and environmental (bb shells out to the
  `sqlite3` CLI per statement; nbb/basilisp use in-process sqlite and
  pass the same tests). The unit adds 33 passing cases, zero new
  failures.

## Not claimed

- No production qualification, no wall-clock SLO (single observation,
  shared machine). Oracle-only, not a gate. No Native fallback.
