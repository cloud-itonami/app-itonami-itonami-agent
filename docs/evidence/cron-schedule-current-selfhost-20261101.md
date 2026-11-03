# Itonami cron schedule native selfhost recheck — 2026-11-01

## Inputs

- Itonami PR #157 head: `f2e923ec79898aa4a8efd24d433292707e3552c8`.
- `kotoba/itonami/schedule.kotoba`: SHA-256 `720b216c867eec90e33249d47ded08c8fcc685c3ab216e8a1c3a438389c174e5`.
- `kotoba/agent_cli/cron_schedule_cli.kotoba`: SHA-256 `92d8a25d028e63b8486022eb91984c3e7097278537f3b2e9cef277290b630dd4`.
- Amu public CLI candidate from PR #1764, commit `d673111b66f43a8569e545c772a70e51737e1707`, SHA-256 `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.
- Reproduction command:

```sh
bin/check-cron-schedule-selfhost \
  /Users/junkawasaki/github/workspaces/codex/kotoba13-project-artifact-replay-repin-1/cli/amu-cli.macho \
  /Users/junkawasaki/github/workspaces/codex/awai60-cron-schedule-recheck-20261101
```

## Results

- Schedule test entry direct native execution: mask `15` (all four exported groups returned true).
- `amu run ENTRY --source-lock`: mask `15`.
- `amu run PROGRAM`: mask `15` after locked-source rebuild/artifact comparison.
- Cron schedule CLI direct execution, locked entry run, and artifact run: each returned `interval 5` and exit value `0` for `every 5m`.
- Changed source with stale lock was refused (`changed_source_refusal: PASS`).
- Exact gate receipt: `build-receipt.txt` in the task workspace at the path above. It records source lock SHA `f388db684150c2d3620aea16238a124324b35092a4aec468cf511b68e0c6b8b2`, schedule test program SHA `035c4ad50cb9d69ffc6c36ac2fa8ec56e08ce22cc7b61bcfc3bdc97190c93a01`, and CLI program SHA `fcc52ef56f5e53ce73cca6d5109dba80b99ca0177cf65be00390dd0a7083b5e7`.

## Qualification boundary

This verifies the current schedule Kotoba source and CLI candidate against an unmerged Amu PR #1764 CLI. It does not qualify the Itonami product launcher, guest/loader packaging, profile-aware host, persistence, or live turn. The app entry remains blocked until those paths are built and qualified through the required Amu selfhost route.
