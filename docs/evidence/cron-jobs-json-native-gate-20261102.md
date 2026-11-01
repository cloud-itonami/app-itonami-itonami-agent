# Itonami cron jobs JSON adapter — 2026-11-02

This slice adds a Kotoba adapter that replaces or removes the top-level `jobs`
array in a JSON document. It delegates strict structural validation to the
canonical Kotoba JCS reader and splices the replacement into the original UTF-8
bytes. Other top-level members remain byte-for-byte unchanged, including
unknown extension values using decimal or exponent number forms.

## Source pins

- Kotoba JCS source commit: `63e30efc6402320fca6f9c70abd93f113e05d99c`.
- JCS reader source SHA-256:
  `f8af2953904bd0a96e7b5c3b9e34151bcae3754e5358669adb8ac289acc0872f`.
- Amu CLI commit: `d673111b66f43a8569e545c772a70e51737e1707`.
- Amu CLI SHA-256:
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.
- Native artifact SHA-256:
  `833df3511a76a12b16b1fee769563aa3ca1dbf112aa7bf16c0ea89d6aa18956e`.

## Native gate

`bin/check-cron-jobs-json-selfhost` compiled the locked three-module project
with Amu selfhost, verified the artifact signature, and ran it successfully.
The positive case replaced `jobs` while preserving a top-level `1.25e+7`, a
nested `-0.0001`, and an unrelated object. Refusal cases covered malformed
source JSON and a malformed replacement array.

Receipt:
`/Users/junkawasaki/github/workspaces/codex/awai-60-cron-jobs-json-native-gate-20261102-r1/build-receipt.txt`.

## Qualification boundary

The follow-up gate also removes all jobs matching an ID while preserving the
remaining job objects byte-for-byte. The native positive case retains a nested
`-0.0001` extension value and a remaining `1.25e+7` value; a missing ID is
refused. Latest receipt:
`/Users/junkawasaki/github/workspaces/codex/awai-60-cron-jobs-json-native-gate-20261102-r6/build-receipt.txt`.

The standalone artifact now also runs positive/refusal cases for adding and
replacing Hermes-style `updated_at`, and the Kotoba UTC formatter (epoch,
leap-day with milliseconds, and negative-time refusal). A Kotoba store module
compiles against the canonical Hata `store-io` interface and uses the Kotoba
clock capability for timestamps. Latest receipt:
`/Users/junkawasaki/github/workspaces/codex/awai-60-cron-jobs-json-native-gate-20261102-r18/build-receipt.txt`
(exact-head source revision `786839521d4d02983ffb865d986accfd7a50734b`).

Direct runtime rerun on 2026-11-02: strict codesign verification passed and
`cron-jobs-json-gate` exited 0. The executed artifact SHA-256 was
`ceedd706e1f52c5e5443fa7bfc3cae8b75992d0704e5c84d8eecb38aab7fb44e`.
Its entrypoint covers the JSON replace/remove/update-at positive predicates,
malformed JSON / malformed replacement / missing-ID refusal predicates, and
UTC timestamp positive/refusal predicates. The exact command result is recorded
in the adjacent r18 build receipt.

The store's filesystem calls are compiled but not executed by this gate. Full
create/update CRUD, a filesystem runtime gate, and launcher dispatch remain
unqualified. The reader uses the strict JCS profile: valid RFC 8259 documents
with escaped object keys or duplicate keys are refused.
Compatibility with Hermes-generated `jobs.json` files remains to be qualified
before using this adapter for writes.

## 2026-11-02 Linux x86_64 launcher candidate

- Added Kotoba CLI entrypoint `agent-cli-cron-jobs-store` and `itonami-agent
  cron jobs remove <absolute-app-data-dir> <job-id>` for Linux x86_64. The
  launcher grants clock, CLI args, and FS35 scoped to the supplied directory;
  unsupported hosts or missing artifacts fail closed.
- Itonami exact-head run `41340435332`, source commit
  `e021f1e6e6f0c2d14e8eb0f79e7e81172c48aaae`, passed the native fixed-point
  compiler derivation (F1=F2=F3 SHA-256
  `5bae07e401b812dba440c8125ba43c7a74393ddafaec49f6922d094f316477c9`),
  locked Kotoba CLI compile, direct positive/refusal runtime, and launcher
  positive/refusal runtime.
