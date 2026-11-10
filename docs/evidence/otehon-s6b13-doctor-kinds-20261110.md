# Verification record — S6b-13 doctor finding units — 2026-11-10

JUN-1036 S6b-13: the pure finding-only heads of the itonami doctor's
host-gated units. `doctor.cljc` gains `toolset-findings`
(toolset-name vs MCP prefix + agent-profile gate), `tool-findings`
(unknown-tool detection as the `{:code :unsupported_tools :detail}`
finder — the veto/headless/OOM/carrier/ssrf shape stays host-side),
`browser-findings` (backend-or-engine non-local, CDP-loopback refutation)
and `oauth-findings` (unready-list reads), with a `:doctor-kinds` case
op. 14 shared cases + 2 must-fail controls; 857 cases + 96 must-fail
green on basilisp 0.5.1 / bb 1.12.218 / nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `src/itonami/otehon/doctor.cljc`: + `toolset-findings`,
  `tool-findings`, `browser-findings`, `oauth-findings`.
- `src/itonami/otehon/cli.cljc`: `ops-doctor` grows the
  `:doctor-kinds` op on `cli/doctor` (verbatim-key dispatch, like
  `:code-rpc` on `cli/code-rpc`).
- `otehon/COMPAT.md`: S6b-13 entry.
- `test/cases.edn` +14, `test/must-fail.edn` +2.

## Portability notes

- `format` / `str` / `when-let` / `string/starts-with?` / `get` /
  `contains?` / `filterv` over vectors are portable — no change.
- `oauth-findings` is `keep` over the names vector (ready names not
  consulted — read of host credential caches stays S6): the first
  expects pinned a detail for the wrong entry and the runners
  rejected it, confirming the ordered-read semantics.
- `:agents?` threaded through `request-id` (5 fields read via
  `(nth fields 3 nil)`-style positional access, same as
  `job-findings`' `(nth fields 3)`); CLI Symptoms/Checks ignore the
  agents column, unit keeps it.
- `tool-findings` is detection-only: role/capability gating is a
  host probe (S6). `browser-findings` covers refutations the doctor
  already performed by hand; the local-Chrome-probe itself stays S6:
  the witness inputs (`backend`/`engine`/`cdp?`) are explicit
  parameters, like the runner-stub witness pattern.
- Diagnosis copy (the `Kotoba browser supports local Chrome only`
  string) lives in the unit; the evidence-table rendering stays S6.

## Runs

- bb 1.12.218: 857 cases, 0 fail; must-fail 96/96 FAIL (as designed).
- nbb (node v26.3.0): 857 cases, 0 fail; must-fail 96/96 FAIL.
- basilisp 0.5.1: 857 cases, 0 fail; must-fail 96/96 FAIL.
- Canon byte-identical across the three runners.
