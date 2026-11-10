# Verification record — S6b-3 local rail selection core — 2026-11-09

JUN-1036 S6b-3: the pure selection core of `itonami.agent.rails`.
`rails.cljc` ports small-model? / normalize-model-ids /
detect-entries / all-models / agent-models / select-candidates,
wired with a `:rails` case op, 19 shared cases and 2 must-fail
controls. Probe fan-out, rails.edn IO and complete's fetch loop stay
host-side (S6).

## Files (this slice only, base 21e52b6; S6b/S6b-2 files alongside are untouched)

- `src/itonami/otehon/rails.cljc` (new; the `rails.cljk` twin —
  `rail.cljc` stays the `provider.cljk` twin).
- `src/itonami/otehon/cli.cljc`: require + declare + dispatch +
  `ops-rails` (pure-data verbs, no scratch/env needed).
- `otehon/COMPAT.md`: `rails.cljk` module row, S6b-3 slice entry.
- `test/cases.edn` +19, `test/must-fail.edn` +2.

## Portability notes

- `small-model?` answers a boolean; the legacy answers the re-find
  match and every caller (`detect`, priority, `agent?`) branches on
  truthiness, so the values agree everywhere.
- The trailing `\b` of the small-model pattern is rewritten to the
  explicit `(?:[^A-Za-z0-9_]|$)` guard: regex `\b` never matches on
  the bb runner (PORTABILITY.md). The leading position is unchanged
  (bare `re-find`, no anchor). `(?i)` and the character classes
  behave the same on all three runners — proven by the gate, not by
  reading docs.
- `detect-entries` takes probed `[port base ids]` rows (injected, the
  same seam style as S5d's `:complete` / `:run-local`), so cases pin
  the exact row shape the live fan-out builds (name, base-url,
  normalized models, +100 priority sink, agent? flag).
- No case fixes were needed: all 19 passed the bb single-case run
  on the first try; the gate below is the first full run.

## Runs

`./bin/run-shared-cases basilisp bb nbb` after the final edit:
659/659 pass, 28 negative controls, must-fail 77/77,
`bb`/`nbb` output identical to basilisp. Runners:
basilisp 0.5.1 / bb 1.12.218 / nbb on node v26.3.0.

## Negative controls

- The 2 new must-fail entries (wrong small-rail priority, wrong
  candidate set) are reported FAIL by all three runners, in the bb
  single-case run and in the full gate.
