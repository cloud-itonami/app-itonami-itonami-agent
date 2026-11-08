# Otehon pause/status slice (JUN-1036 S3a) — 2026-11-08

Unit: `pause` / `resume` / `status` in `.cljc`, verbatim with the legacy
CLI (same marker text, same status lines, same exit codes), plus the
API/command compat matrix. Internal implementation differs freely (pure
namespaces + a real scratch store in cases); the surface matches.

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-api-compat/otehon
./bin/run-shared-cases basilisp bb nbb
```

`run-shared-cases` also drives a CLI flow on a scratch store
(`ITONAMI_DIR` per runner): `status` → `pause owner check` →
`status` (timestamps normalized) → `resume` → `resume`.

## Result (2026-11-08)

```
PASS  basilisp cases: {:cases 146, :fail [], :negative-controls 27, :pass 146, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 7/7 reported FAIL
PASS  bb cases: {:cases 146, :fail [], :negative-controls 27, :pass 146, :runner "babashka 1.12.218"}
PASS  bb must-fail: 7/7 reported FAIL
PASS  nbb cases: {:cases 146, :fail [], :negative-controls 27, :pass 146, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 7/7 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

146 cases (was 141): 5 new pause/status cases, all green on first
post-fix run. must-fail 7/7 on every runner (was 5/5): the two new
controls prove the scheduler assertion and `was-active` read bite.
Canon outputs byte-identical across runners.

## Negative controls

The pause/status paths have no `:negative` fixture-edit controls: the
legacy commands never refuse these inputs (missing reads as absent,
garbage as not-live), so there is nothing for the automatic control to
catch. Coverage comes from adversarial seeds asserted as positive
cases: garbage pid text, an out-of-int32 pid, an unparsable peers.edn
(`?`), an unnamable profile directory (skipped), a paused store, and a
missing store. The must-fail additions guard the harness itself.

## Notes

- Profile listing is sorted. The legacy order is filesystem order, which
  differs per host readdir; byte-identity across runners wins. Recorded
  in `otehon/COMPAT.md` as a deliberate divergence.
- The pause lift hint keeps the `itonami-agent resume` text verbatim so
  the future rename is a no-op (also in COMPAT.md).
- New files: `otehon/src/itonami/otehon/pause.cljc`,
  `otehon/src/itonami/otehon/status.cljc`, `otehon/COMPAT.md`.
  Extended: `io.cljc` (`write-text!`, `pid-alive?`), `text.cljc`
  (`pad-end`), `cli.cljc` (commands + `:pause`/`:status` ops), cases,
  must-fail, `run-shared-cases`, README.
