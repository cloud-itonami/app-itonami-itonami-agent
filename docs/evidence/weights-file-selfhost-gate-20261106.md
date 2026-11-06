# Native safetensors file reader gate (JUN-892, 2026-11-06)

`kotoba/itonami/weights_file.kotoba` reads the real Qwen3-ASR-0.6B `model.safetensors`
(1,876,091,704 bytes, sha256 `79d6cbd4…23ea`, from the download manifest) through FS2 range
reads: `F` for the size, `E` (`"<offset>:<count>"`, at most 262,144 bytes per request) for the
8-byte header length, the 74,544-byte JSON header and single BF16 elements. The header is
read by `itonami.safetensors`, an element is decoded by `itonami.bf16`. Nothing but the
requested bytes is read.

Gate: `bin/check-weights-file-selfhost <cli> <out-dir> <model.safetensors>`, 30 checks, run
as an `amu-command-fs35` artifact with the model cloned (`cp -c`) into a directory granted
on fd 3. Expected values come from a one-off CPython `struct`/`json` read of the same file
(this is a reference measurement, not part of the gate): file size, header length, the header
entries of `conv2d1.bias`, `conv_out.weight` and `embed_tokens.weight`, nine BF16 elements as
exact f64 bit patterns (first, one in the middle, last of each), and the refusals (element
one past the end, unknown tensor, missing file) returning -1 without trapping.

Receipt: [weights-file-selfhost-gate-20261106-receipt.txt](weights-file-selfhost-gate-20261106-receipt.txt).
Six mutations of the source are caught (exit status differs from 30; two trap): LE scale,
BF16 byte order, header offset, data-area base, reply-size check, element bound. A source
changed after locking is refused. `amu run` of the same locked source (no FS35 profile) traps
(133) instead of reading.

Facts learned while building it, for the Amu seed gaps in JUN-876:
- a library module declares capabilities as a vector (`[:fs/app-data]`); the set form is
  for the entry module;
- a `:capabilities` guest must be compiled with `--source-lock` (single-file compile gives
  EABI);
- one long chain of nested `+` in a single function (29 checks) made the multi-module
  compile fail with `E6015 a multi-module compile needs pinned inputs`; splitting it into
  two functions fixed it;
- the safetensors header is padded with spaces to an 8-byte boundary, so the closing `}` is
  not the last byte.

Not covered: tensor-sized reads (a 256 KiB loop over a whole matrix), any computation on the
loaded weights, the Qwen3-TTS files, a node, the agent turn or launcher.
