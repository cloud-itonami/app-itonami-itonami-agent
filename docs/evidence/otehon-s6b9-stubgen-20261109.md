# Verification record — S6b-9 hermes_tools stub generation — 2026-11-09

JUN-1036 S6b-9: the stub-generation tail of `itonami.agent.code-rpc`.
`code_rpc.cljc` gains the `shim-header` / `tool-stubs` data (copied
byte-exact from the legacy, not retyped) and `generate-hermes-tools`,
with a `:code-rpc` verb, 2 full-output shared cases and 1 must-fail
control. Token check, dispatch, budget, log and the serve loop stay
host-side (S6).

## Files (this slice only, base 21e52b6; earlier slices alongside untouched)

- `src/itonami/otehon/code_rpc.cljc`: + `bom-str`, `shim-header`,
  `tool-stubs`, `generate-hermes-tools`.
- `src/itonami/otehon/cli.cljc`: `ops-code-rpc` grows the verb.
- `otehon/COMPAT.md`: S6b-9 entry (module row already partial).
- `test/cases.edn` +2, `test/must-fail.edn` +1.

## Portability notes

- The header is one 4KB string literal containing a `\ufeff`
  escape, which the portable reader set rejects at read time
  (PORTABILITY.md: no `\u` escapes in source, ever). It is spliced
  as `(str "…" bom-str "…")` with `(def bom-str (str (char 65279)))`.
  The BOM is invisible, so this is exactly the case the rule exists
  for — a literal paste would hide it from review.
- Byte-identity to the legacy is proven, not assumed: the legacy
  `shim-header` and `tool-stubs` literals were evaluated
  independently (extracted to throwaway files, read by bb) and
  compared `=` with the port's values — both identical. The case
  expects then pin the port's rendering (header + sorted stubs);
  the generation logic itself (sort, stub shape) is covered the
  same way.
- The `["terminal"]` and `["terminal" "read_file"]` expects are the
  full 4KB/4.3KB module sources — no truncation, no contains-only
  pins. Unknown names fall back to all tools via the already-pinned
  `sandbox-tools-for`.

## Runs

`./bin/run-shared-cases basilisp bb nbb` after the final edit:
793/793 pass, 28 negative controls, must-fail 88/88,
`bb`/`nbb` output identical to basilisp. Runners:
basilisp 0.5.1 / bb 1.12.218 / nbb on node v26.3.0.

## Negative controls

- The new must-fail entry (partial module text as the whole
  expectation) is reported FAIL by all three runners.
- Single-runner pre-checks (bb, basilisp: 23 + 3 green) came before
  the gate; the gate is the first full run.
