# Verification record — S6b-11 transport-name + token-check tail — 2026-11-10

JUN-1036 S6b-11: the remaining pure predicates of
`itonami.agent.code-rpc`. `code_rpc.cljc` gains `token-ok?`
(constant-time token check, empty server token fails closed),
`duration-seconds` (legacy shape with the clock injected),
`int-seq?` (parsed JSON seq number), `req-file?` and `res-name`
(transport filename rules), with `:code-rpc` verbs, 29 shared cases
and 2 must-fail controls. `finish-call!` now renders its duration
through `duration-seconds` (same values, single definition).
Dispatch, budget enforcement and the serve loop stay host-side (S6).

## Files (this slice only, base 21e52b6; earlier slices alongside untouched)

- `src/itonami/otehon/code_rpc.cljc`: + `duration-seconds`,
  `floor-num`, `finite-num?`, `int-seq?`, `req-file?`,
  `res-name`, `token-ok?`; `finish-call!` refactored onto
  `duration-seconds`.
- `src/itonami/otehon/cli.cljc`: `ops-code-rpc` grows the
  `token-ok?` / `duration-seconds` / `req-file?` / `res-name` /
  `int-seq?` verbs.
- `otehon/COMPAT.md`: S6b-11 entry.
- `test/cases.edn` +29, `test/must-fail.edn` +2.

## Portability notes

- `int-seq?` uses numeric `==`, not `=`: bb/SCI `=` is strict
  across Long/Double (`(= 1 1.0)` is false), while all three
  runners agree on `==`. Integral floats count (1.0 is a valid
  seq); strings, booleans, nil, NaN and infinities do not.
- `token-ok?` folds length mismatch and per-char differences into
  one accumulator with no early exit (constant-time shape). The NUL
  pad (`(char 0)`, no backslash escape in source) only aligns
  lengths; a length mismatch already forces false, so pad
  collisions cannot flip a verdict.
- `res-name` pins the Python `f"{seq:06d}"` rule including the
  sign-counts-toward-width case (`-1` to `res_-00001`) and the
  no-truncation overflow (`1234567` to `res_1234567`).
- `duration-seconds` pins the half-up boundary (`1555ms` to
  `1.56`); `finish-call!` refactor changes no logged value (the
  S6b-10 cases pass unmodified).

## Runs

`./bin/run-shared-cases basilisp bb nbb` after the final edit:
833/833 pass, 28 negative controls, must-fail 92/92,
`bb`/`nbb` output identical to basilisp. Runners:
basilisp 0.5.1 / bb 1.12.218 / nbb on node v26.3.0.

## Negative controls

- The new must-fail entries (`rpc-wrong-token-ok`: wrong token
  expected true; `rpc-wrong-res`: `res-name` of 7 expected
  `res_000008`) are reported FAIL by all three runners.
- A pre-fix probe caught the bb/SCI `=` quirk (`int-one`,
  `int-zero` FAIL before the `==` fix); single-runner pre-checks
  (bb, basilisp: 63 + 7 green) came before the gate; the gate is
  the first full run.
