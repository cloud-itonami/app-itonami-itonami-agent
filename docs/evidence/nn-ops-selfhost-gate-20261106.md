# Itonami nn-ops native scalar gate — 2026-11-06 (JUN-867)

`kotoba/itonami/nn_ops.kotoba` adds the scalar neural-net ops the whisper
encoder/decoder need beyond log-mel: wide-range exp (range reduction by powers
of two on top of `itonami.log-mel`'s series, clamped to [-60, 60]), tanh
(`1 - 2/(exp(2y)+1)`, y clamped to [-20, 20]), the tanh-approximation GELU
that whisper.cpp uses (`ggml_gelu`; not the erf form of the PyTorch
reference), and LayerNorm steps (inverse standard deviation, normalise then
scale and shift). The seed has no vectors, so LayerNorm is scalar steps; a host
or later unit drives the reductions over a row.

## Pinned inputs

- `kotoba/itonami/nn_ops.kotoba`, SHA-256
  `4693d227291a31b8c59378b0ab114715a983dfbfd17308d5b478181793a3604d`;
  `kotoba/itonami/log_mel.kotoba`, SHA-256
  `19f892cd437a994661354c3ec90963c3b5ef84ee4885cc157e3a96595049fcd4`.
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.

## Result (2026-11-06)

```sh
bin/check-nn-ops-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `38`: 6 exp, 6 tanh and
8 GELU values, the fixture mean and variance, and 16 LayerNorm outputs, each
within 1e-9 (relative above 1) of the reference. Six mutations each stopped the
gate returning 38: GELU coefficient (33), tanh numerator (27), exp reduction
(23), exp clamp (34), variance divisor (21), LayerNorm shift (25). A source
changed after locking was refused. The lock lists three modules sorted by name.

## One-off reference (not a gate input)

Python 3.15.0, stdlib only, run as `python3 -I oracle.py show` on 2026-11-06;
its values are pasted into the gate as f64 bit patterns. Not part of any
build, CI or runtime path.

```python
# One-off f64 reference for the nn-ops gate (stdlib only): wide exp, tanh,
# whisper.cpp/ggml tanh-approx GELU, LayerNorm (biased variance, eps=1e-5).
import math, struct, sys
def bits(x): return struct.unpack('>q', struct.pack('>d', float(x)))[0]
EXP_X = [-30.0, -1.3, 0.0, 0.7, 5.0, 20.0]
TANH_X = [-9.0, -0.5, 0.0, 0.2, 3.0, 12.0]
GELU_X = [-6.0, -2.5, -0.5, 0.0, 0.3, 1.7, 4.0, 9.0]
def gelu(x): return 0.5 * x * (1.0 + math.tanh(0.7978845608028654 * (x + 0.044715 * x * x * x)))
N = 16
X = [((i * i) % 17 - 8) / 3.0 for i in range(N)]
G = [1.0 + (i % 3) * 0.25 for i in range(N)]
B = [(i % 5) * 0.1 - 0.2 for i in range(N)]
mean = sum(X) / N
var = sum((v - mean) ** 2 for v in X) / N
inv = 1.0 / math.sqrt(var + 1e-5)
Y = [(X[i] - mean) * inv * G[i] + B[i] for i in range(N)]
if len(sys.argv) > 1:
    print("python", sys.version.split()[0])
    print("EXP", [math.exp(x) for x in EXP_X])
    print("TANH", [math.tanh(x) for x in TANH_X])
    print("GELU", [gelu(x) for x in GELU_X])
    print("MEAN", mean, "VAR", var)
    print("Y", Y)
```

## Qualification limits

Agreement is with these formulas on fixed fixtures (6/6/8 points and one
16-element row), not with whisper.cpp output or real activations. Not covered:
the erf-form GELU, rows longer than 16, mean/variance reductions as a reusable
driver, f16 values, GPU kernels, GEMM, attention. The Amu CLI is an unmerged
candidate. No CLJK, EDN, JVM or Node was used; the reference is Python, as
stated.
