# Itonami Qwen3 decoder layer wired end to end — native gate, 2026-11-06 (JUN-886)

`kotoba/itonami/qwen_layer.kotoba` composes the parts of JUN-879/882/883/884 into one
pre-norm Qwen3 layer: RMSNorm; q, k, v projections (BF16 weight rows decoded while
multiplied in); QK-norm and NeoX RoPE (position i for queries, j for keys);
grouped-query causal attention (the two query heads share the single KV head);
`x2 = x + Wo . attention`; RMSNorm; `x3 = x2 + Wd . (silu(Wg h2) * (Wu h2))`. Every weight,
the four norm weights included, is a BF16 hex row. The seed has no vectors or
memoisation, so each value is recomputed where it is used; hence the small sizes:
hidden 4, 2 query heads over 1 KV head of width 2, sequence 2, intermediate 3.

## Pinned inputs

- `kotoba/itonami/qwen_layer.kotoba`, SHA-256 `bff247ea9cdf9865c7c15b2da482539ad2ff6547e330d70b8c996a9e7380c5b8`; built on
  `bf16.kotoba` (`09de868917eb1ba9764794cdd5e3c890e462ec22709d9ede4cf1e1554a33549b`), `qwen_ops.kotoba` (`a5a8744046a81edd21d0740395e874c89ed518e24092a5e889c78b2b2ba51576`),
  `nn_ops.kotoba` (`4693d227291a31b8c59378b0ab114715a983dfbfd17308d5b478181793a3604d`), `log_mel.kotoba` (`daf0d273329a7b5a05aef4255b985f2e1c031c74f32596e9578add46980b1f5e`) and
  `ggml_dequant.kotoba` (`c3f22e7c3f189346d8e6a6225c0ae41796369e6045d142dfb031e6fcb429e8d5`).
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.

## Result (2026-11-06)

```sh
bin/check-qwen-layer-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `8`: both positions x four hidden
elements within 1e-9 of the reference (about 1.6 s wall time including compilation).
Eleven mutations each stopped the gate returning 8: the attention residual (0), the
MLP residual (0), the attention norm weights (0), the MLP norm weights (0), no QK-norm
on q (4), the causal mask shifted (0), the key element index (4), the head
concatenation order (4), no SiLU (0), the o_proj width (0), the query RoPE position (4).
A "4" means position 0 still matched (the mutated stage is invisible at position 0) and
position 1 did not. A source changed after locking was refused.

## One-off reference (not a gate input)

Python 3.15.0, stdlib only, `python3 -I oracle.py show`, 2026-11-06; a direct nested-loop
definition of the layer with `math` functions, not a model library. The weights are
exactly representable in bf16 (asserted by the generator) and are pasted as hex rows.
Not part of any build, CI or runtime path.

```python
# One-off f64 reference for the Qwen3 decoder-layer gate (stdlib only): pre-norm layer
#   x2 = x + o_proj(attention(rmsnorm(x)));  x3 = x2 + down(silu(gate(h)) * up(h)), h = rmsnorm(x2)
# with QK-norm, NeoX RoPE and grouped-query causal attention. hidden 4, 2 query heads over 1 KV
# head (width 2), sequence 2, intermediate 3, theta 1e6, eps 1e-6. Weights are exact in bf16.
import math, struct, sys
def bits(x): return struct.unpack('>q', struct.pack('>d', float(x)))[0]
def bf16(h): return struct.unpack('<f', b'\x00\x00' + struct.pack('<H', h))[0]
def to_bf16_bits(x): return struct.unpack('<I', struct.pack('<f', x))[0] >> 16
def hexrow(vals): return "".join(struct.pack('<H', to_bf16_bits(v)).hex() for v in vals)
H, NQ, NKV, D, T, I, THETA, EPS = 4, 2, 1, 2, 2, 3, 1e6, 1e-6
def xin(i, c): return ((i * 5 + c * 3 + 1) % 9 - 4) / 4.0
def wq(r, c): return ((r * 3 + c * 5 + 1) % 7 - 3) / 4.0
def wk(r, c): return ((r * 5 + c * 3 + 2) % 7 - 3) / 4.0
def wv(r, c): return ((r * 7 + c * 5 + 3) % 7 - 3) / 4.0
def wo(o, r): return ((o * 5 + r * 3 + 2) % 9 - 4) / 4.0
def qnw(e): return 0.5 + 0.25 * e
def knw(e): return 1.0 - 0.25 * e
def anw(c): return 0.75 + 0.125 * c
def mnw(c): return 1.25 - 0.125 * c
def wg(r, c): return ((r * 7 + c * 3 + 1) % 9 - 4) / 4.0
def wu(r, c): return ((r * 5 + c * 7 + 2) % 7 - 3) / 8.0
def wd(o, r): return ((o * 3 + r * 5 + 3) % 9 - 4) / 4.0
def rms(v, w):
    inv = 1.0 / math.sqrt(sum(x * x for x in v) / len(v) + EPS)
    return [v[e] * inv * w[e] for e in range(len(v))]
