# Verification record — S6b-22 bridge request gate — 2026-11-11

JUN-1036 S6b-22: the pure request-gate half of the `bridge` JSONL
protocol, in new `bridge.cljc`. `validate-request` gates the first
line (profile default, string prompt, vector tool_names, unsupported
tools fail closed with the legacy messages verbatim);
`approval-outcome` routes an approval line against the pending id set
(only "once"/"deny" resolve, only "once" allows); `result-row` /
`error-row` shape the JSONL rows verbatim (usage counters default to
0, a failed run without an error map reports its exit reason). The
`:bridge` verbs `validate` / `approval` / `result` / `error` wired
through `ops-bridge`. 14 shared cases + 2 must-fail controls; 1009
cases + 119 must-fail green on basilisp 0.5.1 / bb 1.12.218 /
nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/bridge.cljc`: new (`validate-request`,
  `approval-outcome`, `result-row`, `error-row`).
- `otehon/src/itonami/otehon/cli.cljc`: `:bridge` dispatch +
  `ops-bridge`.
- `otehon/COMPAT.md`: S6b-22 entry + bridge table row updated.
- `test/cases.edn` +14, `test/must-fail.edn` +2.

## Portability notes

- No interop in the slice: plain maps, strings, vectors and
  `contains?` only, so no `:lpy` branch is needed.
- `vector?` mirrors the legacy gate exactly: a hand-written string
  tool_names refuses the same way a JSON-decoded array passes.
- Verified divergence: the legacy throws ex-info and the CLI maps it
  to an error row; the twin returns `{:error msg}` with the verbatim
  message so the shared cases can pin the fail-closed gate. The
  throw/return boundary stays at the future CLI wiring (S6).
- Evaluation order differs invisibly: the legacy computes
  unsupported-tools before the vector check, the twin after; the
  observable outcome is identical (the vector refusal wins either
  way). Readline loop, profile load, agent-loop run and event/result
  printing stay S6.

## Runs

- bb 1.12.218: 1009 cases, 0 fail; must-fail 119/119 FAIL (as designed).
- nbb (node v26.3.0): 1009 cases, 0 fail; must-fail 119/119 FAIL.
- basilisp 0.5.1: 1009 cases, 0 fail; must-fail 119/119 FAIL.
- Canon byte-identical across the three runners.
