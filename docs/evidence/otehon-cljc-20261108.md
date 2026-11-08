# Otehon .cljc CLI second slice — measured runs, 2026-11-08

Linear: JUN-1008 (append requested; body unopened from this host — no Linear
CLI/token here, web fetch returns a JS shell only). Path: Otehon / oracle
(ADR 0026 D18). Not a Native gate, build, packaging, or fallback.
Host: macOS (Darwin, arm64). Work: tick/turn-gate/run-plan/store-plan as
pure `.cljc` data-plane (decide without doing; side-effects :none).

## Tools

| runner | version | how obtained |
|---|---|---|
| Basilisp | 0.5.1 on CPython 3.14.7 | `~/.local/bin/basilisp` (existing) |
| Babashka | 1.12.218 | `~/.local/bin/bb` (existing) |
| nbb | 1.4.208 on node v26.0.0 | Homebrew (existing) |

Same pins as `.github/workflows/oracle-otehon-cljc.yml` (bb 1.12.218, nbb
1.4.208, basilisp 0.5.1).

## What changed

- New pure namespaces: `tick` (5-field cron parse, next fire times, due),
  `store` (import/export copy plan), `turn` (one-turn `-z` gate), `run`
  (real-run plan: prompt assembly, output doc, jobs.json record update).
- `io` gains host branches only: `now-parts`, `dir?`, `read-dir-names`.
  No other namespace carries `#?` (owner chose expanded host surface;
  kept aggregated in `io.cljc` for auditability).
- CLI: `tick`, `turn-gate`, `run-plan`, `store-plan`. Exit 0 ok/planned,
  1 refused/errors, 2 held, 64 usage.
- Shared cases 40 -> 60 (24 -> 27 negative controls); must-fail 4 -> 5.

## Shared cases

```
otehon/bin/run-shared-cases basilisp bb nbb
```

```
PASS  basilisp cases: {:cases 60, :fail [], :negative-controls 27, :pass 60, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 5/5 reported FAIL
PASS  bb cases: {:cases 60, :fail [], :negative-controls 27, :pass 60, :runner "babashka 1.12.218"}
PASS  bb must-fail: 5/5 reported FAIL
PASS  nbb cases: {:cases 60, :fail [], :negative-controls 27, :pass 60, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 5/5 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
exit 0
```

Inputs: `otehon/test/cases.edn`, `otehon/test/must-fail.edn`,
`otehon/test/fixtures/{itonami-ok,hermes-ok}`.

(Inputs and raw log: the shared-case run of 2026-11-08, three runners.)

## Code mutation (negative control on the new core)

Applied to a throwaway copy of `otehon/` (`:due [...]` forced to `[]` in
`tick/tick-plan`), then `bin/run-shared-cases bb`:

```
FAIL  bb cases
:tick/once-spent-when-past FAIL
:neg/tick-past-next-run-at-is-due FAIL
```

Only the two due-sensitive cases fail; the other 58 still pass. The new
cases can tell a broken tick from a working one. (An earlier malformed
mutation broke file load instead of semantics and was discarded.)

## Live commands (byte-identical across runners but for :now)

```
bin/itonami-otehon <r> tick test/fixtures/itonami-ok                    # exit 0
bin/itonami-otehon <r> turn-gate test/fixtures/itonami-ok hello --capability read_source      # exit 0
bin/itonami-otehon <r> turn-gate test/fixtures/itonami-ok hello --capability publish_external # exit 2 held
bin/itonami-otehon <r> run-plan test/fixtures/itonami-ok digest-tick --capability read_source # exit 0 script/pending
bin/itonami-otehon <r> store-plan --direction export --from test/fixtures/itonami-ok --to <empty>       # exit 0
bin/itonami-otehon bb store-plan --direction export --from test/fixtures/itonami-ok --to <non-empty>    # exit 1 refused
bin/itonami-otehon bb store-plan --direction export --from test/fixtures/itonami-ok --to <non-empty> --force # exit 0
```

Sample (`tick`, `<r>` = basilisp/bb/nbb give the same but for `:now`):

```
{:due [], :next {"0f9e8d7c6b5a" {:day 9, :hour 9, :minute 0, :month 11, :year 2026}, "a1b2c3d4e5f6" {:day 8, :hour 18, :minute 17, :month 11, :year 2026}}, :now "2026-11-08T12:45:32+09:00", :profile "itonami-ok"}
```

## Remaining (next slices)

Real execution stays host-side and unwritten: pre-run script spawn,
murakumo rail call, `cron/output/` save, jobs.json apply, monitor sources,
delivery lanes, executions ledger. This slice returns the data the host
needs (`:prompt`, `:output-doc`, `:output-path`, `:record`, `:record-next`,
`:copied`); the host applying them is the next unit.
