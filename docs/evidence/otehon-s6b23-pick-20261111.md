# Verification record — S6b-23 chat number-pick — 2026-11-11

JUN-1036 S6b-23: the pure answer-parsing half of the chat clarify
loop, in `clarify.cljc`. `pick-answer` parses one readline answer
line into the locked answer, verbatim from the `chat` command's
`ask-at`: a blank line is nil; multi? maps the non-blank
comma-separated parts; otherwise the single pick. An all-digits token
naming an in-range 1-based choice resolves to that choice; anything
else (out-of-range, zero, float, negative, unparseable, huge) stays
the trimmed raw token, never an error — the loop re-asks, it does not
crash. The `pick` verb wired through the existing `ops-clarify`.
12 shared cases + 2 must-fail controls; 1021 cases + 121 must-fail
green on basilisp 0.5.1 / bb 1.12.218 / nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/clarify.cljc`: + `pick-one` /
  `pick-answer`.
- `otehon/src/itonami/otehon/cli.cljc`: `:clarify` dispatch grows the
  `pick` verb.
- `otehon/COMPAT.md`: S6b-23 entry + clarify table row updated.
- `test/cases.edn` +12, `test/must-fail.edn` +2.

## Portability notes

- Bare `re-matches` + `parse-long` with a per-platform try/catch,
  the same shape as `adapters.cljc` and `chat.cljc` use: no `:lpy`
  branch needed.
- Overflow cannot diverge: a huge digit string either fails to parse
  (JVM throws, caught to nil) or parses big (Python bignum, JS
  double) and then fails the 1-based range check against the small
  choice list. Every path lands on the raw token.
- The readline loop itself (`ask!`, close/settle handlers,
  per-question printing) and the host clarify-fn wiring stay S6.

## Runs

- bb 1.12.218: 1021 cases, 0 fail; must-fail 121/121 FAIL (as designed).
- nbb (node v26.3.0): 1021 cases, 0 fail; must-fail 121/121 FAIL.
- basilisp 0.5.1: 1021 cases, 0 fail; must-fail 121/121 FAIL.
- Canon byte-identical across the three runners.
