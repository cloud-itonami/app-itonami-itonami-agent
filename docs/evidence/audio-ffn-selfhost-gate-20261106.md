# Native audio-encoder feed-forward sublayer on the real weights (JUN-916, 2026-11-06)

`kotoba/itonami/audio_ffn.kotoba` computes, for one 896-wide token,

    out = x + fc2(gelu(fc1(LayerNorm(x))))       (every projection with its bias)

with the real `thinker.audio_tower.layers.N` tensors of Qwen3-ASR-0.6B: `final_layer_norm`
weight and bias (896), `fc1` (3584 x 896) and bias, `fc2` (896 x 3584) and bias. Weights stay in
the safetensors file and are read one row at a time with FS2 E requests (`itonami.weights-file`
reads the header); activations are internal `vector-f64` arrays. LayerNorm uses eps 1e-5 (the
torch default; `config.json` names no eps) and the exact GELU (`itonami.gelu-erf`). The input
token is x[i] = (((3 i + 1) mod 11) - 5) / 4.

Gate: `bin/check-audio-ffn-selfhost <cli> <out-dir> <model.safetensors>`, 22 cases (11 feed-forward, 11 attention; see the last section), one process
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

## The self-attention half (JUN-917)

`attn-at path layer idx` and `attn-sum path layer` run three tokens (x_t[i] = (((3 i + 1 + 5 t) mod
11) - 5) / 4, t = 0..2) through

    out_t = x_t + out_proj(ctx_t),   ctx_t = concat over 14 heads of softmax_u(q_t . k_u / 8) v_u

with q, k, v the biased projections of `self_attn_layer_norm(x)` and the softmax over all three
tokens (non-causal), using the real `self_attn.{q,k,v,out}_proj` weights and biases and the
`self_attn_layer_norm` of layers 0 and 5. idx runs over the 2688 outputs (token idx / 896).
The gate has 22 cases now (11 feed-forward, 11 attention: the sum and outputs 0, 1000, 2687 for
both layers, an index past the end, a missing layer, a missing file); they agree with a CPython
float64 computation of the same function to 1e-12 relative. Eight further mutations (scale, head
count, softmax normaliser, bias offset, residual, value mix, key of token 2, token offset) are
caught.

Not covered: how the real model chunks or windows the audio, positional embeddings, the conv2d
front end, the projector, the decoder. The function above is the standard definition, not read
from the model code, so equality with the original implementation is not claimed.
