# Awai OS agent placement recon — 2026-11-09

Goal: run itonami-agent + bot profiles ON Awai OS, OVER Awai FS.
Read-only recon; running guests untouched.

## Guest state (serial logs, 2026-11-07 boot, processes still live)

- `awai-167-builder/desktop-candidate-0d1e179.qcow2` (QEMU "Awai OS Combined
  Preview", VNC :78) and `awai-167-cljc-vm` overlay (VNC :79): both reached
  login, NixOS 26.05pre-git aarch64, hostname `awai-otehon`.
- `OTEHON_BOOT_REPORT ... compositor=1 chromium=1 react=1 zenoh=1 matter=1
  gate=1 failed=0` on both.
- otehon pilot mac app running with `otehon-desktop-0.6.0.raw`.

## Blockers for on-OS execution

1. No guest shell access: QEMU has no `hostfwd` ssh, `-monitor none`,
   serial is output-only, VNC is graphical only.
2. No `amu-cli.macho`: `bin/check-storage-selfhost` gate unrunnable.
   `kotoba-lang/amu` is 2.1 GB with no published releases; building is a
   separate task.
3. No Hermes runtime on this Mac (`hermes`/`hermes-agent` absent, no
   hermes python packages): bot profiles (ao-*) cannot run yet.
4. No awai-warm mount here (`~/.juicefs` absent, no juice/fuse/webdav
   mounts): design-doc P0 `fs status` route has nothing to read.

## Pulled this round

- `kotoba-lang/kotoba` @ `92e4a751e` (installable CLI / host impl) into
  `~/github/repos/kotoba-lang/kotoba`.
- Already local: `kotoba-lang`, `itonami-agent` (+ `otonami` otehon
  3-runner green 2026-11-09), `awai-os`, `network-awai-apex`,
  `itonami-registry`.

## Suggested next slices

- A: guest access (ssh hostfwd or vsock shell; or bake agent into image
  via `awai-os`).
- B: evaluate `kotoba` CLI as the agent host on macOS side.
- C: Hermes runtime install for bot profiles (needs model keys).
