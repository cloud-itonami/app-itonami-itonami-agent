# Itonami Qwen3 decoder ops native gate — 2026-11-06 (JUN-882)

`kotoba/itonami/qwen_ops.kotoba` holds the decoder building blocks of Qwen3-ASR's
LLM and Qwen3-TTS's talker (config read in JUN-877: `hidden_act: silu`,
`rms_norm_eps: 1e-6`, `rope_theta: 1e6`, `head_dim: 128`, GQA 16/8): weighted RMSNorm
steps (`rms-inv`, `rms-apply`), SiLU, cos/sin of an arbitrary real angle, and RoPE in
the NeoX / rotate_half layout (the pair (i, i + D/2) rotated by `pos *
theta^(-2i/D)`). RoPE needs real angles, so the angle is reduced by the nearest
multiple of pi/2 (single constant, error about 6e-17 per multiple) and a 12-term
Taylor series runs on `|r| <= pi/4`. Built on `nn_ops`' `exp-wide` and `log_mel`'s
`ln-pos`. Scalar steps: the seed has no vectors.

## Pinned inputs

- `kotoba/itonami/qwen_ops.kotoba`, SHA-256
  `a5a8744046a81edd21d0740395e874c89ed518e24092a5e889c78b2b2ba51576`; built on
  `nn_ops.kotoba` (`4693d227291a31b8c59378b0ab114715a983dfbfd17308d5b478181793a3604d`) and
  `log_mel.kotoba` (`daf0d273329a7b5a05aef4255b985f2e1c031c74f32596e9578add46980b1f5e`).
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.

## Result (2026-11-06)

```sh
bin/check-qwen-ops-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `67`: 16 RMSNorm outputs and
the inverse RMS (eps 1e-6), 10 SiLU points (-10 to 30), and 40 RoPE values (head
width 8, five cases: theta 1e6 at positions 0, 1, 7 and 1234, and theta 1e4 at
position 5, covering every quadrant of the reduced angle), each within 1e-9
(relative above 1) of the reference. Nine mutations each stopped the gate returning
67: RMSNorm eps (51), weight (55), SiLU sign (58), cos quadrant 1 (62), cos
quadrant 2 (65), sine series length (51), RoPE first-half sign (55), second-half
sign (51), RoPE frequency exponent (43). A source changed after locking was refused.

## One-off reference (not a gate input)

Python 3.15.0, stdlib only, `python3 -I oracle.py show`, 2026-11-06; direct
definitions of RMSNorm, SiLU and the NeoX-layout RoPE with `math.cos`/`math.sin`/
`math.exp`, not a model library. Values are pasted as f64 bit patterns. Not part of
any build, CI or runtime path.

```python
# One-off f64 reference for the Qwen3 decoder-ops gate (stdlib only): RMSNorm, SiLU and
# RoPE in the NeoX / rotate_half layout (pairs (i, i + D/2), freq_i = theta^(-2i/D)).
import math, struct, sys
def bits(x): return struct.unpack('>q', struct.pack('>d', float(x)))[0]
N = 16
EPS = 1e-6
def xv(i): return ((i * 7 + 3) % 13 - 6) / 4.0
def wv(i): return 0.5 + (i % 5) * 0.25
mean_sq = sum(xv(i) ** 2 for i in range(N)) / N
INV = 1.0 / math.sqrt(mean_sq + EPS)
RMS = [xv(i) * INV * wv(i) for i in range(N)]
SILU_X = [-10.0, -4.0, -1.5, -0.2, 0.0, 0.2, 1.5, 4.0, 10.0, 30.0]
def silu(x): return x / (1.0 + math.exp(-x))
D = 8
def rx(j): return ((j * 5 + 1) % 9 - 4) / 3.0
def rope(pos, theta):
    h = D // 2
    out = [0.0] * D
    for i in range(h):
        ang = pos * theta ** (-2.0 * i / D)
        c, s = math.cos(ang), math.sin(ang)
        out[i] = rx(i) * c - rx(i + h) * s
        out[i + h] = rx(i + h) * c + rx(i) * s
    return out
CASES = [(0, 1e6), (1, 1e6), (7, 1e6), (1234, 1e6), (5, 1e4)]
if len(sys.argv) > 1:
    print("python", sys.version.split()[0]); print("INV", INV, "RMS[:3]", RMS[:3])
    for p, t in CASES: print(p, t, rope(p, t)[:3])
```

## Qualification limits

Head width 8 and positions up to 1234, not head width 128 and positions in the
tens of thousands (the angle reduction error grows linearly with the angle, about
6e-17 per quarter turn). Not covered: GQA head arrangement (16 query heads over 8
KV heads), the QK-norm Qwen3 applies, the attention and MLP matmuls, real weights,
the sliding-window attention. The Amu CLI is an unmerged candidate. No CLJK, EDN,
JVM or Node was used; the reference is Python, as stated.
