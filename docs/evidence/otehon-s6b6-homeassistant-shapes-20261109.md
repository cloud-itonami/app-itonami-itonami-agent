# Verification record — S6b-6 homeassistant arg shapes — 2026-11-09

JUN-1036 S6b-6: the pure argument half of `itonami.agent.homeassistant`.
`homeassistant.cljc` ports the entity-id / service validators, the
blocked-domain gate, data coercion, the service payload builder and
the state/service/affected projections, with a `:homeassistant` case
op, 26 shared cases and 2 must-fail controls. The fetch, the JSON
envelopes and the tool dispatch stay host-side (S6).

## Files (this slice only, base 21e52b6; earlier slices alongside untouched)

- `src/itonami/otehon/homeassistant.cljc` (new).
- `src/itonami/otehon/cli.cljc`: require + declare + dispatch +
  `ops-homeassistant` (pure-data verbs, no scratch/env needed).
- `otehon/COMPAT.md`: `homeassistant.cljk` module row + S6b-6 entry.
- `test/cases.edn` +26, `test/must-fail.edn` +2.

## Portability notes

- Throwing guards answer reason strings (nil when fine) instead of
  throwing: shared cases pin refusal behavior as data, with no
  host-dependent ex-message in the comparison. Order is verbatim
  (domain, service, blocked, entity, data).
- `coerce-data` is verbatim, quirks included: blank strings are {},
  other strings go through the JSON parser as-is (scalars pass
  through exactly as the legacy passes them on). Not pinned beyond
  maps: scalar-string payloads are left untested on purpose.
- `project-services` field descriptions degrade to "" when absent,
  verbatim (`get ... "description" ""` at both levels).
- Authoring note (not a divergence): the first shapes-block append
  landed outside the doc form (mid-file `]}]` anchor hit the case's
  own close); the reader read only the first form and the count
  stayed flat. Fixed by moving the close to end-of-file — caught by
  counting, not by the gate.

## Runs

`./bin/run-shared-cases basilisp bb nbb` after the final edit:
739/739 pass, 28 negative controls, must-fail 83/83,
`bb`/`nbb` output identical to basilisp. Runners:
basilisp 0.5.1 / bb 1.12.218 / nbb on node v26.3.0.

## Negative controls

- The 2 new must-fail entries (wrong entity error, wrong payload)
  are reported FAIL by all three runners.
- Single-runner pre-checks (bb, basilisp) were green before the
  gate; the gate is the first full run.
