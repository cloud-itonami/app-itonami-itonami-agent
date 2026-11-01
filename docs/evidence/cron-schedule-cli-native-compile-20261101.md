# Cron schedule CLI native compile — 2026-11-01

This records a bounded source-closure compile for the new Kotoba CLI module.
It is not a claim that the command is wired into the installed launcher or
qualified as a product route.

## Inputs

- Itonami worktree base at compile time: `codex/awai-60-selfhost-only-rule` at
  `50183112f3a2f43accfa2165987f74616f8f9fce`; the CLI source was then
  untracked. Its exact bytes were committed as `83572f8c04489c06b6d91558bea4e03344368f63`.
- `kotoba/agent_cli/cron_schedule_cli.kotoba`, SHA-256
  `d325b316c7359f68c55bc68e32c83c2e2296f04907485c73e3dc7d94d4dad3e2`.
- `kotoba/itonami/schedule.kotoba`, SHA-256
  `a0c492960ccfb054fefa5162553eab923b5bd902ee9b455667c9cd1d8729b2ed`.
- Amu checkout: `kotoba-lang/amu` at
  `a4c16e7c47005d2235dc48d3f69569773a1606e7`.
- Amu `amu-full-root.macho` SHA-256
  `dd71f3940267f55c3864b68010c557859bfa122caad8f579b550b5f1d2988240`;
  its checked-in root descriptor classifies it as `diagnostic-candidate`.
- KSL2 source lock SHA-256:
  `4f477777087ac618ec9e3caabd23ca463f607d356d136725daa657959f9bf10e`.

## Command and result

The KSL2 lock mapped these exact source digests to `/src` paths and declared
the CLI entry module, `itonami.schedule`, and a probe module. With FD 3 bound
to the scratch project directory:

```sh
amu-full-root.macho compile /src/cron_schedule_probe.kotoba \
  --target aarch64-macos --output cron-schedule.kseed \
  --source-lock schedule.ksl --source-lock-sha256 <KSL2-SHA256> 3< .
amu-full-root.macho extract-native cron-schedule.kseed \
  --symbol main --output cron-schedule.raw 3< .
```

Compile returned `ok true`; output was 29,193 bytes, SHA-256
`6e8aaa10988c3d8494b0626a01d33fca6d212efbbc3fcb3e3b6f702a4ebfbce3`.
Extraction returned `ok`, offset `29064`, arity `0`; raw output SHA-256
`9b10ebade0522f4aa7fc5cc0801f7067a7604cdca1b1da02a8db6d9e3778c343`.

## Limits

This proves the CLI source and its schedule dependency compile together under
the diagnostic full-root. The KSL2 lock pins copied file bytes, not the
owning Kotoba repository revision. The guest was not invoked; capability
grants and CLI output were not runtime-checked. The launcher has no dispatch
for this command yet, and no generated guest artifact was installed. Natural
language and relative/ISO one-shot schedule forms remain unsupported by this
slice. No test suite or legacy oracle was run.
