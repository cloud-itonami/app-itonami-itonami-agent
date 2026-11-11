# Verification record — S6c-2 bridge run options — 2026-11-11

JUN-1036 S6c-2: the second S6c slice — the data half of the
agent-loop options `start-request` builds, as a `run-options` plan in
`bridge.cljc`. The plan carries model, provider, the model pin flag,
tool names, history, instructions, cwd (explicit argument; the
legacy defaults to the process cwd), the `api_server` platform, the
load-context flag, max turns and budget seconds. The `:bridge` verb
`options` wired through the existing `ops-bridge`. 4 shared cases +
2 must-fail controls; 1031 cases + 125 must-fail green on basilisp
0.5.1 / bb 1.12.218 / nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/bridge.cljc`: + `run-options`.
- `otehon/src/itonami/otehon/cli.cljc`: `:bridge` dispatch grows the
  `options` verb.
- `otehon/COMPAT.md`: S6c-2 entry + bridge table row updated.
- `test/cases.edn` +4, `test/must-fail.edn` +2.

## Portability notes

- No interop: plain map reads and `boolean`/`or` only — no `:lpy`
  branch needed.
- Verified divergence: none in the data half. The function seams
  (`:before-tool`, `:tool-overrides`, `:on-event`), the profile load
  and the agent-loop run stay host-side; the host cwd default moves
  into the explicit `cwd` argument so no host read hides inside the
  unit.

## Runs

- bb 1.12.218: 1031 cases, 0 fail; must-fail 125/125 FAIL (as designed).
- nbb (node v26.3.0): 1031 cases, 0 fail; must-fail 125/125 FAIL.
- basilisp 0.5.1: 1031 cases, 0 fail; must-fail 125/125 FAIL.
- Canon byte-identical across the three runners.
