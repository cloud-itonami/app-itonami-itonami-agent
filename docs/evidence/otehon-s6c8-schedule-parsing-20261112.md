# Verification record — S6c-8 schedule parsing — 2026-11-12

JUN-1036 S6c-8: the eighth S6c slice — the pure half of the
schedule parser in `tick.cljc`. `parse-duration-minutes` reads a
duration string to whole minutes (a bare number is not a duration,
a bare unit means one, zero is not positive); `natural-cron-expr`
reads a weekday/time phrase to a 5-field cron expression through
the fixed word table with am/pm and range checks, else nil. The new
`:tick-parse` op (`duration` / `natural` verbs) mirrors the
`:doctor` / `:doctor-kinds` split: the existing `:tick` op keeps
serving tick plans. 12 shared cases + 2 must-fail controls; 1078
cases + 137 must-fail green on basilisp 0.5.1 / bb 1.12.218 / nbb,
canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/tick.cljc`: + `parse-duration-minutes`
  / `natural-day-map` / `natural-cron-expr`.
- `otehon/src/itonami/otehon/cli.cljc`: declare + `:tick-parse`
  dispatch + `ops-tick-parse`.
- `otehon/COMPAT.md`: S6c-8 entry + cron jobs row updated.
- `test/cases.edn` +12, `test/must-fail.edn` +2.

## Portability notes

- No interop: regex, strings, maps and the namespace `parse-int`
  only — no `:lpy` branch, no js/parseInt. Day/month arithmetic
  never happens here: this only produces the expression, and
  `parse-cron` validation stays where it was.
- Verified divergence: none. The `in X` relative arm and the ISO
  fallback need the host clock, ISO parsing and ISO formatting, so
  the full `parse-schedule` stays host-side until those time
  helpers are twinned.

## Runs

- bb 1.12.218: 1078 cases, 0 fail; must-fail 137/137 FAIL (as designed).
- nbb (node v26.3.0): 1078 cases, 0 fail; must-fail 137/137 FAIL.
- basilisp 0.5.1: 1078 cases, 0 fail; must-fail 137/137 FAIL.
- Canon byte-identical across the three runners.
