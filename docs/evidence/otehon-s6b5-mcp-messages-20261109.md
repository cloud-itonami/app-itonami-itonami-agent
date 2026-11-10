# Verification record — S6b-5 mcp message shapes — 2026-11-09

JUN-1036 S6b-5: the pure message half of `itonami.agent.mcp`. `mcp.cljc`
gains handshake-lines / call-line (session open + one call input),
collect-call-results / collect-exchange-result (raw stdout lines to the
legacy answer, incl. the exact error vocabulary) and render-result
(tool result to the call answer, incl. text/structuredContent dedupe
and _meta passthrough), with `:mcp` case verbs, 20 shared cases and
2 must-fail controls. Exchange spawn, oauth-cache-ready, discover
projection and the call dispatch stay S6.

## Files (this slice only, base 21e52b6; earlier slices alongside untouched)

- `src/itonami/otehon/mcp.cljc`: + builders / collectors / render-result /
  hermes-native-aliases (data only; the alias projection rides the
  discover slice).
- `src/itonami/otehon/cli.cljc`: `ops-mcp` grows the five verbs.
- `otehon/COMPAT.md`: mcp row + S6b-5 entry.
- `test/cases.edn` +20, `test/must-fail.edn` +2.

## Portability notes

- A docstring with unescaped `"` inside broke loading on every
  runner (the string ends early and the defn macroexpand fails) —
  caught by the gate (all three runners errored), fixed by rewording
  without embedded quotes. Lesson recorded here, not in COMPAT: it
  was authoring, never legacy behavior.
- `collect-*` take string-keyed maps (`{:strs ...}`), like the wire
  maps they fold; keyword `:keys` would read nil out of EDN-passed
  args. Error answers are string-keyed `{"error" ...}`, verbatim
  from the legacy.
- Expected JSON strings are compact (no spaces): `hio/json-str`
  emits canonical compact form on all runners (already pinned by the
  tts speech-request cases). The first draft used Python-default
  spaced separators and failed identically everywhere — the gate
  compares values, not bytes, so this was expectation-side only.
- `re-matches` with the plain character class, `re-pattern`-free
  code throughout this slice (no new regex idioms beyond S6b-4's
  glob).

## Runs

`./bin/run-shared-cases basilisp bb nbb` after the final edit:
713/713 pass, 28 negative controls, must-fail 81/81,
`bb`/`nbb` output identical to basilisp. Runners:
basilisp 0.5.1 / bb 1.12.218 / nbb on node v26.3.0.

## Negative controls

- The 2 new must-fail entries (wrong render string, wrong collect
  value) are reported FAIL by all three runners.
- Single-runner pre-checks (bb, basilisp) were green before the
  gate; the gate is the first full run.
