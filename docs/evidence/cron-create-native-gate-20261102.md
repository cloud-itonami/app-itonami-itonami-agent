# Kotoba cron create standalone native gate (2026-11-02)

## Scope and result

Exact Itonami PR head `6661cb626713b9265ca568c7915d787c0ef8bee2` passed GitHub Actions workflow [41351760652](https://github.com/cloud-itonami/itonami-agent/actions/runs/41351760652), job `linux-x86_64-native-runtime`. The Python workflow driver only pins/stages sources, invokes Amu, and checks the receipt; cron behavior ran in the Kotoba KX64 payload on the Amu x86_64 Linux static runner.

The guest exposes `create` as a direct artifact action for this check. The user-facing `bin/itonami-agent` create route was not added. This is Linux candidate evidence only; no install, publication, merge, or live cutover is claimed.

## Immutable inputs and derived artifacts

- Itonami source: `6661cb626713b9265ca568c7915d787c0ef8bee2`
- Amu source: `01a2e550f96ac7029b5796cada2e9a00c8e36136`
- Pinned Amu x86_64 compiler SHA-256: `2cbd90970f9766481ae46fd30ae515548e7b3163a9272f7a0497c988258dd654`
- Amu full candidate source SHA-256: `0ecd7bf4dadda3e6932b65095fe0bbdd4500172d138213718f0735f1fcc4a167`
- Amu F1=F2=F3 compiler: 10,306,048 bytes each, SHA-256 `4e4cbb4a66c47ba8277045489e2dd75c28e781a4ffa8a37ed7d3733b5387fc96`
- Runner source SHA-256: `c9f1fe1b5c1748450fb11b233cbdc9d9234880a60dc480c0387b173f60d905be`
- Runner ELF SHA-256: `27349b7edfea5833f41263cdbf6195e5f26848bfb491e406c2d826987194d3b2`
- Kotoba source: `63e30efc6402320fca6f9c70abd93f113e05d99c`
- JCS reader SHA-256: `f8af2953904bd0a96e7b5c3b9e34151bcae3754e5358669adb8ac289acc0872f`
- Hata store I/O SHA-256: `096f6d29d77f374313600a51a18366ee59525c7385456a01218a160904187b10`
- Project source lock SHA-256: `ba5189c77db4308cfd3d66734c3b39e4823abfa70cb91413394943c6d15acefc`
- Project KX64 SHA-256: `a6e7f9329119f81e0805afee185cbb48b1cbe6120cb77284a2681427d5c92e2e`
- Kotoba payload SHA-256: `391dde3056eda5247218e5230448d80f8f432a112692c4bc0dc167a9897abafe`

## Direct runtime checks

- Valid `every 5m`: PASS. Appended exactly one job, generated a 12-character lowercase hex ID, preserved the root extension, set a UTC `updated_at`, and scheduled exactly five minutes later. Guest stdout contained only the success result marker, not the generated ID.
- Invalid schedule: PASS refusal (`:result 2`, diagnostic `cron-store/invalid-create-input`); `jobs.json` remained byte-for-byte unchanged and no temp file remained.
- Empty prompt: PASS refusal (`:result 2`, same named diagnostic); `jobs.json` remained unchanged.
- Existing standalone cron-store list/pause/resume/remove positive/refusal/package checks in the same workflow: PASS.

The exact compiler digest is now pinned in `bin/check-cron-store-linux-native.py`; an exact-head rerun is required and pending. The separate Amu `selfhost-native` workflow `41349582793` is still in progress, so this does not establish Amu PR adoption or public compiler qualification. Launcher wiring remains a later unit after the digest-pinned rerun and Amu gate.

## Reproduction command

The workflow invokes:

```sh
python3 bin/check-cron-store-linux-native.py \
  --output "$RUNNER_TEMP/itonami-cron-store-linux-native"
```
