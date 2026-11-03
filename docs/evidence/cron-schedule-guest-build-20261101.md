# Cron schedule native guest build — 2026-11-01

## Scope

This builds the existing Kotoba cron schedule CLI source into an extractable
native guest candidate for the next launcher-integration step. It does not add
the guest to `bin/itonami-agent`, install it into a loader bundle, or qualify
runtime behavior.

## Pinned inputs

- Itonami source revision: `52aef794ab170199eaf551b0df9d646925faa9df`.
- `kotoba/agent_cli/cron_schedule_cli.kotoba` SHA-256:
  `92d8a25d028e63b8486022eb91984c3e7097278537f3b2e9cef277290b630dd4`.
- `kotoba/itonami/schedule.kotoba` SHA-256:
  `720b216c867eec90e33249d47ded08c8fcc685c3ab216e8a1c3a438389c174e5`.
- KSL2 source lock SHA-256:
  `6c2f676e228129e4ad1167ff2820cc809c565e3ba210637ff9fdfc2205861666`.
- Amu selfhost full-root from checkout `a4c16e7c47005d2235dc48d3f69569773a1606e7`,
  SHA-256 `dd71f3940267f55c3864b68010c557859bfa122caad8f579b550b5f1d2988240`.
- Amu multi-module CLI candidate PR #1764 was separately checked and pinned at
  `d673111b66f43a8569e545c772a70e51737e1707`, but it does not expose
  `extract-native`; it was not used for this artifact build.

## Build result

The Amu selfhost root compiled the locked Kotoba project to an aarch64-macos
kseed (`46,819` bytes, SHA-256
`691572e60294f1aafbf3221935c265367801e5baf594b60dc63b32570afb497e`) and
`extract-native` returned `offset 45228`, `length 46738`, `arity 0`. The raw
guest SHA-256 is
`cfbad22624581c219c6e9f060a7db64b62a4a1dbd82338c1f70e3a33c2674fac`.

The raw and kseed outputs remain in the task workspace at
`/Users/junkawasaki/github/workspaces/codex/awai-60-cron-launch-route-20261101/`.
The source lock and outputs were hashed after extraction. The Amu full-root was
invoked with the workspace directory on file descriptor 3 for both compile and
extract operations.

## Qualification boundary

This is compile/extraction evidence only. The guest was not invoked, its
capability grants and stdout/stderr were not runtime-checked, and it is not
installed into `KEXE_GUESTS`. Launcher dispatch, package delivery, schedule
persistence, and profile-aware product behavior remain open. Do not use the
measured offset in the launcher until the guest contract and runtime behavior
are qualified against the intended loader.
