# Kotoba cron schedule comparison with the CLJK oracle — 2026-11-02

This record compares two existing one-time CLJK oracle vectors with the
standalone Kotoba schedule artifact. It does not make the oracle a required
build/runtime/CI dependency and does not establish general parser equivalence.

## Pinned inputs

- Existing oracle record: `cron-schedule-cljk-oracle-20261101.md`.
- CLJK source revision: `2eb36c00cc21c02ede813719b3fccf8d3f164e2c`.
- Oracle wrapper/tool versions from that record: `kotoba 0.6.29`, NBB
  `1.5.212-cljk.1`, Node `v26.0.0`.
- Kotoba build source revision: `1bd19035d61811d3a87bdb118050fd81f18ec218`.
- Kotoba CLI source SHA-256:
  `92d8a25d028e63b8486022eb91984c3e7097278537f3b2e9cef277290b630dd4`.
- Schedule source SHA-256:
  `720b216c867eec90e33249d47ded08c8fcc685c3ab216e8a1c3a438389c174e5`.
- Amu selfhost CLI revision: `d673111b66f43a8569e545c772a70e51737e1707`;
  CLI SHA-256 `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.
- KSL2 source lock SHA-256:
  `ef228521887f97d1bb93aa90d617bd5dc0f76dd6c6f3e6fe8c629694c129ca35`.
- Standalone arm64 Mach-O SHA-256:
  `bb48d5426e48a610045b310f978d21359dd431cb7b7667da44d5a978b112adac`.
  Strict codesign verification passed.

## Results

| Input | CLJK normalized result | Kotoba normalized result | Result |
| --- | --- | --- | --- |
| U+00A0 + `3h` + U+00A0 | interval, 180 minutes | `interval 180` | Match |
| `monday, mon at 9am` | `0 9 * * 1` | `cron 0 9 * * 1` | Match |

The native CLI prints the normalized result followed by guest result `0`;
comparison above concerns the normalized schedule value.

## Limits

Only these two recorded inputs were compared. This does not qualify other
schedule forms, next-run/time-zone behavior, Hermes `jobs.json` read/modify/write,
locking, cron execution, the full Itonami package, or profile-aware turns.
The Itonami launcher uses the native artifact for the bounded `cron schedule`
route and fails closed if that artifact is missing; no legacy runtime fallback
is part of this measurement.
