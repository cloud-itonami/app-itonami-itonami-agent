# Cron schedule multi-module native selfhost gate — 2026-11-01

This measurement advances the schedule port from compile/extract admission to
native execution of its pure Kotoba test entry. It is bounded to four existing
schedule test groups. It does not qualify the Itonami CLI launcher, profile
loading, cron persistence, capability wiring, or a live turn.

## Pinned inputs

- Itonami PR #157 head: `2af95f5c7c384866bc4d814be3b287691d9f778d`.
- `kotoba/itonami/schedule.kotoba`, SHA-256
  `0d69ed31b6601dacdf9c65e3d98c2d44328925ac6cddefa46125db2679fb7cbe`.
- Generated test entry SHA-256
  `10472ed66225f67ac3588cd1c704b4b3ce6950de85ca9441ff12677efb530bfe`;
  first run's generated KSL2 lock SHA-256
  `f8e8755a30f83b639f3daf0312ed3b22a6c0326456ba6b3f62d66fde42b9c458`.
- Amu PR #1764 candidate commit:
  `d673111b66f43a8569e545c772a70e51737e1707`.
- Amu host CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`;
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
  /Users/junkawasaki/github/workspaces/codex/kotoba13-project-artifact-replay-repin-1/cli/amu-cli.macho \
  /Users/junkawasaki/github/workspaces/codex/awai60-schedule-artifact-replay-gate-1
```

The candidate compiled the locked two-module project, signed the generated
native Mach-O, and the generated program returned mask `15`: `test-civil`,
`test-parse`, `test-cron`, and `test-other` all returned true. Then
`amu run ENTRY --source-lock FILE.ksl` rebuilt and ran the same project and
also returned `15`. The program's SHA-256 is
`9bbe158e6f98bed697785c789d3ba72935f99c1ab8376432b630b3dfa4c41e1a`.

## 2026-11-01 Amu project-artifact replay follow-up

Amu PR #1764 commit `d673111b66f43a8569e545c772a70e51737e1707` adds verified
locked-source bundles to multi-module artifacts. Rebuilt the Amu candidate at
CLI SHA-256 `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`
and reran this gate. Direct execution, source-locked `amu run ENTRY`, and
`amu run schedule-tests.macho` each returned mask `15`. The rebuilt program
SHA-256 is `17b78a93128dde3c2f52a80eb10db8e2887cb944e390d604a287092d0c6f4763`;
the source-lock SHA-256 is
`cd31c7f60c65cf25f27d760da417697900147c4178e1d95ccd404c40f94fb1d2`.
The changed-source refusal still passed.

As a negative control, the script changes the staged schedule source without
changing the lock. Compilation refuses with `EINPUT a locked project source`
and creates no output artifact.

## Qualification limits

This depends on the unmerged Amu PR #1764 candidate, so it is not yet evidence
from the merged required route. Both direct execution and the source-locked
`amu run ENTRY` form and multi-module `amu run PROGRAM` artifact replay passed.
Artifact replay recompiles the embedded locked project, verifies its sources,
and compares the rebuilt artifact before execution. This is still dependent on
the unmerged candidate and does not qualify the Itonami product launcher.

No Node/NBB/JVM/KBB reference test was run in this measurement. Prior one-time
oracle evidence remains separately recorded in
`cron-schedule-iso-oracle-20261101.md`.
