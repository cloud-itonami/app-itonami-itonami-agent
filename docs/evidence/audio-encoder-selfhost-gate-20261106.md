# The whole native Qwen3-ASR audio encoder on the real weights (JUN-923, 2026-11-06)

Three native guests, built from `itonami.audio-ffn`, run the audio tower of the real
Qwen3-ASR-0.6B `model.safetensors` end to end for 24 synthetic mel frames:

1. `audio_front_cli`: a 128-bin x 24-frame mel input (m[f][t] = (((7 f + 3 t) mod 13) - 6) / 8,
   height = mel, width = time) goes through three 3x3, stride 2, padding 1 `conv2d` blocks with
   480 channels, bias and exact GELU (128x24 -> 64x12 -> 32x6 -> 16x3); the features of time step
   w are c * 16 + h (channel major), 7680 values; `conv_out` (896 x 7680, no bias) projects them
   to 896; the sinusoidal position embedding (dims 0..447 sin, 448..895 cos of p exp(-j ln(10000)
   / 447); positions restart at 0 in each chunk) is added. Three tokens as 43,008 hex characters.
2. `audio_layer_step_cli`, 18 times (JUN-922): one pre-LN encoder layer per process, hex in, hex
   out.
3. `audio_tail_cli`: `ln_post`, `proj1` (896 x 896 + bias), exact GELU, `proj2` (1024 x 896 + bias):
   three 1024-wide embeddings, 49,152 hex characters.

The structure comes from the local llama.cpp checkout: `tools/mtmd/models/qwen3a.cpp` (whose
comment cites the Qwen3-ASR modeling code) and `conversion/qwenvl.py` (sinusoid table, default
`layer_norm_eps` 1e-5). It was not read from the original Python code, and the position table is
float64 here where the model's buffer is float32 (a difference of about 1e-7), so equality with
the original model is not claimed.

Gate: `bin/check-audio-encoder-selfhost <cli> <out-dir> <model.safetensors>`, 18 checks: the sum
and three single values after the front end, after the 18 layers and after the tail (12), plus six
refusals (front without a model path, front with a missing model, tail without arguments, tail
with an input one character short, with a non-hex character, with a missing model; each exits 2
with nothing on stdout). The expected values come from a one-off CPython float64 pipeline of the
same definition (struct/json for the weights, `math.erf`, `math.sin/cos/exp`; 159 s). Measured
agreement (relative, absolute below magnitude 1): front end 1e-15 on the sum and 2.8e-15 to
5.3e-15 on the values; after 18 layers 6e-16 and up to 4e-15; tail 1.9e-15 on the sum and below
1e-16 on the values. The gate tolerance is 1e-11. Timing on this Mac: front end 10 s (about 500
million multiply-adds, 490 MB), 18 layers 10 s, tail 0.4 s. Eleven mutations (mel input, conv
padding, kernel order, conv GELU dropped, feature order, position table, position restart, front
token order, `ln_post` dropped, projector GELU dropped, tail token order) are caught; a source
changed after locking is refused.

Not covered: real audio or a real mel front end feeding it (JUN-869/878 produce the mel frames
natively but are not wired in here), chunking beyond one chunk and the windowed attention of long
audio, the language model, any text output, a node, the agent turn or the launcher. The gate needs
the 1.88 GB model and is not CI-runnable.

Receipt: [audio-encoder-selfhost-gate-20261106-receipt.txt](audio-encoder-selfhost-gate-20261106-receipt.txt).
