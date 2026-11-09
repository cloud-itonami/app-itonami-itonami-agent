# Otehon S6a: tool registry core (JUN-1036) — 2026-11-09

S6a (this doc): the Hermes tool registry core in
`otehon/src/itonami/otehon/tools.cljc` — the `schemas` table (58 tool
schemas), the `toolsets` map, and the pure predicates
(`tool-names`, `unsupported-tools`, `unsupported-toolsets`,
`approval-required?`, `enabled-tool-names`,
`execution-disabled-toolsets`, `runtime-tool-names`,
`tool-schemas`) — ported to `.cljc` behind the same observable API.
The live tool implementations, the host PATH/CLI checks and the
CLI commands stay S6c/d. S6 slices: registry core (a),
clarify/collect/tts-shape/webguard-pure (b), tool implementations
(c), servers/processes + CLIs (d).

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-s6a/otehon
rm -rf test/scratch
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-09)

```
PASS  basilisp cases: {:cases 495, :fail [], :negative-controls 27, :pass 495, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 62/62 reported FAIL
PASS  bb cases: {:cases 495, :fail [], :negative-controls 27, :pass 495, :runner "babashka 1.12.218"}
PASS  bb must-fail: 62/62 reported FAIL
PASS  nbb cases: {:cases 495, :fail [], :negative-controls 27, :pass 495, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 62/62 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

S6a contribution: 8 cases + 5 must-fail (was 487 / 57): toolset
resolution incl. the tool-name-not-broadened rule, full `coding`
and `hermes-webhook`/`hermes-cli` derivations, unsupported
tools/toolsets incl. mcp-prefix allowlisting, approval matrix
(files/cron/computer-use), enabled minus disabled, execution
disabled incl. cron platform defaults, runtime gating (CDP /
browser CLI / computer CLI), exact schema lookup. Canon outputs
byte-identical across runners.

## Verified divergences / fixes during S6a

- `runtime-tool-names` ports only the explicit 5-arity: the 4-arity
  reads the host PATH for the computer-use CLI, which stays
  host-side (the CLI passes its own availability in).
- The `computer_use` schema rides the table as inlined static data
  (the legacy calls `computer-use/schema`, a static literal).
- Step vectors use the flat peer/send convention
  (`["approval", name, args]`, not nested); the harness protocol is
  internal and free.
