# Itonami native `cron next-run` launcher — 2026-11-02

The Itonami launcher now exposes the existing Kotoba/Amu schedule artifact's
next-run calculation as `cron next-run <expression> <epoch-ms>
<utc-offset-minutes>`. The offset is explicit so native callers do not depend
on each host's local timezone.

## Source and artifact

- Launcher source SHA-256:
  `b8d53e7773143d099a040f3611b4201758403179b463cdd742ff6b0cf0c3dbd9`.
- Launcher gate SHA-256:
  `6b8ed4c38e96b3f1c1a3bacfed4a7126748088293af921e6c34c47f4a73c256e`.
- Standalone arm64 Mach-O artifact SHA-256:
  `bb48d5426e48a610045b310f978d21359dd431cb7b7667da44d5a978b112adac`.
- The artifact was built from the pinned schedule sources with Amu CLI commit
  `d673111b66f43a8569e545c772a70e51737e1707`; see
  `cron-schedule-native-oracle-parity-20261102.md` for source and lock hashes.

## Native launcher gate

- `cron schedule 'every 5m'`: positive route passed.
- `cron next-run 'every 5m' 0 0`: returned `300000` milliseconds, followed by
  the guest result `0`.
- Invalid `cron schedule` and `cron next-run` inputs: both returned guest
  marker `2` and the named diagnostic.
- Missing native artifact: `cron next-run` exited `78`; a `itonami-agent-kbb`
  sentinel was not invoked.

The receipt is
`/Users/junkawasaki/github/workspaces/codex/awai-60-cron-next-run-launcher-20261102/launcher-receipt.txt`.
This qualifies the schedule and next-run launcher subroutes only. It does not
qualify Hermes `jobs.json` storage, cron execution, the full product package,
or profile-aware turns.
