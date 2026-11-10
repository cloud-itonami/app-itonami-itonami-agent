# Verification record — S6b-7 computer-use arg shapes — 2026-11-09

JUN-1036 S6b-7: the pure argument half of `itonami.agent.computer-use`.
`computer_use.cljc` ports canon-keys, blocked-input?, select-window,
build-request, content-text, image-parts, result-error, structured,
error/success-result and action-request (with the ctx-atom seam for
the target state), with a `:computer-use` case op, 31 shared cases and
2 must-fail controls. The driver spawn, capture/list flows, approval
and call dispatch stay host-side (S6).

## Files (this slice only, base 21e52b6; earlier slices alongside untouched)

- `src/itonami/otehon/computer_use.cljc` (new).
- `src/itonami/otehon/cli.cljc`: require + declare + dispatch +
  `ops-computer-use` (action-request builds its ctx from case data).
- `otehon/COMPAT.md`: `computer_use.cljk` module row + S6b-7 entry.
- `test/cases.edn` +31, `test/must-fail.edn` +2.

## Portability notes

- `blocked-input?` answers a boolean (like small-model? in S6b-3:
  the legacy answers the re-find match and every caller branches on
  truthiness). The dangerous-input regex uses only portable classes —
  verified by the gate, not by reading.
- EDN-passed arg maps carry string keys, so the shared-case op passes
  the target state with keyword keys exactly like the legacy
  in-memory state (`{:pid :window-id :tokens}`, int-keyed tokens
  untouched). No boundary conversion in the unit.
- Throws pin as `{:crash message}`: static strings, identical on
  every runner (no host values inside).
- `canon-keys` aliases map alt to option, so Control-Alt-Delete
  reports modifiers `["ctrl" "option"]` — pinned verbatim from the
  legacy table, not as written from the key names.
- `result-error` returns `""` (not the fallback) when the content has
  no text: `or` picks the empty string first, verbatim — the fallback
  is unreachable there, pinned as-is.
- Authoring note (not a divergence): the first single-run harness
  filtered the wrong namespace and reported green on zero cases; the
  gate caught 10 real failures. Count the oks, not the absence of
  FAILs.

## Runs

`./bin/run-shared-cases basilisp bb nbb` after the final edit:
770/770 pass, 28 negative controls, must-fail 85/85,
`bb`/`nbb` output identical to basilisp. Runners:
basilisp 0.5.1 / bb 1.12.218 / nbb on node v26.3.0.

## Negative controls

- The 2 new must-fail entries (wrong canon modifiers, wrong blocked
  verdict) are reported FAIL by all three runners.
- Single-runner pre-checks (bb, basilisp: 31 + 2 green) came before
  the gate; the gate is the first full run.
