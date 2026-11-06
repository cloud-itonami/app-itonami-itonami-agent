# Native safetensors file reader gate (JUN-892, 2026-11-06)

`kotoba/itonami/weights_file.kotoba` reads the real Qwen3-ASR-0.6B `model.safetensors`
(1,876,091,704 bytes, sha256 `79d6cbd4…23ea`, from the download manifest) through FS2 range
reads: `F` for the size, `E` (`"<offset>:<count>"`, at most 262,144 bytes per request) for the
8-byte header length, the 74,544-byte JSON header and single BF16 elements. The header is
read by `itonami.safetensors`, an element is decoded by `itonami.bf16`. Nothing but the
requested bytes is read.

Gate: `bin/check-weights-file-selfhost <cli> <out-dir> <model.safetensors>`, 44 checks, run
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
- `E6015 a multi-module compile needs pinned inputs` is what the multi-module compile
  reports for a source it cannot read, including a missing closing parenthesis in a
  module; it does not name the module or the cause. (An earlier version of this note blamed
  the depth of a `+` chain; the later row-dot change reproduced E6015 with one parenthesis
  missing, so the depth explanation is withdrawn.);
- the safetensors header is padded with spaces to an 8-byte boundary, so the closing `}` is
  not the last byte.

Not covered: tensor-sized reads (a 256 KiB loop over a whole matrix), any computation on the
loaded weights, the Qwen3-TTS files, a node, the agent turn or launcher.

## Row dot products (JUN-893)

`wf-row-dot path name row seed` reads one row of a 2-D BF16 weight with a single E request
(at most 131072 columns) and returns its dot product with x[i] = (((i + seed) mod 7) - 3) / 8
as f64 bits, summed in index order. BF16 weights and these x values are exact in f64, so the
CPython sum in the same order matches bit for bit. Three real rows
(`layers.0.fc1.weight` rows 0 and 3583, `layers.1.fc2.weight` row 7) match, and a row past the
end, a 1-D tensor and an unknown tensor return -1.

## Whole matrix-vector product (JUN-915)

`wf-matvec-sum path name seed` parses the header once, reads every row of a 2-D BF16 weight with
one E request each, computes y = W x as above and returns the f64 bits of the sequential sum of
all outputs. On `thinker.audio_tower.layers.0.fc1.weight` (3584 x 896, 3,211,264 multiply-adds)
with seed 3 the sum and three single outputs (rows 0, 1791, 3583) match a CPython computation in
the same order bit for bit; a second, unpinned run with seed 4 matched too. The whole product
takes about 0.5 s as a native artifact. The hot loop uses an integer BF16 -> f64 decoder
(exponent + 896, mantissa shifted to the top, exact decoder for zero and subnormals);
`wf-fast-matches-exact` checks it against `itonami.bf16` on all 65,536 patterns. The first
version used the exact decoder per element, whose repeated halving costs about 0.2 ms per
element in this seed (measured on 2,700 multiply-adds), which would have been about ten minutes
for this matrix.

A mutation that only changes one input (`v >= 32768` -> `v >= 32769`) was behaviour-preserving
(the one value it affects, negative zero, takes the exact path) and was replaced by `v >= 40000`.
