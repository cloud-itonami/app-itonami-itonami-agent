# Itonami cron prompt preamble native selfhost gate — 2026-11-01

This ports the pure scheduled-job safety preamble from
`src/itonami/agent/cron.cljk` to canonical Kotoba. The host still resolves the
job's delivery lane (default `local`) and passes it to the formatter. No
profile, scheduler state, script, tool, model, or network operation is read or
invoked here.

## Pinned inputs

- Itonami branch: `codex/awai-60-pr157`.
- `kotoba/itonami/cron_hint.kotoba`, SHA-256
  `3b415c6bed30af0c8a89a6165dc4319fe68dc13b91c603ff8e97c76907192922`.
- Amu PR #1764 candidate commit:
  `d673111b66f43a8569e545c772a70e51737e1707`.
- Amu CLI SHA-256:
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.
- Host: macOS Apple Silicon.

## Reproduction

```sh
bin/check-cron-hint-selfhost \
  /Users/junkawasaki/github/workspaces/codex/kotoba13-project-artifact-replay-repin-1/cli/amu-cli.macho \
  /Users/junkawasaki/github/workspaces/codex/awai60-cron-hint-selfhost-gate-2
```

The locked project checks both `local` and a routed lane, verifies the safety
markers and output boundary, and runs the entry through direct native
execution, source-locked `amu run ENTRY`, and `amu run PROGRAM` replay. It also
requires changed-source refusal. The gate passed on 2026-11-01 with:

- Gate module SHA-256: `9468d62581fa6de759b83b04c36e5b156b6ce6457b784a503a71c21f9778746a`.
- Source lock SHA-256: `81916dc4f397d6559e573b170acb3cbf5a7f9e07fcbafae26383db54c48d2110`.
- Native program SHA-256: `0528b00c5e746116fe323c7855634519d095bfa7af6841404ef05f22aec16dae`.
- Direct result, locked run result, and artifact replay result: `1`.
- Changed locked source: refused with `EINPUT a locked project source`; no output artifact was left.

## Qualification limits

This is a bounded pure string-formatting source port. It does not connect the
formatter to `build-prompt`, qualify cron scheduling/state/effects, execute an
agent turn, or qualify the product launcher. The Amu CLI is an unmerged
candidate; this is not merged required-path evidence. No legacy oracle was used.
