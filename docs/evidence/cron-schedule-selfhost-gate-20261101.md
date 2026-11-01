# Cron schedule multi-module native selfhost gate — 2026-11-01

This measurement advances the schedule port from compile/extract admission to
native execution of its pure Kotoba test entry. It is bounded to four existing
schedule test groups. It does not qualify the Itonami CLI launcher, profile
loading, cron persistence, capability wiring, or a live turn.

## Pinned inputs

- Itonami PR #157 head: `1e614c9dbe2c1fc41ed3adbb2648ec9fe916fb5c`.
- `kotoba/itonami/schedule.kotoba`, SHA-256
  `0d69ed31b6601dacdf9c65e3d98c2d44328925ac6cddefa46125db2679fb7cbe`.
- Generated test entry SHA-256
  `10472ed66225f67ac3588cd1c704b4b3ce6950de85ca9441ff12677efb530bfe`;
  first run's generated KSL2 lock SHA-256
  `f8e8755a30f83b639f3daf0312ed3b22a6c0326456ba6b3f62d66fde42b9c458`.
- Amu PR #1764 candidate commit:
  `b16fe2b49237d6418d27864a09e36d17ba289c5d`.
- Amu host CLI SHA-256
  `03edb244e1eefde4193bb5a18e1a7eddd9716e1d2c46d27c5f03dfaa1e42b499`;
  its sibling compiler and packager are pinned and checked by that CLI.
- Host: macOS `26.4`, Apple Silicon.
- The test entry and KSL2 lock were generated under the new output directory;
  the lock maps both source digests and module names, including the schedule
  module.

## Reproduction

Build the Amu CLI candidate from the KOTOBA-13 worktree and copy its pinned
compiler and packager siblings beside it. Then run:

```sh
bin/check-cron-schedule-selfhost \
  /Users/junkawasaki/github/workspaces/codex/awai60-schedule-runtime-test/amu-cli.macho \
  /Users/junkawasaki/github/workspaces/codex/awai60-schedule-selfhost-gate-1
```

The candidate compiled the locked two-module project, signed the generated
native Mach-O, and the generated program returned mask `15`: `test-civil`,
`test-parse`, `test-cron`, and `test-other` all returned true. The program's
SHA-256 is
`9bbe158e6f98bed697785c789d3ba72935f99c1ab8376432b630b3dfa4c41e1a`.

As a negative control, the script changes the staged schedule source without
changing the lock. Compilation refuses with `EINPUT a locked project source`
and creates no output artifact.

## Qualification limits and open route gap

This depends on the unmerged Amu PR #1764 candidate, so it is not yet evidence
from the merged required route. The signed program was directly executed on
the host. `amu run schedule-tests.macho` currently refuses it with
`EABI expected one exported main [] :i64 in a single canonical Kotoba source`
because `run` recompiles the embedded single-file source and cannot replay the
multi-module source lock. Keep this as a KOTOBA-13 integration blocker; do not
claim the `amu run` project-artifact path is qualified.

No Node/NBB/JVM/KBB reference test was run in this measurement. Prior one-time
oracle evidence remains separately recorded in
`cron-schedule-iso-oracle-20261101.md`.