def rope(v, pos):
    a = pos * THETA ** 0.0
    c, s = math.cos(a), math.sin(a)
    return [v[0] * c - v[1] * s, v[1] * c + v[0] * s]
def silu(x): return x / (1.0 + math.exp(-x))
def layer():
    x = [[xin(i, c) for c in range(H)] for i in range(T)]
    h1 = [rms(x[i], [anw(c) for c in range(H)]) for i in range(T)]
    q = [[sum(wq(r, c) * h1[i][c] for c in range(H)) for r in range(NQ * D)] for i in range(T)]
    k = [[sum(wk(r, c) * h1[j][c] for c in range(H)) for r in range(NKV * D)] for j in range(T)]
    v = [[sum(wv(r, c) * h1[j][c] for c in range(H)) for r in range(NKV * D)] for j in range(T)]
    kr = [rope(rms(k[j][0:D], [knw(e) for e in range(D)]), j) for j in range(T)]
    out = []
    for i in range(T):
        cat = []
        for h in range(NQ):
            qr = rope(rms(q[i][h * D:(h + 1) * D], [qnw(e) for e in range(D)]), i)
            sc = [sum(qr[e] * kr[j][e] for e in range(D)) / math.sqrt(D) for j in range(i + 1)]
            m = max(sc); ex = [math.exp(s - m) for s in sc]; z = sum(ex)
            for e in range(D): cat.append(sum(ex[j] / z * v[j][e] for j in range(i + 1)))
        o = [sum(wo(oo, r) * cat[r] for r in range(NQ * D)) for oo in range(H)]
        x2 = [x[i][c] + o[c] for c in range(H)]
        h2 = rms(x2, [mnw(c) for c in range(H)])
        g = [sum(wg(r, c) * h2[c] for c in range(H)) for r in range(I)]
        u = [sum(wu(r, c) * h2[c] for c in range(H)) for r in range(I)]
        a = [silu(g[r]) * u[r] for r in range(I)]
        d = [sum(wd(oo, r) * a[r] for r in range(I)) for oo in range(H)]
        out += [x2[c] + d[c] for c in range(H)]
    return out
if len(sys.argv) > 1:
    print("python", sys.version.split()[0]); print(layer())
```

## Qualification limits

Agreement is with this reference on small analytic BF16 weights, not with Qwen3's own
implementation and not on real weights. Not covered: real sizes (hidden 1024, head width
128, 16 over 8 heads, intermediate 3072), a KV cache, more than one layer, the final norm
and lm_head, sliding-window attention, precision at large sizes (the reference
accumulates in f64). Recomputation without memoisation makes this approach
impractical at real sizes; a vector or array primitive in the seed (JUN-876) is the
precondition for running the actual model. The Amu CLI is an unmerged candidate. No
CLJK, EDN, JVM or Node was used; the reference is Python, as stated.
