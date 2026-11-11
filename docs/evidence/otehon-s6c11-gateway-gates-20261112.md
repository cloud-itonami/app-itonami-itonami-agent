# Verification record — S6c-11 gateway run/serve gates — 2026-11-12

JUN-1036 S6c-11: the eleventh S6c slice — the `gateway run` /
`gateway serve` precondition twins in `gateway.cljc`.
`tick-interval-ms` reads `--interval` seconds to ms with the legacy
default (60 s) and the 1 s floor; `serve-interval-ms` shapes the
serve interval with neither (nil stays nil, unparsable reads nil —
the legacy reads NaN); `serve-gate` answers the telegram-only gate
as data. The new `:gateway-gates` op (`tick-interval` / `serve`
verbs) mirrors the `:tick-parse` split: the stub-harness `:gateway`
op keeps serving fan-in steps. 12 shared cases + 2 must-fail
controls; 1114 cases + 143 must-fail green on basilisp 0.5.1 /
bb 1.12.218 / nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/gateway.cljc`: + `parse-int-prefix`
  / `tick-interval-ms` / `serve-interval-ms` / `serve-gate`.
- `otehon/src/itonami/otehon/cli.cljc`: declare + `:gateway-gates`
  dispatch + `ops-gateway-gates`.
- `otehon/COMPAT.md`: S6c-11 entry.
- `test/cases.edn` +12, `test/must-fail.edn` +2.

## Portability notes

- No interop: `str/replace`, `re-find`, `str/starts-with?`, char
  arithmetic, maps and vectors only — each with existing gate
  precedent on all three runners.
- Verified divergence: one, kept. `serve-interval-ms` reads nil for
  an unparsable interval where the legacy reads NaN; both stay
  host-side with the serve loop (no floor, no default — only
  `gateway run` carries those, pinned by the zero/no-floor case
  pair). `parse-int-prefix` duplicates the `js-parse-int` shape so
  the gateway surface stays dependency-free.

## Runs

- bb 1.12.218: 1114 cases, 0 fail; must-fail 143/143 FAIL (as designed).
- nbb (node v26.3.0): 1114 cases, 0 fail; must-fail 143/143 FAIL.
- basilisp 0.5.1: 1114 cases, 0 fail; must-fail 143/143 FAIL.
- Canon byte-identical across the three runners.
