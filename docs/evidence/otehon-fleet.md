# Otehon fleet metadata slice — measured runs (2026-11-09)

`peer/fleet-copies` + `peer/replication-plan` (pure) + `fleet-status`
command with `--copies N`. Runners: bb 1.12.218, nbb 1.4.208
(node v26), basilisp 0.5.1 / python 3.14.7.

## Shared cases (in repo, all runners)

`bin/run-shared-cases`: 134/134 pass on bb, nbb, basilisp;
must-fail 5/5; bb/nbb outputs byte-identical to basilisp.
4 new: `fleet-copies-basic` (union with per-sha holders),
`fleet-plan-under/healthy/empty` (composition through both fns,
exactly what the CLI calls).

## Live disk demos (/tmp/fleet-demo, not committed)

Store S (`a.txt`, `b.txt`, `c.txt`); P1 via `peer-push`, P2 via
`peer-pull` (both hold a+b); `c.txt` only in S.
`fleet-status S P1 P2 --copies 2` → `:objects 3`, `:healthy 2`,
`:under [{c-sha :have 1 :want 2}]`.
- Bug found live and fixed: `--copies 2` leaked into `:peers`
  (peer-args knew only --as/--registry). Fixed by teaching peer-args
  to consume `--copies` + value; pure repair/reconcile behavior
  unchanged, suite re-run green after the fix.
- Registry `{"n1" {:prefix "P1"}}`, `--as n1`: S and P2 gate-fail
  (`peer/prefix-mismatch`) → `:skipped` with reasons, never read;
  census runs over admitted P1 only.
- `--as zz`: no admitted peer → refused `fleet/no-peer`, exit 1.
- `--copies xyz` → refused `fleet/bad-copies`, exit 1.

## Contract

- Read-only: fleet-status never writes. Skipped peers are reported,
  never touched. Single-peer fleets are valid (degraded, not refused).
- Default desired copies is 2; `--copies N` requires N ≥ 1.
- What stays open: asymmetric signatures (measured infeasible in pure
  .cljc — AWAI-232), native execution of these planners.
