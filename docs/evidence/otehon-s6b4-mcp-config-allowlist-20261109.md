# Verification record — S6b-4 mcp config + allowlist + dapps run-wiring — 2026-11-09

JUN-1036 S6b-4: the portable config half of `itonami.agent.mcp` plus
the dapps run-wiring it unlocks. `mcp.cljc` ports enabled? / names-for /
command-for / server-env / sanitize / tool-name / object-schema /
filter-patterns / glob-match? / tool-allowed? with a `:mcp` case op;
`dapps.cljc` gains with-servers / select-servers with `:dapps` verbs.
28 shared cases (22 mcp + 6 dapps, incl. 6 expand edge cases) and
2 must-fail controls.

## Files (this slice only, base 21e52b6; S6b/S6b-2/S6b-3 files alongside are untouched)

- `src/itonami/otehon/mcp.cljc` (new; twin of the config half of
  `mcp.cljk`. `mcp_http_client.mjs`, exchange, oauth-cache-ready,
  discover/call dispatch stay S6).
- `src/itonami/otehon/dapps.cljc`: + with-servers / select-servers.
- `src/itonami/otehon/cli.cljc`: require + declare + dispatch +
  `ops-mcp`; `ops-dapps` grows the two run-wiring verbs.
- `otehon/COMPAT.md`: `mcp.cljk` module row, S6b-4 slice entry with
  divergences.
- `test/cases.edn` +28, `test/must-fail.edn` +2.

## Portability notes

- `expand-string` is a hand scan over `${...}`, not str/replace with
  a match fn: what the fn receives is not the groups vector on every
  runner — on Basilisp the key arrives nil and every variable renders
  `${}` (caught by the gate: bb/nbb went 685, basilisp 687 before the
  rewrite; the 2 failures were command-string and server-env, the only
  cases through expand). Single pass, no rescan of substituted values:
  the legacy replaceAll semantics, pinned by the no-rescan case.
- `glob-match?` builds its regex with `re-pattern` from a plain
  string (the confine.cljc idiom): no regex-literal escaping issues
  on any runner; `*` / `?` / specials behave the same everywhere.
- `tool-name` hashes with `cas/sha256-hex` over `hio/utf8-encode`
  (ASCII in the pinned case; the cross-runner unicode question stays
  with the existing `:cas-sha` coverage, not this slice).
- `with-servers` success path is FIXED, not verbatim: the legacy
  threads `acc` twice (`(update acc ...)` inside `->`), so its
  resolvable-server path always crashes with a 3-arg keyword call —
  only the keep/error branches ever ran there. The port drops the
  stray `acc`; with-resolve pins the fixed behavior.

## Runs

`./bin/run-shared-cases basilisp bb nbb` after the final edit:
693/693 pass, 28 negative controls, must-fail 79/79,
`bb`/`nbb` output identical to basilisp. Runners:
basilisp 0.5.1 / bb 1.12.218 / nbb on node v26.3.0.

## Negative controls

- The 2 new must-fail entries (wrong names list, wrong added set)
  are reported FAIL by all three runners.
- The gate itself caught the expand divergence (685 vs 687) before
  the rewrite and passes after — the harness bites across runners,
  not just within one.
