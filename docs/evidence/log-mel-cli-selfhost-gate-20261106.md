# Itonami log-mel native guest gate — 2026-11-06 (JUN-869)

`kotoba/agent_cli/log_mel_cli.kotoba` is a standalone native program (macOS
arm64 Mach-O, 83,904 bytes) built with the Amu-native project CLI in the
`amu-command` runtime profile. argv 0 is one frame (1600 hex characters, see
`itonami.log-mel`); it prints the 80 log10 mel energies on one line as
space-separated integers in millionths (round half away from zero) and exits 0.
A missing or malformed frame prints one named line on stderr, nothing on
stdout, and exits 2. Capabilities are exactly `:cli/args`, `:io/write` and
`:io/write-error`.

## Input path finding

The profile has no working `:io/read`: a guest compiled with it traps at run
time (exit 133, measured with a guest that only reads a line and echoes it),
while `:cli/args` plus `:io/write` run fine. So the frame arrives on argv; a
1600-character argument is accepted. The earlier note that frames could also
arrive on `:io/read` lines applies to the loader-based guests, not to this
profile. Window max-8 clamp and (x+4)/4 normalisation are across-frame steps
and stay with the caller.

## Pinned inputs

- Amu-native CLI commit `49dccb68f1a6ef9aece7e9de30bf6224687d06fc`, CLI SHA-256
  `16cbef76292013eae7c2324624ed5d76dd6e890acdba1f3537998d2690bc4896`, with its
  pinned compiler `0df05effcc0624e94403e8aaf1780feab564de1b44b322680f5535582adb6c04`
  and packager `1f7134ea8a6921bcc07b9439b8c54497a4031bf0b67bfcb6464d5fdb000eba77`
  (the CLI refuses with `EPROVENANCE` unless both sit beside it under the names
  `amu-full-root.macho` and `kotoba-packager-root.macho`).
- `kotoba/itonami/log_mel.kotoba`, SHA-256
  `19f892cd437a994661354c3ec90963c3b5ef84ee4885cc157e3a96595049fcd4`;
  `kotoba/agent_cli/log_mel_cli.kotoba`, SHA-256
  `a881e40935e67c8c59a2a848e5567ba23af8f3e02a62e3c2d6de28e6c82c2869`.
- Fixtures: `test/native/log_mel_frame.hex` (the frame) and
  `test/native/log_mel_expected.txt` (reference line, from the one-off stdlib
  f64 script of `log-mel-input-path-20261106.md`, rounded the same way).

## Result (2026-11-06)

```sh
bin/check-log-mel-cli-selfhost <cli-dir> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

The fixed frame's stdout is byte-identical to the reference line, exit 0, empty
stderr, about 0.5 s. No argument, a 1599-character frame and a frame starting
with `z` each exit 2 with an empty stdout and the named stderr line. Negative
controls: removing `:io/write-error` from the declared capabilities is refused
at compile (`E2176 cap-call uses a capability not declared`); a mutated core
(sample offset 32768 -> 32000) prints a different line; a source changed after
locking is refused with `EINPUT a locked project source`. The lock lists
modules sorted by name and files sorted by path (`ELOCK` otherwise).

## Qualification limits

One frame per process. Not covered: a driver that builds frames from audio over
the 3000-frame window (see `window_conv.kotoba`), the cross-frame normalisation,
FFT speed, real audio, a model, the launcher. The output is millionths, not f64
bits. The reference is a Python re-statement of the formulas, not librosa or
whisper. The Amu CLI is an unmerged candidate. No CLJK, EDN, JVM or Node ran in
the gate.

## Update — faster DFT (2026-11-06, JUN-866)

Two changes in `log_mel.kotoba` (SHA-256 `c8fe6a1035f5ef2c7681a3175702f0240c2764eb4db8d8335bfa194311389b69`), outputs unchanged:

1. The direct DFT steps cos/sin of `2*pi*k*n/400` and the Hann cos of
   `2*pi*n/400` by the angle-addition recurrence from per-bin constants instead
   of evaluating a series per sample. Rounding drift over 400 steps is about
   1e-14; the gate still holds 1e-6.
2. The three mel edge frequencies (each an exp or ln series) are computed once
   per mel filter instead of once per (filter, bin) pair.

Measured with `subprocess` wall clock on the guest, fixed frame, macOS arm64,
process start included: **0.1029 s per frame before (PR #173 build, 10 runs
median) and 0.0182 s after (30 runs median, min 0.0178, max 0.0190)**, so 3000
frames take about 55 s sequentially instead of about 309 s. The fixed-frame
output is still byte-identical to the reference line; `check-log-mel-selfhost`
returns 89 with ten mutations caught (two new for the recurrence: rotation sign
6, Hann rotation 6; the old Hann coefficient control now targets the recurrence,
7), `check-nn-ops-selfhost` 38 and `check-attention-selfhost` 50 on the new
core, and all four gate scripts are repinned to the new source digest. The O(N^2)
DFT remains (an FFT needs arrays, JUN-876); real-time is not reached.
