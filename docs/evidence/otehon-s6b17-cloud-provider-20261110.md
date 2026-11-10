# Verification record — S6b-17 cloud provider shapes — 2026-11-10

JUN-1036 S6b-17: the pure data shapes of the cloud browser plane.
`browser-cloud.cljc` ports the Browserbase / Browser Use request
shaping of `itonami.agent.browser-cloud`: session-name (random
suffix explicit), bb/bu-config (blank counts as unset, base URL
trailing-slash stripped), create payloads (non-positive timeouts
dropped), the single 402 downgrade step (keepAlive, then proxies;
a third call is a no-op — the retry loop stays S6), feature flags,
verbatim session records and close-request specs (method upper-cased,
`{session_id}` substituted). `browser-provider.cljc` ports
`normalize-provider` and `resolve-provider` over an explicit
registry snapshot + availability witness: explicit names win
regardless of availability, unregistered names fall back to
auto-detect, auto-detect walks only the legacy preference list,
otherwise nil for local mode. Names resolve; provider records
(which carry fns) stay host-side. `:browser-cloud` +
`:browser-provider` verbs wired through `ops-browser-cloud` /
`ops-browser-provider` in `cli.cljc`. 24 shared cases + 3
must-fail controls; 918 cases + 105 must-fail green on basilisp
0.5.1 / bb 1.12.218 / nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/browser_cloud.cljc`: new, 10 public fns.
- `otehon/src/itonami/otehon/browser_provider.cljc`: new,
  normalize + resolve.
- `otehon/src/itonami/otehon/cli.cljc`: requires, declares,
  `:browser-cloud` / `:browser-provider` dispatch, both ops fns.
- `otehon/COMPAT.md`: S6b-17 entry + both table rows updated.
- `test/cases.edn` +24, `test/must-fail.edn` +3.

## Portability notes

- `resolve-provider` takes `{name provider}` presence plus a
  `{name bool}` availability witness — the atom and the throwing
  is-available probes stay S6; absent counts as unavailable,
  matching is-available-safe.
- `"Local"` (capital) falls through to auto-detect while `"local"`
  is nil — the legacy compares the trimmed raw string, pinned by
  `resolve-capital-local` / `resolve-local`.
- An explicit-but-unavailable name still wins (the typed "X_API_KEY
  is not set" error surfaces at create time, not at resolution) —
  pinned by `resolve-explicit-unavailable`.
- Sets (`#{...}` dropped flags) compare identically on all three
  runners — pinned by the downgrade cases.

## Runs

- bb 1.12.218: 918 cases, 0 fail; must-fail 105/105 FAIL (as designed).
- nbb (node v26.3.0): 918 cases, 0 fail; must-fail 105/105 FAIL.
- basilisp 0.5.1: 918 cases, 0 fail; must-fail 105/105 FAIL.
- Canon byte-identical across the three runners.
