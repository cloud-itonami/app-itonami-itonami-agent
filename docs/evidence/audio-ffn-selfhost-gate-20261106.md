# Native audio-encoder feed-forward sublayer on the real weights (JUN-916, 2026-11-06)

`kotoba/itonami/audio_ffn.kotoba` computes, for one 896-wide token,

    out = x + fc2(gelu(fc1(LayerNorm(x))))       (every projection with its bias)

with the real `thinker.audio_tower.layers.N` tensors of Qwen3-ASR-0.6B: `final_layer_norm`
weight and bias (896), `fc1` (3584 x 896) and bias, `fc2` (896 x 3584) and bias. Weights stay in
the safetensors file and are read one row at a time with FS2 E requests (`itonami.weights-file`
reads the header); activations are internal `vector-f64` arrays. LayerNorm uses eps 1e-5 (the
torch default; `config.json` names no eps) and the exact GELU (`itonami.gelu-erf`). The input
token is x[i] = (((3 i + 1) mod 11) - 5) / 4.

Gate: `bin/check-audio-ffn-selfhost <cli> <out-dir> <model.safetensors>`, 11 cases, one process
per case: layers 0 and 5, the sum of the 896 outputs and outputs 0, 447, 895, plus an index past
the end, a layer that does not exist and a missing file returning -1. Expected values come from a
one-off CPython float64 computation of the same sublayer (struct/json for the weights, `math.erf`
for GELU; a reference measurement, not part of the gate). The native result must agree to
1e-12 relative (absolute below magnitude 1). Measured: output 0 of layer 0 is bit-identical to
CPython; the two sums differ by 18 and 54 ulps (about 1e-14 relative), because erf and exp are
series here. Nine mutations (eps, variance divisor, gamma/beta swapped, bias dropped, identity
activation, residual, input vector, row stride, fc1 weights used for fc2) each leave only the
three refusal cases passing; a source changed after locking is refused.

Receipt: [audio-ffn-selfhost-gate-20261106-receipt.txt](audio-ffn-selfhost-gate-20261106-receipt.txt).

Findings for the Amu seed gaps in JUN-876:
- `vector-f64-new` takes its elements (`(vector-f64-new)` is empty), `vector-f64-conj` appends,
  `vector-f64-at v i`, `vector-f64-count`, `vector-f64-assoc v i x`, `vector-f64-get v i default`
  (three arguments). They work in private functions and in `let`, with `:vector-f64` parameter
  types, under `--source-lock`; a plain single-file `amu run` reports EABI.
- There is no collector in the native runtime: a fourth evaluation of the sublayer in one process
  traps (exit 133) while two succeed. The gate therefore runs one process per case. Building the
  vectors by `conj` allocates a lot; a pre-sized vector would need a constructor the seed does not
  have.
- `E6015 a multi-module compile needs pinned inputs` again came from unbalanced parentheses in
  the gate source (found with a paren-depth check), not from the compiler's limits.

Not covered: the attention half of the layer, the conv2d front end, positional embedding,
chunking, the projector into the language model, any layer other than 0 and 5, the decoder, the
Qwen3-TTS files, a node. This does not show equality with the original model implementation
(eps and the order of operations are the standard ones, not read from the model's code). The
gate needs the 1.88 GB model and is not runnable in CI.
