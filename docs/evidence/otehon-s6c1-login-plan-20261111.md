# Verification record — S6c-1 mcp login plan — 2026-11-11

JUN-1036 S6c-1: the first S6c slice — the `mcp login` command gate
and route as data, in `mcp.cljc`. `login-plan` takes the subcommand,
the server name, its `mcp_servers` spec and the device flag, and
answers `{:usage msg}` (verbatim legacy text) unless the subcommand
is `login` and the server names a configured entry; else
`{:login {:server :url :device?}}` for the host to execute (PKCE
loopback or the RFC 8628 device grant). The `:mcp` verb
`login-plan` wired through the existing `ops-mcp`. 6 shared cases +
2 must-fail controls; 1027 cases + 123 must-fail green on basilisp
0.5.1 / bb 1.12.218 / nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/mcp.cljc`: + `login-plan`
  (+ ns docstring S6c note).
- `otehon/src/itonami/otehon/cli.cljc`: `:mcp` dispatch grows the
  `login-plan` verb.
- `otehon/COMPAT.md`: S6c-1 entry + mcp table row updated.
- `test/cases.edn` +6, `test/must-fail.edn` +2.

## Portability notes

- No interop: strings, maps, booleans and `seq` only — no `:lpy`
  branch needed.
- Verified divergence: the legacy gates on truthiness (`(and server
  spec)`), so an empty server name passes the gate and fails at the
  host login; the twin keeps that exact boundary (pinned by the
  `login-empty-server` case) rather than "fixing" it.
- The twin returns `{:usage msg}` where the legacy dies with exit 2;
  the usage exits, the PKCE/device flows and the success print stay
  at the CLI (host-side).

## Runs

- bb 1.12.218: 1027 cases, 0 fail; must-fail 123/123 FAIL (as designed).
- nbb (node v26.3.0): 1027 cases, 0 fail; must-fail 123/123 FAIL.
- basilisp 0.5.1: 1027 cases, 0 fail; must-fail 123/123 FAIL.
- Canon byte-identical across the three runners.
