# Verification record — S6c-10 -main routing — 2026-11-12

JUN-1036 S6c-10: the tenth S6c slice — the routable core of `-main`
in `cli.cljc`. `route-argv` reads raw argv to a routing decision
without running anything: the profile-name gate first (`default`
always passes, anything else must satisfy `valid-name?`), then the
oneshot branch (any truthy `:oneshot` unless the command is `peer`),
then the command case with `gateway` sub-routing. `valid-name?`
(a lowercase token of at most 64 chars, non-strings refuse) is
twinned in `profile.cljc`. The new `route` / `valid-name` verbs
extend the existing `:parse-args` op — no new op: routing is the
continuation of parsing. 12 shared cases + 2 must-fail controls;
1102 cases + 141 must-fail green on basilisp 0.5.1 / bb 1.12.218 /
nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/profile.cljc`: + `valid-name?`.
- `otehon/src/itonami/otehon/cli.cljc`: + `main-commands` /
  `route-argv`, `route` / `valid-name` verbs on `ops-parse-args`.
- `otehon/COMPAT.md`: S6c-10 entry.
- `test/cases.edn` +12, `test/must-fail.edn` +2.

## Portability notes

- No interop: sets-as-functions, `string?`, `re-matches`, maps and
  vectors only — each with existing gate precedent.
- Verified divergence: none. Errors come back as data (`{:usage
  msg}` for the gateway usage line, `{:error msg}` for the die-2
  refusals, `{:help true}` for the help arms); dying, printing,
  profile loading and execution stay host-side.

## Runs

- bb 1.12.218: 1102 cases, 0 fail; must-fail 141/141 FAIL (as designed).
- nbb (node v26.3.0): 1102 cases, 0 fail; must-fail 141/141 FAIL.
- basilisp 0.5.1: 1102 cases, 0 fail; must-fail 141/141 FAIL.
- Canon byte-identical across the three runners.
