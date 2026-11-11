# Verification record — S6c-7 cron create/incidents gates — 2026-11-12

JUN-1036 S6c-7: the seventh S6c slice — the `cron create` gate and
the `cron incidents` routing as `create-gate` / `incidents-plan` in
`cron.cljc`, verbatim from `cron-cmd`. The create gate demands a
schedule plus either a prompt or `--no-agent` with a script,
answering the verbatim `{:usage msg}` otherwise; the incidents plan
routes `ack` of a found incident to `{:ack}`, `ack` of a missing one
to its verbatim usage, and any other action to `{:list {}}`. The
`:cron` verbs `create-gate` / `incidents` extend the `ops-cron`
wired in S6c-5. 9 shared cases + 2 must-fail controls; 1066 cases +
135 must-fail green on basilisp 0.5.1 / bb 1.12.218 / nbb, canon
byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/cron.cljc`: + `create-gate` /
  `incidents-plan`.
- `otehon/src/itonami/otehon/cli.cljc`: `:cron` dispatch grows the
  `create-gate` / `incidents` verbs.
- `otehon/COMPAT.md`: S6c-7 entry + cron command row updated.
- `test/cases.edn` +9, `test/must-fail.edn` +2.

## Portability notes

- No interop: strings, booleans and maps only — no `:lpy` branch
  needed.
- Verified divergences: none new. The create gate reads blankness
  like the legacy (`str/blank?` over `str`); the incidents plan
  takes the ack outcome as `found?` so no store read hides inside
  the unit. Job construction, file writes and result prints stay at
  the CLI (host-side).

## Runs

- bb 1.12.218: 1066 cases, 0 fail; must-fail 135/135 FAIL (as designed).
- nbb (node v26.3.0): 1066 cases, 0 fail; must-fail 135/135 FAIL.
- basilisp 0.5.1: 1066 cases, 0 fail; must-fail 135/135 FAIL.
- Canon byte-identical across the three runners.
