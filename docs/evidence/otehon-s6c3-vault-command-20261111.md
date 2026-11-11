# Verification record — S6c-3 vault command dispatch — 2026-11-11

JUN-1036 S6c-3: the third S6c slice — the `vault` command dispatch
as a `command-plan` in `vault.cljc`. The plan answers `{:list {}}`
for `list` (and for a missing subcommand, like the legacy `case`),
`{:add {...}}` with defaulted origin/totp-seed when label and
username are present, and `{:remove {:handle h}}` when a handle is
present; anything missing its required part answers the verbatim
`{:usage msg}`. The `:vault` verb `command` wired through the
existing `ops-vault`. 8 shared cases + 2 must-fail controls; 1039
cases + 127 must-fail green on basilisp 0.5.1 / bb 1.12.218 / nbb,
canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/vault.cljc`: + `command-plan`.
- `otehon/src/itonami/otehon/cli.cljc`: `:vault` dispatch grows the
  `command` verb.
- `otehon/COMPAT.md`: S6c-3 entry + vault table row updated.
- `test/cases.edn` +8, `test/must-fail.edn` +2.

## Portability notes

- No interop: strings, maps, `or`/`first` destructuring only — no
  `:lpy` branch needed.
- Verified divergences: the gate uses truthiness like the legacy
  (`(and label username)`), so the twin documents rather than fixes
  the boundary. Usage exits, stdin password read, the AES file and
  the row printing stay at the CLI (host-side).

## Runs

- bb 1.12.218: 1039 cases, 0 fail; must-fail 127/127 FAIL (as designed).
- nbb (node v26.3.0): 1039 cases, 0 fail; must-fail 127/127 FAIL.
- basilisp 0.5.1: 1039 cases, 0 fail; must-fail 127/127 FAIL.
- Canon byte-identical across the three runners.
