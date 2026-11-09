# Otehon main 3-runner baseline (JUN-1036) — 2026-11-09

Regression baseline on `main` @ `21e52b6` (JUN-1036 S2d #249; includes
S6a #248, S5d units #245/#246). No code changed — verification only.
Otehon / oracle path (ADR 0026 D18), not a native gate.

## Tool versions

- Basilisp 0.5.1 / python 3.14.5 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.3.0)

## Command

```
cd otehon
rm -rf test/scratch
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-09)

```
PASS  basilisp cases: {:cases 530, :fail [], :negative-controls 27, :pass 530, :runner "basilisp 0.5.1 / python 3.14.5"}
PASS  basilisp must-fail: 64/64 reported FAIL
PASS  bb cases: {:cases 530, :fail [], :negative-controls 27, :pass 530, :runner "babashka 1.12.218"}
PASS  bb must-fail: 64/64 reported FAIL
PASS  nbb cases: {:cases 530, :fail [], :negative-controls 27, :pass 530, :runner "nbb on node v26.3.0"}
PASS  nbb must-fail: 64/64 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

530 cases (was 418 in S5c unit-2 evidence): S5d messaging/peer routes,
S6a tool registry core, S2d turn assembly core. Canon outputs
byte-identical across all three runners. Worktree left clean on `main`.

## Follow-ups (not done here)

- Linear status sync for AWAI-401 / JUN-1036 (needs attended auth).
- Live-relay + deploy-gate + prod check for AWAI-218 (needs real network).
