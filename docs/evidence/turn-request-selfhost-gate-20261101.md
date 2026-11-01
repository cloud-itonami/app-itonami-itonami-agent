# Itonami turn-request native selfhost gate — 2026-11-01

This gate compiles and executes the pure Kotoba tool-request parser with a
locked multi-module Amu project. Its `test-tool-request` covers accepted
read-only tool forms, malformed requests, unknown tools, empty arguments,
extra separators, and line breaks. This does not execute a model turn, invoke a
tool, or qualify the Itonami product launcher.

## Pinned inputs

- Itonami branch: `codex/awai-60-pr157`.
- `kotoba/agent_cli/turn_request.kotoba`, SHA-256
  `ea462c44a18b2b690911fd4b8decebe3610dd3df502ec348b6b82f3653ecbe8c`.
- Amu PR #1764 candidate commit:
  `d673111b66f43a8569e545c772a70e51737e1707`.
- Amu CLI SHA-256:
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.
- Host: macOS Apple Silicon.

## Reproduction

```sh
bin/check-turn-request-selfhost \
  /Users/junkawasaki/github/workspaces/codex/kotoba13-project-artifact-replay-repin-1/cli/amu-cli.macho \
  /Users/junkawasaki/github/workspaces/codex/awai60-turn-request-selfhost-gate-1
```

The gate pins both source files in KSL2, compiles a native Mach-O, and checks
direct execution, source-locked `amu run ENTRY`, and project-artifact replay.
It also changes the staged source without changing the lock and requires Amu to
refuse with `EINPUT a locked project source` without leaving an artifact.

## Qualification limits

This establishes native execution evidence for the pure request parser only.
It does not resolve the canonical typed wire-11 request/result ABI, turn
execution, filesystem effects, loaderless host integration, or public CLI
dispatch. The Amu CLI is an unmerged candidate, so the evidence is not yet from
the merged required path. No CLJK, EDN, JVM, Node, Python, or model oracle was
used in this gate.
