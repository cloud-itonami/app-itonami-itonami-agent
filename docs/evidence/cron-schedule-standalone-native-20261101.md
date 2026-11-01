# Cron schedule standalone native candidate — 2026-11-01

## Purpose

Build the current Kotoba schedule CLI as a standalone native application
artifact through the Amu-native project CLI. This avoids the C `kexe-loader`
used by the existing `bin/itonami-agent` launcher contract. It is a build
candidate only; it is not wired into the launcher or installed in a product
package.

## Pinned inputs

- Itonami source revision: `7460ab3c2f4cca5d37dfb0deb16c6f1edbb077e9`.
- `kotoba/agent_cli/cron_schedule_cli.kotoba` SHA-256:
  `92d8a25d028e63b8486022eb91984c3e7097278537f3b2e9cef277290b630dd4`.
- `kotoba/itonami/schedule.kotoba` SHA-256:
  `720b216c867eec90e33249d47ded08c8fcc685c3ab216e8a1c3a438389c174e5`.
- Amu-native CLI candidate from open PR #1764, commit
  `d673111b66f43a8569e545c772a70e51737e1707`, SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.
- KSL2 lock SHA-256:
  `a81fa30645c8a9df17ec2f473e9b54e3f8d1a2f958e931f9829f7a71266ad98f`.

## Build result

`bin/build-cron-schedule-cli-selfhost` compiled the locked source project to a
standalone macOS native program. The Amu CLI returned success and
`codesign --verify --strict` passed. Program size is 133,152 bytes; SHA-256 is
`bb48d5426e48a610045b310f978d21359dd431cb7b7667da44d5a978b112adac`.

Reproduction:

```sh
bin/build-cron-schedule-cli-selfhost \
  /Users/junkawasaki/github/workspaces/codex/kotoba13-project-artifact-replay-repin-1/cli/amu-cli.macho \
  /Users/junkawasaki/github/workspaces/codex/awai-60-cron-standalone-native-20261101
```

The program and `build-receipt.txt` are in the task workspace shown above.
The builder refuses other Amu CLI digests, changed source digests, existing
output directories, and output paths outside the canonical `github/workspaces`
root.

## Qualification boundary

Amu PR #1764 is open and unmerged, so this is candidate-dependent evidence.
The program was compiled and signature-checked, but not invoked. Output/exit
parity, ARM/Linux artifacts, product packaging, launcher dispatch, scheduler
persistence, and profile-aware product behavior remain unqualified. Do not
present this candidate as deployed or production-ready.
