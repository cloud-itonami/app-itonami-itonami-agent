# Verification record — S6c-6 cron run/tick gates — 2026-11-12

JUN-1036 S6c-6: the sixth S6c slice — the `cron run` and `cron
tick` preconditions as `run-gate` / `tick-gate` data plans in
`cron.cljc`, verbatim from `cron-cmd`. A paused dispatcher refuses
with the resume hint (usage for `run`, the stdout skip line for
`tick`); a missing job refuses with its id; otherwise `{:run
{:job :manual? true}}` or `{:tick {:force? bool}}` for the host to
execute. The `:cron` verbs `run-gate` / `tick-gate` extend the
`ops-cron` wired in S6c-5. 6 shared cases + 2 must-fail controls;
1057 cases + 133 must-fail green on basilisp 0.5.1 / bb 1.12.218 /
nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/cron.cljc`: + `run-gate` /
  `tick-gate`.
- `otehon/src/itonami/otehon/cli.cljc`: `:cron` dispatch grows the
  `run-gate` / `tick-gate` verbs.
- `otehon/COMPAT.md`: S6c-6 entry + cron command row updated.
- `test/cases.edn` +6, `test/must-fail.edn` +2.

## Portability notes

- No interop: booleans, strings and maps only — no `:lpy` branch
  needed.
- Verified divergence: none. The pause state and the job lookup
  arrive as explicit arguments (`paused?`, `since`, `job-id`,
  `job`); the file reads, the run/tick execution and the exit codes
  stay at the CLI (host-side).

## Runs

- bb 1.12.218: 1057 cases, 0 fail; must-fail 133/133 FAIL (as designed).
- nbb (node v26.3.0): 1057 cases, 0 fail; must-fail 133/133 FAIL.
- basilisp 0.5.1: 1057 cases, 0 fail; must-fail 133/133 FAIL.
- Canon byte-identical across the three runners.
