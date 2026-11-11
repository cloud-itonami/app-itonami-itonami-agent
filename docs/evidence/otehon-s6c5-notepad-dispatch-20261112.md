# Verification record — S6c-5 cron notepad dispatch — 2026-11-12

JUN-1036 S6c-5: the fifth S6c slice — the `cron notepad`
subcommand dispatch as a `notepad-plan` in `cron.cljc`, verbatim
from `cron-cmd`. The plan answers `{:list _}`, `{:get _}`,
`{:delete _}` and `{:set _}` (job id, key and, for set, value);
anything missing its required part answers the verbatim
`{:usage msg}`. The `:cron` verb `notepad` is new — this also wires
the first `:cron` op through a new `ops-cron`. 8 shared cases + 2
must-fail controls; 1051 cases + 131 must-fail green on basilisp
0.5.1 / bb 1.12.218 / nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/cron.cljc`: + `notepad-plan`.
- `otehon/src/itonami/otehon/cli.cljc`: declare + `:cron` dispatch
  + `ops-cron` with the `notepad` verb.
- `otehon/COMPAT.md`: S6c-5 entry + cron command row updated.
- `test/cases.edn` +8, `test/must-fail.edn` +2.

## Portability notes

- No interop: strings, maps, set membership and `nil?` only — no
  `:lpy` branch needed.
- Verified divergences: the gate uses membership for the verb and
  nil-ness for the set value, like the legacy — `false` passes as a
  set value (pinned by case), and the set refusal reuses the same
  usage text as the dispatch refusal. The notepad file read/write
  and the result printing stay at the CLI (host-side).

## Runs

- bb 1.12.218: 1051 cases, 0 fail; must-fail 131/131 FAIL (as designed).
- nbb (node v26.3.0): 1051 cases, 0 fail; must-fail 131/131 FAIL.
- basilisp 0.5.1: 1051 cases, 0 fail; must-fail 131/131 FAIL.
- Canon byte-identical across the three runners.
