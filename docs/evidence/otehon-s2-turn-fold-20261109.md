# Verification record — S2 live turn fold + host step-fn — 2026-11-09

JUN-1036 S2 (`execute the gated turn`, continued): the live-call seam.
The provider rail stays host-side by design; the oracle now owns both
halves of the handoff contract.

## Files

- `src/itonami/otehon/execute.cljc` — `turn-from-transcript` (new):
  fold a finished `run-turn` transcript into the legacy executor-result
  shape (`{:final :exit-reason ...}`) that `apply-turn!` saves. `:done`
  saves the last executed step's result (`:status` stripped: the legacy
  shape has none); anything else — failed turn, passed-through gate,
  empty or malformed transcript — fails closed as a `[CRON_FAILURE]`
  turn naming the transcript reason.
- `src/itonami/otehon/cli.cljc` — `:step-fn` injection through
  `execute-run` opts: a `:planned` turn with a host step-fn runs the
  plan's `:exec` through `execute/run-turn`, folds via
  `turn-from-transcript`, and saves the legacy way
  (`apply-planned-turn!`, both plan sites). The `:turn` injection and
  the pending exit-3 path are unchanged. `run-step-stub` mirrors the
  `:turn-exec` `:via` selection (`ok` / `fail` / `throw` / `bad`).
- `test/cases.edn` — 5 `:turn-exec/fold-*` cases (done-last-wins,
  failed, threw, held passthrough, empty) and 4 `:run/step-*` cases
  (`ok` through the post-script site, `fail` / `throw` / `bad` through
  the direct site), all byte-pinned.

## Portability notes

- The fold's failure final is built from the transcript's own reason
  string plus the step id — no per-runner content, so the byte-identical
  contract holds (caught live: the first render-held expectation missed
  one `\n`, fixed before green).
- `:doc-has` keys must be a subset of `:needles` — the harness derives
  got's `:doc-has` from the needles only (caught live on step-fail /
  step-throw, fixed before green).

## Runs

`./bin/run-shared-cases basilisp bb nbb` (2026-11-09, after the
render-transcript slice): 554/554 pass, 28 negative controls, must-fail
64/64, `bb`/`nbb` output identical to basilisp. Runners:
basilisp 0.5.1 / bb 1.12.218 / nbb on node v26.3.0.

## Not done (still open in S2)

Watcher fan-in live integration, `system-prompt` / `skills-index` /
`context-file` readers (host-side by design, per `assembly.cljc`).
No commit/push made. Integration note: the s2e branch
(`jun784/jun-1036-s2e-basilisp`, verified 558/558 x3 in worktree
`~/github/workspaces/s2e-check`) touches the same `declare` line in
`cli.cljc` — one-line union resolution when both land.
