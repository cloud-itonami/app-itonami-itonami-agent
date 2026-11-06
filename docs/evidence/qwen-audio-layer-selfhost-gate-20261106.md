# Itonami Qwen3-ASR audio-encoder layer wired end to end — native gate, 2026-11-06 (JUN-887)

`kotoba/itonami/qwen_audio_layer.kotoba` wires one layer of the Qwen3-ASR audio tower
(structure read from the real safetensors header in JUN-879): pre-LayerNorm with weight
and bias (eps 1e-5); q, k, v projections with biases (BF16 weight rows decoded while
multiplied in); non-causal softmax attention over all keys with the 1/sqrt(head width)
scale, heads side by side; `x2 = x + Wo . attention + bo`; LayerNorm; `x3 = x2 + W2 .
gelu(W1 h2 + b1) + b2` with the **exact (erf) GELU** of `gelu_erf.kotoba`. Every weight,
bias and norm parameter is a BF16 hex row. The seed has no vectors or memoisation, so each
value is recomputed where it is used; hence the small sizes: d_model 4, 2 heads of width 2,
sequence 3, ffn 6. The layers have no RoPE (positions enter before them).

## Pinned inputs

- `kotoba/itonami/qwen_audio_layer.kotoba`, SHA-256 `201abbe41749398026b281b81cd5903a4419d3428c9f9b9f9dd40023716ace1c`; built on
  `bf16.kotoba` (`09de868917eb1ba9764794cdd5e3c890e462ec22709d9ede4cf1e1554a33549b`), `gelu_erf.kotoba` (`36b4c98a4df8fd817695a62d1602320f23d93fb38ddf97c1fb124bdcbd7c18a6`),
  `nn_ops.kotoba` (`4693d227291a31b8c59378b0ab114715a983dfbfd17308d5b478181793a3604d`), `log_mel.kotoba` (`daf0d273329a7b5a05aef4255b985f2e1c031c74f32596e9578add46980b1f5e`) and
  `ggml_dequant.kotoba` (`c3f22e7c3f189346d8e6a6225c0ae41796369e6045d142dfb031e6fcb429e8d5`).
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.

## Result (2026-11-06)

```sh
bin/check-qwen-audio-layer-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `12`: 3 positions x 4 elements
within 1e-9 of the reference (about 3.4 s wall time including compilation). Thirteen
mutations each stopped the gate returning 12: each of the seven biases removed (LayerNorm 1
and 2, q, v, out, fc1, fc2; all 0), tanh in place of the exact GELU (0), a causal mask on
the otherwise non-causal attention (4: the first position has a single visible key, so it
still matched), either residual dropped (0, 0), no 1/sqrt(d) scale (0), and LayerNorm eps
1e-5 -> 1e-2 (0). A source changed after locking was refused.

## One-off reference (not a gate input)

Python 3.15.0, stdlib only, `python3 -I oracle.py show`, 2026-11-06; a direct nested-loop
definition with `math.erf`, `math.exp`, not a model library. The weights are exactly
representable in bf16 (asserted by the generator) and pasted as hex rows. Not part of any
build, CI or runtime path.

```python
# One-off f64 reference for the Qwen3-ASR audio-encoder layer gate (stdlib only): pre-LN layer
#   x2 = x + out_proj(softmax(q k^T / sqrt(d)) v),  q,k,v = proj(LN1(x)) (with biases), non-causal
#   x3 = x2 + fc2(gelu(fc1(LN2(x2)))), exact (erf) GELU, LayerNorm with weight, bias, eps 1e-5.
# d_model 4, 2 heads of width 2, sequence 3, ffn 6. Weights exact in bf16.
import math, struct, sys
def bits(x): return struct.unpack('>q', struct.pack('>d', float(x)))[0]
def bf16(h): return struct.unpack('<f', b'\x00\x00' + struct.pack('<H', h))[0]
def to_bf16_bits(x): return struct.unpack('<I', struct.pack('<f', x))[0] >> 16
def hexrow(vals): return "".join(struct.pack('<H', to_bf16_bits(v)).hex() for v in vals)
DM, NH, HD, T, FF, EPS = 4, 2, 2, 3, 6, 1e-5
def xin(i, c): return ((i * 5 + c * 3 + 1) % 9 - 4) / 4.0
def wq(r, c): return ((r * 3 + c * 5 + 1) % 7 - 3) / 4.0
def wk(r, c): return ((r * 5 + c * 3 + 2) % 7 - 3) / 4.0
def wv(r, c): return ((r * 7 + c * 5 + 3) % 7 - 3) / 4.0
def wo(o, r): return ((o * 5 + r * 3 + 2) % 9 - 4) / 4.0
def bq(r): return ((r * 3 + 1) % 5 - 2) / 8.0
def bk(r): return ((r * 5 + 2) % 5 - 2) / 8.0
def bv(r): return ((r * 7 + 3) % 5 - 2) / 8.0
def bo(o): return ((o * 2 + 1) % 5 - 2) / 8.0
def l1w(c): return 0.75 + 0.125 * c
def l1b(c): return ((c * 3 + 1) % 5 - 2) / 16.0
def l2w(c): return 1.25 - 0.125 * c
def l2b(c): return ((c * 2 + 3) % 5 - 2) / 16.0
def w1(r, c): return ((r * 7 + c * 3 + 1) % 9 - 4) / 4.0
def b1(r): return ((r * 3 + 2) % 5 - 2) / 8.0
def w2(o, r): return ((o * 3 + r * 5 + 3) % 9 - 4) / 4.0
def b2(o): return ((o * 4 + 1) % 5 - 2) / 8.0
def ln(v, w, b):
    m = sum(v) / len(v); var = sum((x - m) ** 2 for x in v) / len(v)
    inv = 1.0 / math.sqrt(var + EPS)
    return [(v[c] - m) * inv * w[c] + b[c] for c in range(len(v))]
