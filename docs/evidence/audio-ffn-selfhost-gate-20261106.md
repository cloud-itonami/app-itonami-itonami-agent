# Native audio-encoder feed-forward sublayer on the real weights (JUN-916, 2026-11-06)

`kotoba/itonami/audio_ffn.kotoba` computes, for one 896-wide token,

    out = x + fc2(gelu(fc1(LayerNorm(x))))       (every projection with its bias)

with the real `thinker.audio_tower.layers.N` tensors of Qwen3-ASR-0.6B: `final_layer_norm`
weight and bias (896), `fc1` (3584 x 896) and bias, `fc2` (896 x 3584) and bias. Weights stay in
the safetensors file and are read one row at a time with FS2 E requests (`itonami.weights-file`
reads the header); activations are internal `vector-f64` arrays. LayerNorm uses eps 1e-5 (the
torch default; `config.json` names no eps) and the exact GELU (`itonami.gelu-erf`). The input
token is x[i] = (((3 i + 1) mod 11) - 5) / 4.

Gate: `bin/check-audio-ffn-selfhost <cli> <out-dir> <model.safetensors>`, 46 checks (11 feed-forward, 11 attention, 11 whole layer, 8 values after 3 and 18 stacked layers, 5 refusals of the one-layer CLI; see the last sections), one process
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
  traps (exit 133) while two succeed. The gate therefore runs one process per case. Building a
  `vector-f64` by `conj` copies the whole vector on every append (a 3584-element vector costs about
  50 MB, 100 of them exhaust the roughly 1 GB arena); see the layer section for the fix.
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

## The whole layer (JUN-921)

`layer-at path layer idx` and `layer-sum path layer` compose the two halves for the three tokens:
h_t = x_t + attn(LN1(x))_t, then out_t = h_t + ffn(LN2(h_t)) with the layer's own
`final_layer_norm`, `fc1` and `fc2`. Layers 0 and 5 agree with the CPython composition to 1e-12
relative (the sum and outputs 0, 1000, 2687, an index past the end, a missing layer, a missing
file: 11 more cases, 33 in all). One evaluation takes about 1.2 s and 570 MB.

The first composition trapped (exit 133, 1.1 GB resident): feed-forward after attention for
three tokens needs about 3584-element vectors nine times over, and `conj` copies. The module now
keeps activations in `vector-i64` arrays of f64 bit patterns, allocated with `vector-alloc` and
filled in place with `vector-assoc!` (the weights are already handled as bit patterns). That
measured 100 allocations of 3584 elements in 4 MB against 1 GB for `conj`, and the feed-forward
evaluation went from about 1 GB to 126 MB. The numbers are unchanged: the earlier 22 cases pass
without edits to their expected values. Four more mutations (the feed-forward input of token 1,
the token order of the concatenation, the feed-forward skipped for token 2, a slice offset) are
caught, 21 in all.

Not covered: positional embeddings, how the real model chunks audio, the conv2d front end, the
projector, the decoder, any layer other than 0 and 5, equality with the original implementation.

## The 18-layer stack (JUN-922)

`kotoba/agent_cli/audio_layer_step_cli.kotoba` runs one layer as its own process: argv 0 is the model
path inside the granted directory, argv 1 the layer index, optional argv 2 the activations of the
three tokens as 43,008 hex characters (16 per f64 bit pattern); it prints the activations after the
layer in the same format (exit 2 and one named line on stderr for a bad index, a malformed input or
an unreadable layer). The gate script chains layers 0..17 (synthetic tokens in, hex on argv between
processes, about 11 s for all 18) and compares the activations after 3 and 18 layers with a CPython
float64 stack: the sum and outputs 0, 1000, 2687, to 1e-11 relative (absolute below magnitude 1).
Measured differences: after 3 layers 2e-15 on the sum and below 1e-15 on the outputs; after 18
layers 2.9e-13 on the sum (which is only 0.06, so its low digits cancel) and 1.4e-14 to 2.1e-14
on the outputs (largest output magnitude 15.2). Five refusals of the CLI (a layer past the last,
an input one character short, a non-hex character, a non-numeric layer, a missing model file) exit
2 with nothing on stdout. Five further mutations (hex decode shift, encode order, nibble table, the
step ignoring its input, the step skipping the hex check; 26 in all) are caught.

Why one process per layer: the native runtime never reclaims memory and keeps about eight bytes for
every byte a file read returns. That was measured, not assumed: ten reads of the header cost 15 MB,
the activation vectors are tens of kilobytes, but one layer reads 19 MB of weights and holds about
150 MB for good (one layer evaluated for the synthetic tokens peaks at 530 MB), so 18 layers would
need roughly 2.8 GB against an arena of about 1 GB. Reading the weights in blocks of 256 KiB
instead of row by row (done here, and about 3x faster) did not change the total. A single process
therefore reaches about two layers.

Not covered: positional embeddings, the conv2d front end, how the real model chunks audio, `ln_post`,
the projector into the language model, the decoder, equality with the original implementation.
