# Cron schedule CLI native compile — 2026-11-01

This records a bounded source-closure compile for the new Kotoba CLI module,
reproduced from an immutable Itonami commit. It is not a claim that the
command is wired into the installed launcher or qualified as a product route.

## Inputs

- Itonami source revision: `83572f8c04489c06b6d91558bea4e03344368f63`.
  Both Kotoba source files below were retrieved directly with `git show` from
  this immutable commit before compiling.
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
  `230e01ec1c3c14115e2aeaf50d7424b77d0b49c9c17de742e888a91c99e9765b`.

## Command and result

The KSL2 lock mapped these exact source digests to `/src` paths. The CLI
module itself was the entry module, importing `itonami.schedule`. The
reproducible command is `bin/build-cron-schedule-cli-diagnostic`; it verifies
the full-root digest and writes only to a new directory under `workspaces/`.
It was run as:

```sh
bin/build-cron-schedule-cli-diagnostic \
  /Users/junkawasaki/github/kotoba-lang/amu/seed/bootstrap/aarch64-macos/amu-full-root.macho \
  /Users/junkawasaki/github/workspaces/codex/awai-60-cron-cli-build-script
```

The command returned exit 0 and wrote `workspaces/codex/awai-60-cron-cli-build-script/build-receipt.txt`.
Compile returned `ok true`; output was 29,103 bytes, SHA-256
`4352a11c4cfdc6b2d9c570971ae5690a5908d017ef29697f5ae5a09ca030ecec`.
Extraction returned `ok`, offset `27116`, raw length `29061`, arity `0`; raw
output SHA-256 `e4011b9d613864d485bccc553a79cd5f426cec47c2dabe0ff4ce54bcdb306978`.

## Limits

This proves the pinned CLI source revision and its schedule dependency compile
together under the diagnostic full-root. The guest was not invoked; capability
grants and CLI output were not runtime-checked. The launcher has no dispatch
for this command yet, and no generated guest artifact was installed. Natural
language and relative/ISO one-shot schedule forms remain unsupported by this
slice. No test suite or legacy oracle was run.

## Expanded Kotoba schedule parser — 2026-11-01

The parser was extended in commit `122be6f` to classify interval durations,
relative-duration `once-after`, weekday/natural-time forms, and direct
five-field cron. The diagnostic builder now pins that immutable source commit.

Reproduction:

```sh
bin/build-cron-schedule-cli-diagnostic \
  /Users/junkawasaki/github/kotoba-lang/amu/seed/bootstrap/aarch64-macos/amu-full-root.macho \
  /Users/junkawasaki/github/workspaces/codex/awai-60-cron-parse-expanded-4
```

The command returned exit 0. Source SHA-256 values are CLI
`f55fb591ce6985f65ce5ae94f519c18b910b3913b12c73c318b04df5643534d3` and
schedule `cb303f56cf634834fa423358b21ec203937420c6a565d3353d6ef99ddc301013`.
The KSL2 lock SHA-256 is
`f815493bf5a8c09234a46056d77858a77910de35bfd1f5f8c77ef1732e60b961`.
Amu compiled a 38,655-byte kseed (SHA-256
`3d166995de019b24826a93177a4b129034ccab25bfec3abd0acf0a1cc260fc21`); native
extraction returned offset `36784`, length `38613`, arity `0`, with raw SHA-256
`90708b26c56e34d3a8e1fb6464ef43f3ff68000ff655623c3d650378cf5c3885`.
The pinned Amu root remains diagnostic-candidate. No guest execution,
launcher dispatch, CLI output check, cron persistence, or product runtime
qualification occurred. No test suite or legacy oracle was run.

## Natural weekday canonicalization — 2026-11-01

Commit `e1cec20` deduplicates weekday aliases after mapping them to cron day
values, matching the `distinct` behavior in CLJK `natural-schedule` (for
example, `mon, monday` now yields a single `1`). The diagnostic builder is
pinned to this source commit.

The pinned builder returned exit 0 with KSL2 lock SHA-256
`7409b3825023c79424f8a10e75b03cb25b9e6b0626b05dc48aa4539922d000f1`, CLI
source SHA-256 `f55fb591ce6985f65ce5ae94f519c18b910b3913b12c73c318b04df5643534d3`,
and schedule source SHA-256
`a8e2bcfccc503877775d6eb3917c23ebf5fc4bba3077e06e5e1d6c3504cf506f`. The
39,135-byte kseed SHA-256 is
`ba0986074608d8df7558cd49a4f893b59d6544e177ed830dff5d915dd71fd9c3`; native
extraction succeeded at offset `37264`, length `39093`, arity `0`, raw SHA-256
`836f916ce27d58fdfb67742c3a39b31abf32d39d866b9c25a551c6f225b8df69`.
This remains diagnostic-only. No guest execution, test suite, or legacy oracle
comparison was run.

## ECMAScript edge whitespace — 2026-11-01

Commit `da20ba9` aligns schedule input edge trimming with the explicit
ECMAScript whitespace set used by CLJS `str/trim`. The Kotoba scan uses UTF-8
byte offsets and advances by each code point's encoded width, matching Amu's
byte-oriented substring contract. The pinned builder returned exit 0 with
KSL2 lock SHA-256
`aa32b3662557891ef9babc6fad03c51f3c5eeefcc440f0288d13065623ef5e4a`, CLI
source SHA-256 `f55fb591ce6985f65ce5ae94f519c18b910b3913b12c73c318b04df5643534d3`,
and schedule source SHA-256
`60995b75815fbcc6df58f3abcbfcfd59df2f40b36ad3fd4f429e3d9626b0adcf`. Amu
compiled a 39,175-byte kseed (SHA-256
`1a7c8c89ac3b0f5f3f8e6fd975369d597218894a91cf2fb758aa2ebc9625685b`); native
extraction succeeded at offset `37304`, length `39133`, arity `0`, raw SHA-256
`c056100de5c59ad9b61afaa59feadacd3361545c82c9f1f6f9ee65e9413db494`.
Qualification remains diagnostic-only; no Unicode runtime sample, test suite,
or legacy oracle comparison was run.