def gelu(x): return 0.5 * x * (1.0 + math.erf(x / math.sqrt(2.0)))
def layer():
    x = [[xin(i, c) for c in range(DM)] for i in range(T)]
    h1 = [ln(x[i], [l1w(c) for c in range(DM)], [l1b(c) for c in range(DM)]) for i in range(T)]
    q = [[sum(wq(r, c) * h1[i][c] for c in range(DM)) + bq(r) for r in range(DM)] for i in range(T)]
    k = [[sum(wk(r, c) * h1[i][c] for c in range(DM)) + bk(r) for r in range(DM)] for i in range(T)]
    v = [[sum(wv(r, c) * h1[i][c] for c in range(DM)) + bv(r) for r in range(DM)] for i in range(T)]
    out = []
    for i in range(T):
        cat = []
        for h in range(NH):
            sc = [sum(q[i][h * HD + e] * k[j][h * HD + e] for e in range(HD)) / math.sqrt(HD) for j in range(T)]
            m = max(sc); ex = [math.exp(s - m) for s in sc]; z = sum(ex)
            for e in range(HD): cat.append(sum(ex[j] / z * v[j][h * HD + e] for j in range(T)))
        o = [sum(wo(oo, r) * cat[r] for r in range(DM)) + bo(oo) for oo in range(DM)]
        x2 = [x[i][c] + o[c] for c in range(DM)]
        h2 = ln(x2, [l2w(c) for c in range(DM)], [l2b(c) for c in range(DM)])
        f1 = [gelu(sum(w1(r, c) * h2[c] for c in range(DM)) + b1(r)) for r in range(FF)]
        f2 = [sum(w2(oo, r) * f1[r] for r in range(FF)) + b2(oo) for oo in range(DM)]
        out += [x2[c] + f2[c] for c in range(DM)]
    return out
if len(sys.argv) > 1:
    print("python", sys.version.split()[0]); print(layer())
```

## Qualification limits

Agreement is with this reference on small analytic BF16 weights, not with Qwen3-ASR's own
implementation and not on real weights. Not covered: real sizes (d_model 896, 14 heads,
ffn 3584), the position embedding added before the layers, the conv2d front end and
`conv_out` projection, the chunked windowing (`n_window`, `conv_chunksize`), `ln_post`
and the two output projections into the LLM, 18 layers. Recomputation without memoisation
is impractical at real sizes; array primitives in the seed (JUN-876) are the
precondition. The Amu CLI is an unmerged candidate. No CLJK, EDN, JVM or Node was used;
the reference is Python, as stated.
