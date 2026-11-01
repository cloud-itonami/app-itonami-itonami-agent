# Itonami cron jobs JSON adapter — 2026-11-02

This slice adds a Kotoba adapter that replaces only the top-level `jobs`
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

This still does not implement create/update CRUD, locking, durable filesystem
writes, or launcher dispatch. The reader uses the strict JCS profile: valid RFC
8259 documents with escaped object keys or duplicate keys are refused.
Compatibility with Hermes-generated `jobs.json` files remains to be qualified
before using this adapter for writes.
