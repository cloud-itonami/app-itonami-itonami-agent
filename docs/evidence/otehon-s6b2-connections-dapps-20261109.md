# Verification record — S6b-2 connections/dapps status surface — 2026-11-09

JUN-1036 S6b-2: the read-only connection surfaces. `dapps.cljc`
(registry pin + server table + resolve-checkout + status),
`oauth.cljc` (store-name only) and `connections.cljc`
(connection-status + auth-status) are new ports of
`itonami.agent.dapps` (status half), `itonami.agent.oauth`
(store-name) and `itonami.agent.connections` (slice 1), wired with
`:dapps` / `:connections` case ops, 11 shared cases and 2 must-fail
controls.

## Files (this slice only, base 21e52b6; S6b/S2 files alongside are untouched)

- `src/itonami/otehon/dapps.cljc` (new): `registry` / `servers` /
  `requested?` / `resolve-checkout` / `server-spec` / `status`.
  `with-servers` / `select-servers` stay S6 (need the MCP allowlist).
- `src/itonami/otehon/oauth.cljc` (new): `store-name` only; the
  discovery/registration/token flows stay S6.
- `src/itonami/otehon/connections.cljc` (new): `connection-status` /
  `auth-status` with the `hosted-error` named refusal.
- `src/itonami/otehon/cli.cljc`: require + declare + dispatch +
  `ops-dapps` / `ops-connections` (env = case env or whole process
  env; home = per-case scratch dir).
- `otehon/COMPAT.md`: module rows (dapps/connections/oauth),
  `dapps` CLI route row, S6b-2 slice entry with divergences.
- `test/cases.edn` +11, `test/must-fail.edn` +2.

## Portability notes

- `store-name` clamps the 128-char cut (`subs` to the min length).
  Bare `(subs s 0 128)` throws on bb/JVM for short names but returns
  the string whole on the legacy host (.cljk on Node: JS substring
  semantics, confirmed by the `js/Error` / node-fs interop in the
  legacy sources). The clamp matches the legacy on every runner.
- `str/replace` with the `[^A-Za-z0-9_]` class and `_` replacement
  behaves the same on all three runners (no `$` in the replacement).
- `quot` on `now-ms` milliseconds: expiry edges are far-past (0) /
  far-future (9999999999, good until 2286) / absent / null /
  corrupt / missing, so no case depends on the host clock.
- Seeds are real JSON (`"expires_at": 0`, with the colon — an
  EDN-shaped seed without it fails to parse and reads unreadable,
  which the token-matrix case would catch as a wrong row).
- A configured server answers `mcp` even with `"mcp": false` (the
  flag only admits unconfigured names), verbatim from the legacy
  `or` — pinned by the named-mixed case, which first caught the
  reviewer's wrong expectation, not the code.

## Runs

`./bin/run-shared-cases basilisp bb nbb` after the final edit:
640/640 pass, 28 negative controls, must-fail 75/75,
`bb`/`nbb` output identical to basilisp. Runners:
basilisp 0.5.1 / bb 1.12.218 / nbb on node v26.3.0.

## Negative controls

- The 2 new must-fail entries (wrong cached auth, wrong dapp
  launcher) are reported FAIL by all three runners, in the bb
  single-case run and in the full gate.
- During development the bb single-case run caught 3 real case bugs
  (EDN-shaped JSON seeds, error-map op wrapping, configured-name
  `"mcp": false` semantics), each failing before the fix and passing
  after — the cases bite.
