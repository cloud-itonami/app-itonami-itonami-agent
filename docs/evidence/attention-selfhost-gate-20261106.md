# Itonami attention and GEMM native scalar gate — 2026-11-06 (JUN-870)

`kotoba/itonami/attention.kotoba` holds the scalar arithmetic of the two
kernels the whisper encoder and decoder still lacked: single-head scaled
dot-product attention with a softmax shifted by the row maximum (non-causal
self, causal self with a `j > i` mask, and cross with a different key length),
and `C = A.B + bias`. It builds on `itonami.nn-ops`' `exp-wide`. The seed has no
vectors, so the rows are analytic fixtures (d = 2; Q 4x2/3x2, K and V up to
5x2, A 3x4, B 4x2); the arithmetic per output element is the real kernel's and
a later unit drives it over host data. Multi-head, d = 64 and larger shapes,
and a GPU kernel are not covered.

## Pinned inputs

- `kotoba/itonami/attention.kotoba`, SHA-256
  `b3a30442ed5b55f42804c697cbc32b8faff301d8377ad7383e2a31e81af14d84`; built on
  `nn_ops.kotoba` (`4693d227291a31b8c59378b0ab114715a983dfbfd17308d5b478181793a3604d`) and
  `log_mel.kotoba` (`19f892cd437a994661354c3ec90963c3b5ef84ee4885cc157e3a96595049fcd4`).
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.

## Result (2026-11-06)

```sh
bin/check-attention-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `28`: 8 non-causal and 8
causal self-attention outputs, 6 cross-attention outputs and 6 GEMM outputs,
each within 1e-9 (relative above 1) of the reference. Six mutations each stopped
the gate returning 28: dropping the 1/sqrt(d) scale (8), dropping the max shift
from the numerator (7), flipping the mask direction (20), dropping the bias
(22), reading value column 0 only (17), doubling the softmax denominator (7).
A source changed after locking was refused. The lock lists four modules sorted
by name and files sorted by path.

## One-off reference (not a gate input)

Python 3.15.0, stdlib only, `python3 -I oracle.py show`, 2026-11-06; values are
pasted into the gate as f64 bit patterns. Not part of any build, CI or runtime
path.

```python
# One-off f64 reference for the attention/GEMM gate (stdlib only): single-head
# scaled dot-product attention (non-causal, causal, cross) and C = A.B + bias.
import math, struct, sys
def bits(x): return struct.unpack('>q', struct.pack('>d', float(x)))[0]
D = 2
def Q(i, d): return ((i * 3 + d * 5) % 7 - 3) / 4.0
def K(j, d): return ((j * 5 + d * 3 + 1) % 9 - 4) / 4.0
def V(j, d): return ((j * 2 + d * 7) % 5 - 2) / 2.0
def attn(lq, lk, causal):
    out = []
    for i in range(lq):
        js = [j for j in range(lk) if not (causal and j > i)]
        s = [sum(Q(i, d) * K(j, d) for d in range(D)) / math.sqrt(D) for j in js]
        m = max(s)
        e = [math.exp(x - m) for x in s]
        z = sum(e)
        for d in range(D):
            out.append(sum(e[n] / z * V(js[n], d) for n in range(len(js))))
    return out
def A(i, k): return ((i + 2 * k) % 5 - 2) / 3.0
def B(k, j): return ((3 * k + j) % 4 - 1) / 2.0
def bias(j): return j * 0.25 - 0.1
def gemm():
    return [sum(A(i, k) * B(k, j) for k in range(4)) + bias(j) for i in range(3) for j in range(2)]
if len(sys.argv) > 1:
    print("python", sys.version.split()[0])
    print("noncausal", attn(4, 4, False)); print("causal", attn(4, 4, True)); print("cross", attn(3, 5, False)); print("gemm", gemm())
```

## Qualification limits

Agreement is with these formulas on fixed d = 2 fixtures, not with whisper.cpp
output or real activations. Not covered: multi-head, larger d and sequence
lengths (1500 encoder frames), the output projection, f16 or quantised weights,
GPU kernels. The Amu CLI is an unmerged candidate. No CLJK, EDN, JVM or Node
was used; the reference is Python, as stated.

## Update — two heads (2026-11-06, JUN-870)

`attention-at` now takes a head index: head h reads columns 2h and 2h+1 of
Q, K and V (head width 2, two heads, model width 4) and the heads' outputs sit
side by side. Head 0 reproduces the earlier 28 values unchanged; head 1 adds 22
more (non-causal 8, causal 8, cross 6), so the gate returns **50** (direct,
source-locked and artifact runs). The reference script's `attn` function was
extended with the same head offset (values are pasted as f64 bit patterns).
Eight mutations each stopped the gate returning 50: no 1/sqrt(d) scale (10),
dropping the max shift from the numerator (7), mask direction (34), no bias (44),
value head offset (28), query head offset (30), key head offset (34), doubled
denominator (7). A source changed after locking was refused.

New attention source SHA-256
`e66eaec2c210cb7aa2201ec82cac55cf49cae65197fb519bbae821971f13173a`.
Still not covered: real shapes (head width 64, 1500 encoder frames), the output
projection after the heads, weights, GPU kernels.
