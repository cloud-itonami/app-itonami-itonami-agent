# Verification record — S6b-12 handle-request gates + message-text — 2026-11-10

JUN-1036 S6b-12: the pure head of `itonami.agent.code-rpc/handle-request`.
`code_rpc.cljc` gains `refusal` (allow-list and budget gates as
`tool-error` strings, nil when the call may proceed — the counter is a
plain value, refusals never consume budget or log), `terminal-args`
(terminal blocked-param stripping; other tools pass through, non-map
terminal args become {}) and `message-text` (exception message or its
printed form), with `:code-rpc` verbs, 10 shared cases and 2 must-fail
controls. The async `dispatch-call!` tail (promise branching) and the
serve loop stay host-side (S6).

## Files (this slice only, base 21e52b6; earlier slices alongside untouched)

- `src/itonami/otehon/code_rpc.cljc`: + `refusal`,
  `terminal-args`, `message-text`.
- `src/itonami/otehon/cli.cljc`: `ops-code-rpc` grows the
  `refusal` / `terminal-args` / `message-text` verbs (the latter
  builds its exception op-side via `ex-info`, since exceptions do
  not cross EDN).
- `otehon/COMPAT.md`: S6b-12 entry.
- `test/cases.edn` +10, `test/must-fail.edn` +2.

## Portability notes

- `refusal` returns the `tool-error` JSON envelope
  (`{"error":"..."}`), not the bare message — the first expects
  pinned the bare strings and all three runners rejected them
  identically; the envelope form is green everywhere. This matches
  the legacy, where `handle-request` returns `tool-error` output.
- The allow-list gate precedes the budget gate (cond order pinned
  by `refuse-order`); the available-list joins with `", "`.
- `message-text` is the `(or (ex-message e) (str e))` shape the
  other units already use; `""` stays `""` (empty is truthy, no
  fall-through — pinned by `msg-empty`).
- `finish-call!`'s catch still uses `(str e)`: no EDN value makes
  `json-str` throw, so that path cannot be pinned by a shared case;
  switching it to `message-text` waits for a host-side probe (S6).

## Runs

`./bin/run-shared-cases basilisp bb nbb` after the final edit:
843/843 pass, 28 negative controls, must-fail 94/94,
`bb`/`nbb` output identical to basilisp. Runners:
basilisp 0.5.1 / bb 1.12.218 / nbb on node v26.3.0.

## Negative controls

- The new must-fail entries (`rpc-wrong-refusal`: disallowed tool
  expected nil; `rpc-wrong-term-args`: stripped key expected back)
  are reported FAIL by all three runners.
- Single-runner pre-checks (bb, basilisp: 73 + 9 green, after the
  envelope fix) came before the gate; the gate is the first full
  run.
