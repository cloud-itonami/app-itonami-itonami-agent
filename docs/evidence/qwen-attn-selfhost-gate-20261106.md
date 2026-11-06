# Itonami Qwen3 attention layer native gate — 2026-11-06 (JUN-883)

`kotoba/itonami/qwen_attn.kotoba` is one attention layer of Qwen3 (the LLM inside
Qwen3-ASR and the talker of Qwen3-TTS): per-head RMSNorm of q and k with weights over
head_dim (QK-norm), NeoX RoPE on the normalised q and k (position i for queries,
position j for keys), grouped-query attention (the query heads of a group share one
KV head; Qwen3-ASR's LLM has 16 query heads over 8 KV heads, group 2), and a causal
softmax shifted by the row maximum, then the weighted sum of v. Scalar steps on an
analytic fixture: head width 4, 4 query heads over 2 KV heads, sequence 3, theta 1e6,
eps 1e-6. Built on `qwen_ops` (RMSNorm steps, RoPE), `nn_ops` (exp-wide) and
`log_mel`. The seed has no vectors, so a host or later unit drives the same
arithmetic over real tensors and adds the output projection.

## Pinned inputs

- `kotoba/itonami/qwen_attn.kotoba`, SHA-256
  `c1ef7a27aedd9c74bf5598d02b5c870f2afef0b8add7b8919594231362d7f2cd`; built on
  `qwen_ops.kotoba` (`a5a8744046a81edd21d0740395e874c89ed518e24092a5e889c78b2b2ba51576`),
  `nn_ops.kotoba` (`4693d227291a31b8c59378b0ab114715a983dfbfd17308d5b478181793a3604d`) and
  `log_mel.kotoba` (`daf0d273329a7b5a05aef4255b985f2e1c031c74f32596e9578add46980b1f5e`).
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.

## Result (2026-11-06)

```sh
bin/check-qwen-attn-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `48`: all 3 positions x 4
query heads x 4 elements within 1e-9 (relative above 1) of the reference. Nine
mutations each stopped the gate returning 48: no QK-norm on q (16), the wrong
QK-norm weights on k (16), the KV-head mapping h/2 -> h%2 (24), the causal mask
shifted by one (0), no 1/sqrt(d) scale (16), the key RoPE position fixed at 0
(16), the wrong RoPE index in the second half of q (16), values read from KV head 0
only (24), no max shift in the numerator (0). A source changed after locking was
refused.

## One-off reference (not a gate input)

Python 3.15.0, stdlib only, `python3 -I oracle.py show`, 2026-11-06; direct
definitions of RMSNorm, NeoX RoPE and grouped-query causal attention with `math`
functions, not a model library. Values are pasted as f64 bit patterns. Not part of
any build, CI or runtime path.

```python
# One-off f64 reference for the Qwen3 attention-layer gate (stdlib only): per-head RMSNorm of
# q and k (QK-norm), NeoX RoPE, grouped-query attention, causal softmax. D=4, 4 query heads,
# 2 KV heads (group 2), sequence 3, theta 1e6, eps 1e-6. Direct definitions, no model library.
import math, struct, sys
def bits(x): return struct.unpack('>q', struct.pack('>d', float(x)))[0]
D, NQ, NKV, T, THETA, EPS = 4, 4, 2, 3, 1e6, 1e-6
GROUP = NQ // NKV
def fq(i, h, e): return ((i * 7 + h * 5 + e * 3 + 1) % 11 - 5) / 4.0
def fk(j, g, e): return ((j * 5 + g * 7 + e * 2 + 3) % 9 - 4) / 3.0
def fv(j, g, e): return ((j * 3 + g * 5 + e * 7 + 2) % 7 - 3) / 2.0
def wq(e): return 0.8 + 0.1 * e
def wk(e): return 1.1 - 0.1 * e
def rms_norm(v, w):
    inv = 1.0 / math.sqrt(sum(x * x for x in v) / len(v) + EPS)
    return [v[e] * inv * w[e] for e in range(len(v))]
def rope(v, pos):
    h = D // 2
    out = [0.0] * D
    for i in range(h):
        a = pos * THETA ** (-2.0 * i / D)
        c, s = math.cos(a), math.sin(a)
        out[i] = v[i] * c - v[i + h] * s
        out[i + h] = v[i + h] * c + v[i] * s
    return out
def qrot(i, h): return rope(rms_norm([fq(i, h, e) for e in range(D)], [wq(e) for e in range(D)]), i)
def krot(j, g): return rope(rms_norm([fk(j, g, e) for e in range(D)], [wk(e) for e in range(D)]), j)
def layer():
    out = []
    for i in range(T):
        for h in range(NQ):
            g = h // GROUP
            q = qrot(i, h)
            sc = [sum(q[e] * krot(j, g)[e] for e in range(D)) / math.sqrt(D) for j in range(i + 1)]
            m = max(sc); ex = [math.exp(s - m) for s in sc]; z = sum(ex)
            for e in range(D):
                out.append(sum(ex[j] / z * fv(j, g, e) for j in range(i + 1)))
    return out
if len(sys.argv) > 1:
    o = layer(); print("python", sys.version.split()[0], "n", len(o)); print(o[:6])
```

## Qualification limits

Agreement is with this reference on a fixed fixture, not with Qwen3's actual
implementation or real activations. Not covered: head width 128 and 16 over 8 heads,
the q/k/v/o projections (the real tensors are BF16 matrices), the KV cache,
sliding-window attention, the MLP and the residual and norm layers around it, the
chunked audio encoder. The Amu CLI is an unmerged candidate. No CLJK, EDN, JVM or
Node was used; the reference is Python, as stated.