- Compiled KX64 project SHA-256:
  `824bf766bd4eb08a7d2cf58be31da24e877b0f7466c3b820e611b433cd7f69e5`.
  Executable payload SHA-256:
  `89ec32cd179d99718b96bdc9ceebfe76585abc275a530847deeed280b935a565`.
  Runner SHA-256:
  `27349b7edfea5833f41263cdbf6195e5f26848bfb491e406c2d826987194d3b2`.
- Positive launcher result was `:result 0`; it removed the requested job,
  retained unknown JSON extensions, wrote a host-UTC timestamp within 10
  seconds, and left no temp file. Malformed-JSON refusal returned exit 1 and
  `:result 1`, preserved the source bytes, and left no temp file.
- Full build receipt:
  `workspaces/codex/awai-60-amu-cap-clock/itonami-launcher-passed/build-receipt.json`.
- Qualification boundary at the time of this preceding remove-only run: it used
  Amu PR #1768 commit `06b3d1021e6d2d41a1c2c0d22004cb758ca9d631`, whose
  experimental compiler excluded stdout/stderr. That pin was later superseded
  by the exact wire candidate documented below. Darwin clock runtime, public
  Amu compiler/root adoption, and live behavior remain open.

### 2026-11-02 colocated package candidate

- Exact-head run `41340733907` at Itonami source
  `e7f15c692d3f8a778d350badf9a9db7303fc7419` staged and executed a candidate
  package containing the launcher, Amu KX64 runner, Kotoba payload, and
  provenance manifest. The launcher used the adjacent default paths without
  environment overrides.
- Package manifest SHA-256:
  `547270fb9ec27190fe206b79deb3f71590c75eefa84006ab90fc0e99bc60cb43`.
  Launcher SHA-256:
  `6eb0fefa70c2a5696798bd037078bff4d8a6f00767192ddf1b11fd64bc8df81f`.
  Payload and runner digests match the preceding gate record.
- Colocated positive/refusal passed, and the package with missing runtime
  artifacts failed closed with exit 78. Malformed source JSON remained
  unchanged and no temp file remained. The receipt includes all three checks.
- Package files and receipt:
  `workspaces/codex/awai-60-amu-cap-clock/itonami-package-passed/`.
- This is package-candidate qualification. It was not installed or published;
  Amu PR #1768 remains unmerged, and Darwin/live qualification stays open.

### 2026-11-02 exact Amu wire candidate and Kotoba list route

- Exact-head Itonami run `41342885801` at Itonami source `ba7e52feea5ca9f1c2c030da53d2a593ef928b96` pinned Amu source `a7dc736cc4f8f3102c6c49dde0931528f68a83d6`, whose experimental project compiler admits stdout/stderr wires 37/39.
- Amu F1=F2=F3 fixed-point compiler SHA-256: `d8e3078224a512e9ec7dd6693ff6091647ad770d1954fd8c638dee2566e61982` (10,306,048 bytes). The runner source remains SHA-256 `c9f1fe1b5c1748450fb11b233cbdc9d9234880a60dc480c0387b173f60d905be`.
- The locked Kotoba artifact compiled and ran directly: removal positive and malformed-JSON refusal passed. The packaged launcher also passed positive/refusal and missing-artifact refusal (exit 78). The new `list` route passed direct and launcher active-only projection, read-only source preservation, and invalid-job refusal. The positive removal preserved unknown JSON values, wrote a host-UTC timestamp within 10 seconds, and left no temp file.
- KX64 project SHA-256: `4ab9bc0f3e3ea77eda432254724500687604d505fe506764bba98c62e1c28747`; payload SHA-256: `5591320e943d0a875fed3c94dabb10c4ab216801a2c013dc9a79977ab516be66`; candidate package manifest SHA-256: `f733cefe080d181bcd2a1540cf51c1a181e30e2b068dc7f559005b850e938a04`.
- Receipt and package artifacts are retained in workflow run `41342885801` (`itonami-cron-store-linux-native`). This candidate was staged and exercised in CI only; it was not installed or published. Amu PR #1768 is still unmerged; Darwin clock runtime, public compiler adoption, complete CLI, and live behavior remain unqualified.
- Exact-head rerun after pinning the measured compiler digest passed at Itonami source `265175a498e207eaabe54fca3215393e3510cad6` in workflow run `41343189476`. It reproduced the same F1=F2=F3 compiler digest, KX64 project digest, and payload digest, with all direct/launcher positive, refusal, list, and missing-artifact checks passing. This confirms repeatability with the checked-in digest pin; the runtime and publication boundaries above still apply.
