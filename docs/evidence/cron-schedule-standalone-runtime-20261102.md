# Cron schedule standalone artifact runtime check — 2026-11-02

## Artifact identity

- Build receipt: `workspaces/codex/awai-60-cron-standalone-native-20261101/build-receipt.txt`.
- Pinned Itonami source revision: `7460ab3c2f4cca5d37dfb0deb16c6f1edbb077e9`.
- Amu CLI candidate: `d673111b66f43a8569e545c772a70e51737e1707`.
- Standalone macOS arm64 program SHA-256:
  `bb48d5426e48a610045b310f978d21359dd431cb7b7667da44d5a978b112adac`.
- Strict codesign verification passed before invocation.

## Runtime results

- Input `every 5m`: process status `0`, stdout `interval 5` then guest result
  `0`, stderr empty.
- Input `not-a-schedule`: guest refusal marker `2` on stdout and
  `cron: unsupported or invalid schedule: not-a-schedule` on stderr. The
  standalone host process status is `0`; the guest returns its i64 result
  separately from the host process exit status.
- Reproduction receipt and captured streams:
  `workspaces/codex/awai-60-cron-standalone-runtime-20261102/`.

## Qualification boundary

This directly exercises the previously built artifact against its recorded
digest and source provenance. The current checkout's
`bin/check-cron-schedule-selfhost` refuses before rebuilding because the
current schedule source digest is `e7be58fae63535c09e25758bbfa6bb80addeb28a1f0556e5f60d59ec961c5758`,
which differs from the artifact's pinned digest
`720b216c867eec90e33249d47ded08c8fcc685c3ab216e8a1c3a438389c174e5`. This
result does not qualify a rebuild from current source. The launcher already
contains `cron schedule` dispatch; no launcher change was made in this check.
Package installation, release, and live product behavior are not implied.
