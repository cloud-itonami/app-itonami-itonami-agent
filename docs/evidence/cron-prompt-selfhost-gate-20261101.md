# Itonami cron prompt assembly selfhost gate — 2026-11-01

This ports the pure prompt assembly branches from
`src/itonami/agent/cron.cljk`'s `build-prompt` into
`kotoba/itonami/cron_prompt.kotoba`. The host supplies the resolved delivery
lane, already assembled base text, whether a script ran, its success and
output, and the result of the host's `wakeAgent=false` check. Kotoba owns the
prompt strings and branch selection. It does not run scripts or parse JSON.

## Pinned inputs

- Itonami branch: `codex/awai-60-pr157`.
- `kotoba/itonami/cron_hint.kotoba`, SHA-256
  `3b415c6bed30af0c8a89a6165dc4319fe68dc13b91c603ff8e97c76907192922`.
- `kotoba/itonami/cron_prompt.kotoba`, SHA-256
  `f0c01f9fdce7d67e9bfb83c2749c00d4df758b47180765da247d8b6bd9452f99`.
- Amu PR #1764 candidate commit:
  `d673111b66f43a8569e545c772a70e51737e1707`.
- Amu CLI SHA-256:
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.
- Host: macOS Apple Silicon.

## Reproduction

```sh
bin/check-cron-prompt-selfhost \
  /Users/junkawasaki/github/workspaces/codex/kotoba13-project-artifact-replay-repin-1/cli/amu-cli.macho \
  /Users/junkawasaki/github/workspaces/codex/awai60-cron-prompt-selfhost-gate-2
```

The KSL2 project passed direct native execution, source-locked `amu run ENTRY`,
and `amu run PROGRAM` replay; all returned `1`. Tests cover the plain prompt,
successful script output, script failure, empty-output refusal, and
`wakeAgent=false` refusal. Mutating the locked prompt source was refused with
`EINPUT a locked project source` and left no output artifact.

- Gate module SHA-256: `4cddd1fbb6b5abed7e5af0cac80464a5945789ad548415079fa5fa0b383af0db`.
- Source lock SHA-256: `8305730c12c58bb26ed34d4e9efefbee3296a6d08241aaa9c1c896d04b45a181`.
- Native program SHA-256: `d56f632b1fc16b70f3329b70d6c441e5abc134de943bf1eaf140d4aa2cf4dc42`.

## Qualification limits

This is pure Kotoba source and candidate selfhost execution evidence. It is not
connected to the current `build-prompt`, does not parse script JSON or run
scripts, and does not qualify scheduling, profile state, effects, launcher
dispatch, the profile-aware host, a live turn, or the merged required path. No
legacy oracle was used.
