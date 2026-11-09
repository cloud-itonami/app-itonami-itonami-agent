# Verification record — S2 turn-execution core — 2026-11-09

JUN-1036 S2 (`execute the gated turn`): new pure core
`itonami.otehon.execute/run-turn`. Takes the gated plan's `:exec`
steps plus an injected `:step-fn`, folds them into a transcript.
Nothing is called, read, or written besides `:step-fn` (S5c
injected-transport idiom): model calls, tools, and stores stay
host-side.

## Files

- `src/itonami/otehon/execute.cljc` (new) — `:done` with ordered
  `:steps`/`:ran`; non-`:ok` gates pass through untouched; malformed
  plan/step, unknown step, non-map result, and throwing step-fn all
  fail closed with named reasons; never blocks (no waiting).
- `src/itonami/otehon/cli.cljc` — `:turn-exec` case op with `:via`
  stub selection (ok / fail-1 / unknown / bad / throw), same pattern
  as `:market-verify-asymmetric`.
- `test/cases.edn` — 12 `:turn-exec` cases (ok paths, passthrough,
  empty exec, mid-run failure, unknown/bad/throwing steps,
  malformed plan/step/exec).

## Portability notes

- Step-failure `:error` uses `ex-message` (portable idiom used across
  the codebase); `(str e)` differs per runner and would break the
  byte-identical contract (caught by the suite itself).
- Vectors in `:expect` compare exactly; runner-varying strings must
  not appear in expected values.

## Runs

1. New op before implementation: runner rejects unknown op
   (observed while wiring).
2. Combined run: basilisp/bb/nbb `542 passed, 0 failed`,
   must-fail 64/64, `bb output identical to basilisp`,
   `nbb output identical to basilisp`.

## Not done (still open in S2)

Host-side live calls (provider rail), watcher fan-in, `system-prompt`/
`skills-index`/`context-file` readers. No commit/push made.
