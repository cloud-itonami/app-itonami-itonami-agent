# Otehon transfer/release slice (JUN-1036 S4c) — 2026-11-08

Unit: `profile refresh-bundle`, `profile release`, `profile import`
and `profile export`, ported verbatim from the legacy CLI (same lines,
same keep/skip/would shapes, same exit codes, same export-parks rule).
Internal implementation differs freely (pure definition walk +
`io/copy-file!`); the surface matches.

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-s4c/otehon
rm -rf test/scratch src/itonami/otehon/__pycache__
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-08)

```
PASS  basilisp cases: {:cases 272, :fail [], :negative-controls 27, :pass 272, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 17/17 reported FAIL
PASS  bb cases: {:cases 272, :fail [], :negative-controls 27, :pass 272, :runner "babashka 1.12.218"}
PASS  bb must-fail: 17/17 reported FAIL
PASS  nbb cases: {:cases 272, :fail [], :negative-controls 27, :pass 272, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 17/17 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

272 cases (was 251): 3 refresh-bundle (ok/refuse/missing),
7 release (keep/released/parked-only/dry/skip/usage/--all),
11 import/export (copied/dry/force-refuse/force/skip/usage/roots/
parked/no-park/usage/--all). must-fail 17/17 (was 14/14: one new
entry per new verb). Canon outputs byte-identical across runners.

## Compat notes (legacy `main.cljk`/`profile.cljk` vs otehon)

- New: `copy-profile!` in `bundle.cljc` (definition files + sorted
  script/skill walks, state never crosses), `set-parked!` +
  release/transfer lines in `cli.cljc`, `copy-file!` in `io.cljc`.
- `import`/`export` execute the legacy `store-plan` copy plan the
  `store` op only describes. Park-on-export fires only for
  Kotoba-scheduled sources, verbatim (asserted by `:parked?`).
- Copy byte-identity is asserted end to end: the target manifest
  text is part of the case contract (`:manifest-file`), and the
  copied rel list is asserted exactly (`:copied`, definition order
  then sorted walk order).
- Release lifts the marker even with no manifest (parked-only
  ownership), flips the manifest scheduler otherwise. The marker
  stamp text is host-clocked and never asserted, only presence.
- Recorded divergences (COMPAT): transfer homes keep `default`
  under `profiles/`; no host-root defaults in scratch (missing
  `--from`/`--to` refuses); release with no name refuses (the
  legacy blank-name keep is a `load-profile(nil)` accident);
  import/export dry-run reports 0 copied, verbatim.

## Incident during the slice (found and fixed before green)

- The import dry-run case expected `1 profile(s) would be copied`,
  mirroring itonamify/refresh/rail/release. The legacy transfer
  body counts only real copies (`swap!` lives in the else branch),
  so dry-run reports 0 — the code was verbatim, the case was
  fixed. This per-verb dry-count difference is now pinned by cases
  on both sides.

## Open (per `otehon/COMPAT.md`)

- S4 remaining: adopt/soul/organize/hy-* (registry + Hy-conversion cluster).
- S5 messaging (ledger/queue writes, gateway/send/deliver/peers/chat).
- S6 tool backends, live rail call.
