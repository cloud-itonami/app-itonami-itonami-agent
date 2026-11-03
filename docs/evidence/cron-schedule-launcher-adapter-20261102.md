# Native cron schedule launcher adapter — 2026-11-02

## Change

The standalone Kotoba artifact prints the guest's i64 return marker after its
own output. Its invalid-input behavior is guest marker `2` on stdout, a named
diagnostic on stderr, and host process status `0`. The launcher previously
passed that runner output through, which did not match the CLI contract.

`bin/itonami-agent` now adapts both `cron schedule` and `cron next-run`:

- guest result `0`: print the command output without the trailing marker and
  exit `0`;
- guest result `1` or `2`: omit marker stdout, retain stderr, and exit with
  the guest result;
- missing artifact remains blocked with exit `78`; malformed result markers
  fail closed with exit `78`.

## Exact-head runtime gate

- Itonami source commit: `fd1d88f9c3ab1fde17b8d460291dd533dc946b21`.
- Standalone macOS arm64 artifact SHA-256:
  `bb48d5426e48a610045b310f978d21359dd431cb7b7667da44d5a978b112adac`.
- `cron schedule 'every 5m'`: PASS; output is `interval 5`, exit `0`.
- Invalid schedule: PASS; empty stdout, named stderr diagnostic, exit `2`.
- `cron next-run 'every 5m' 0 0`: PASS; output is `300000`, exit `0`.
- Invalid next-run schedule: PASS; empty stdout, named stderr diagnostic,
  exit `2`.
- Missing artifact: PASS; exit `78`; legacy kbb sentinel was not invoked.
- Gate receipt:
  `workspaces/codex/awai-60-cron-launcher-exact-head-20261102/launcher-receipt.txt`.

## Qualification boundary

This qualifies the schedule parser and next-run launcher subroutes on this
macOS arm64 artifact. It does not qualify rebuilding from the current
Kotoba schedule source, cron persistence, the complete product package,
installation, publication, or live behavior. The Amu compiler candidate and
artifact provenance limitations in the standalone build evidence still
apply.
