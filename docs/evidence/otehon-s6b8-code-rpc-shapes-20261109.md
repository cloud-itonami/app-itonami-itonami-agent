# Verification record — S6b-8 code-rpc error shaping + hints — 2026-11-09

JUN-1036 S6b-8: the pure shaping half of `itonami.agent.code-rpc`.
`code_rpc.cljc` ports tool-error, result-error, tool-errors-since,
sandbox-tools-for, the terminal-blocked-params const,
missing-import-hint, failure-hint-rules, failure-hint and
hint-on-failure, with a `:code-rpc` case op, 21 shared cases and
2 must-fail controls. The shim-header / tool-stubs data,
generate-hermes-tools, token check, dispatch, budget and log stay
with the file-transport/host slice (S6).

## Files (this slice only, base 21e52b6; earlier slices alongside untouched)

- `src/itonami/otehon/code_rpc.cljc` (new).
- `src/itonami/otehon/cli.cljc`: require + declare + dispatch +
  `ops-code-rpc`.
- `otehon/COMPAT.md`: `code_rpc.cljk` module row + S6b-8 entry.
- `test/cases.edn` +21, `test/must-fail.edn` +2.

## Portability notes

- `failure-hint-rules` keeps the legacy (regex, formatter) shape
  with real regex literals — no `re-pattern` building needed here;
  group captures (`(\w+)`, alternations, `[\w.]`) behave the same on
  all three runners, proven by the gate (rails groups already showed
  this in S6b-3; this slice re-proves it with four more patterns).
- The truncation marker is the literal `…` (U+2026), not three
  ASCII dots — caught while writing the unit, before any case ran.
- Expected JSON strings are compact (`hio/json-str` canonical form);
  Python's default spaced separators would fail identically
  everywhere (same lesson as S6b-5's message shapes).
- Authoring note (not a divergence): a stale single-run script with
  a missing `clojure.string` require exited 1 on basilisp after
  printing 21 oks — the script's bug, not the units'. Fixed and
  re-run green; the gate never depends on these scripts.

## Runs

`./bin/run-shared-cases basilisp bb nbb` after the final edit:
791/791 pass, 28 negative controls, must-fail 87/87,
`bb`/`nbb` output identical to basilisp. Runners:
basilisp 0.5.1 / bb 1.12.218 / nbb on node v26.3.0.

## Negative controls

- The 2 new must-fail entries (wrong hint, wrong sandbox list)
  are reported FAIL by all three runners.
- Single-runner pre-checks (bb 21 green; basilisp 21 + 2 green
  after the script fix) came before the gate; the gate is the
  first full run.
