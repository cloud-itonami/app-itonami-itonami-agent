# Verification record — S6b-10 finish-call dispatch tail — 2026-11-09

JUN-1036 S6b-10: the finish-call tail of `itonami.agent.code-rpc`.
`code_rpc.cljc` gains `token-str` (request-token normalization:
nil/false to `""`, else `str`), `make-state` (dispatch-state
constructor with defaults for allowed tools and max tool calls),
`args-preview` (80-char truncation) and `finish-call!` (result
coercion, budget consumption, bounded logging with durations), with
a `:code-rpc` verb, 11 shared cases and 2 must-fail controls. Token
check, dispatch, budget enforcement and the serve loop stay
host-side (S6).

## Files (this slice only, base 21e52b6; earlier slices alongside untouched)

- `src/itonami/otehon/code_rpc.cljc`: + `token-str`,
  `make-state`, `args-preview`, `round-half-up`, `finish-call!`.
- `src/itonami/otehon/cli.cljc`: `ops-code-rpc` grows the
  `token-str` / `make-state` / `finish-call` verbs.
- `otehon/COMPAT.md`: S6b-10 entry.
- `test/cases.edn` +11, `test/must-fail.edn` +2.

## Portability notes

- `round-half-up` needs one branch per runner
  (`Math/round` / `js/Math.round` / `math.floor(x + 0.5)`):
  durations here are non-negative, where all three agree, and the
  one Java/JS/Python split (negative halves) cannot occur.
- The logged duration is clock-derived, so shared-case comparison
  strips it (op-side); the pinned shapes are result, count, tool
  name and the 80-char args preview, including the 74-char
  truncation boundary case (`finish-trunc`).
- Error maps stay string-keyed (`{"error" ...}`), consistent with
  the S6b-8 error-shape pins; `result-error` reuse keeps the
  bounded-error rendering identical.

## Runs

`./bin/run-shared-cases basilisp bb nbb` after the final edit:
804/804 pass, 28 negative controls, must-fail 90/90,
`bb`/`nbb` output identical to basilisp. Runners:
basilisp 0.5.1 / bb 1.12.218 / nbb on node v26.3.0.

## Negative controls

- The new must-fail entries (`rpc-wrong-token`: empty map is `""`,
  not `"x"`; `rpc-wrong-log`: tool name is pinned) are reported
  FAIL by all three runners.
- Single-runner pre-checks (bb, basilisp: 34 + 5 green) came before
  the gate; the gate is the first full run.
