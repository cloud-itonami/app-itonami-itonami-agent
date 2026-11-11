# Verification record — S6c-12 peer command routing — 2026-11-12

JUN-1036 S6c-12: the twelfth S6c slice — the `peer` subcommand
routing as data in `peer.cljc`. `serve-opts` shapes the listener
options (ports prefix-parse, nil-safe, no default or floor);
`rails-add-row` builds one `rails add` row (name defaults to
`manual-<local count>`, models split, priority prefix-parses with a
50 default, any truthy `--small` opts out); `peer-cmd-route` routes
every `peer` subcommand (`serve` / `add` / `trust` / `list` / `run`
/ `rails` sub-route / `exec` / `authorities` / `id`, verbatim
unknown-command refusal). The shared `text/parse-int-prefix`
helper pins the JS prefix-parse shape for command surfaces. The new
`:peer-cmd` op (`serve-opts` / `rails-add` / `route` verbs)
mirrors the `:tick-parse` split: the stub-harness `:peer` op keeps
serving protocol steps. 13 shared cases + 2 must-fail controls;
1127 cases + 145 must-fail green on basilisp 0.5.1 / bb 1.12.218 /
nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/text.cljc`: + `parse-int-prefix`.
- `otehon/src/itonami/otehon/peer.cljc`: + `serve-opts` /
  `rails-add-row` / `peer-cmd-route`.
- `otehon/src/itonami/otehon/cli.cljc`: declare + `:peer-cmd`
  dispatch + `ops-peer-cmd`.
- `otehon/COMPAT.md`: S6c-12 entry.
- `test/cases.edn` +13, `test/must-fail.edn` +2.

## Portability notes

- No interop: `host/replace`, `re-find`, `str/starts-with?`, char
  arithmetic, `str/split`, maps and vectors only — each with
  existing gate precedent on all three runners.
- Verified divergence: one, kept. `peer-cmd-route` keeps the raw
  `--dry-run` value for `rails detect` (any truthy value arms the
  legacy dry path — no boolean normalization). Priority and ports
  read nil where the legacy reads NaN; both stay host-side.
- Incident during the slice: the first draft named the router
  `peer-route`, redefining the internal serve payload helper of
  the same name — the bb gate caught it (`:peer/serve-peer-models`
  arity crash). Renamed to `peer-cmd-route`; existing helper
  untouched. Lesson recorded: grep the twin namespace for the
  name before adding a public defn.

## Runs

- bb 1.12.218: 1127 cases, 0 fail; must-fail 145/145 FAIL (as designed).
- nbb (node v26.3.0): 1127 cases, 0 fail; must-fail 145/145 FAIL.
- basilisp 0.5.1: 1127 cases, 0 fail; must-fail 145/145 FAIL.
- Canon byte-identical across the three runners.
