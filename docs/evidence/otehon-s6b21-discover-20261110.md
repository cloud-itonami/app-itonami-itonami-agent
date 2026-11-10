# Verification record — S6b-21 mcp discover projection — 2026-11-10

JUN-1036 S6b-21: the pure projection half of MCP discovery, in
`mcp.cljc`. `project-discovery` turns exchange-selected outcomes
into schemas + dispatch index + errors: per-tool schema rows with
the allowlist applied and inputSchema/input_schema normalized,
hermes-native alias rows only when no other server claimed the
name, disabled servers skipped silently, missing results recorded
as exchange errors. The `:mcp` verb `discover` wired through the
existing `ops-mcp`. 5 shared cases + 2 must-fail controls; 946
cases + 114 must-fail green on basilisp 0.5.1 / bb 1.12.218 /
nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/mcp.cljc`: + `project-discovery`
  (+ docstring envelope note).
- `otehon/src/itonami/otehon/cli.cljc`: `:mcp` dispatch grows the
  `discover` verb.
- `otehon/COMPAT.md`: S6b-21 entry + mcp table row updated.
- `test/cases.edn` +5, `test/must-fail.edn` +2.

## Portability notes

- Exchange outcomes are keyword-enveloped (`:error`) while tool
  payloads are string-keyed (`"tools"`, `"name"`): the first
  draft passed keyword tools and the gate rejected all four
  positive cases identically on every runner. The exchange
  sources (stdio `exchange` returns `(get response "result")`,
  failures return `{:error ...}`) prove the mixed convention;
  the docstring records it.
- Error entries keep the legacy string keys (`"server"` /
  `"error"`); the index keeps `[server raw]` vectors.
- Alias detection counts data rows only (errors ride a separate
  key, matching the legacy's separate errors atom).
- Exchange spawn (stdio/HTTP) and call dispatch stay S6.

## Runs

- bb 1.12.218: 946 cases, 0 fail; must-fail 114/114 FAIL (as designed).
- nbb (node v26.3.0): 946 cases, 0 fail; must-fail 114/114 FAIL.
- basilisp 0.5.1: 946 cases, 0 fail; must-fail 114/114 FAIL.
- Canon byte-identical across the three runners.
