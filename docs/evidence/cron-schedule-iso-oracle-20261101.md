# Cron ISO schedule slice — 2026-11-01

This is one dated CLJK/EDN-era reference measurement for a bounded Kotoba
parser slice. The oracle is not a build, CI, launch, or runtime dependency.

## Scope

`itonami.schedule/parse-schedule-form` accepts a bounded JavaScript-Date ISO
timestamp shape: four-digit year, month/day, `T` time with seconds, and `Z` or
`±HH:MM`. It returns `once-at\n<epoch-milliseconds>` for the native CLI's
intermediate contract. Month, clock, and offset are range checked. Day values
1–31 follow the legacy JS Date overflow behavior (for example, February 30
normalizes to March 2). Fractional seconds are truncated to millisecond
precision. This does not attempt every implementation-defined string accepted
by JS Date; unrecognized forms remain refused. The command is not yet wired to
cron persistence or launcher dispatch.

## One-time reference oracle

- Date: 2026-11-01.
- Source: Itonami `src/itonami/agent/cron.cljk`, at PR source revision
  `3187046f6db1585715848cbd703bf19002c1bd0b`; source SHA-256
  `c8948b0a6f40c916f73c5aca61e666a49bc151d9dd308c73b4de6c8a369cba21`.
- Tools: NBB `v1.4.208`, Node.js `v26.0.0`.
- Inputs (LF-delimited): SHA-256
  `c6ca8ddbd0de62873ec1ecacaaa93a4727d2d53cf55baaf4df3f1fb8e4cc1f3f`.
- Command:

  ```sh
  nbb -cp src -e '(require (quote [itonami.agent.cron :as cron])) (doseq [s ["2026-11-01T09:30:45.123+09:00" "2026-11-01T00:30:45Z" "2026-11-01T00:30:45.1Z" "2026-02-30T00:00:00Z" "2026-11-01T00:30:60Z" "2026-11-01T00:30:45.123456Z"]] (try (println s (cron/parse-schedule s)) (catch :default e (println s "REFUSED"))))'
  ```

- Oracle results in the local `Asia/Tokyo` timezone:

  | Input | Legacy result |
  | --- | --- |
  | `2026-11-01T09:30:45.123+09:00` | once at `2026-11-01T09:30:45+09:00` |
  | `2026-11-01T00:30:45Z` | once at `2026-11-01T09:30:45+09:00` |
  | `2026-11-01T00:30:45.1Z` | once at `2026-11-01T09:30:45+09:00` |
  | `2026-02-30T00:00:00Z` | once at `2026-03-02T09:00:00+09:00` |
  | `2026-11-01T00:30:60Z` | refused |
  | `2026-11-01T00:30:45.123456Z` | once at `2026-11-01T09:30:45+09:00` |

  Corresponding UTC epoch milliseconds for accepted inputs are respectively
  `1793493045123`, `1793493045000`, `1793493045100`, `1772409600000`, and
  `1793493045123`.

## Amu compile evidence

The two Kotoba modules compiled and `cron-schedule-cli` extracted through the
checked-in diagnostic builder, pinned to immutable Itonami source revision
`5745666a15ae2ead1152d1d008467da1959c2656`. Reproduction:

```sh
bin/build-cron-schedule-cli-diagnostic \
  /Users/junkawasaki/github/kotoba-lang/amu/seed/bootstrap/aarch64-macos/amu-full-root.macho \
  /Users/junkawasaki/github/workspaces/codex/awai-60-iso-time-diagnostic-2
```

The compile used these exact KSL2 source hashes:

- `kotoba/agent_cli/cron_schedule_cli.kotoba`:
  `bc9979c8d68b54dc4605dc6c5cf60e08687a53e90a44087915d4d1f4df584fe1`.
- `kotoba/itonami/schedule.kotoba`:
  `0d69ed31b6601dacdf9c65e3d98c2d44328925ac6cddefa46125db2679fb7cbe`.
- KSL2 lock SHA-256:
  `7bc6ab46063c10c8721da5a2d9115930c5434c3cf2809134e0cd1e06af7bc627`.
- Amu checkout `a4c16e7c47005d2235dc48d3f69569773a1606e7`; full-root SHA-256
  `dd71f3940267f55c3864b68010c557859bfa122caad8f579b550b5f1d2988240`.
- The builder receipt is under `workspaces/codex/awai-60-iso-time-diagnostic-2/`.
- Compile returned `{:ok true, :target :aarch64-macos, :bytes 43551}`.
- Extraction returned `{:ok, :offset 41536, :length 43509, :arity 0}`.
- Kseed SHA-256 `c8550516f74cd7dc5b21e7e3ee8375509cee02089e0ea8f54ca7e794ab8e9efe`;
  raw guest SHA-256
  `a8d8a32fe237b960382e7e918e66f5584ba802a47ff9de38589564318d8b7a47`.

This is diagnostic-candidate compile/extraction evidence only. The guest was
not run, so the native parser's values have not been runtime-compared to the
oracle. Launcher dispatch, persisted schedule values, product qualification,
and live runtime remain open.
