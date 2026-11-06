# Itonami exact GELU (erf form) native gate — 2026-11-06 (JUN-881)

`kotoba/itonami/gelu_erf.kotoba` is the activation Qwen3-ASR's audio encoder names
(`activation_function: "gelu"` in its config.json, the exact PyTorch GELU), as
opposed to `nn_ops.kotoba`'s tanh approximation that whisper.cpp uses:
`gelu(x) = 0.5 * x * (1 + erf(x / sqrt 2))`. The seed has no erf, so it is built
here: the Maclaurin series `2/sqrt(pi) * sum (-1)^n z^(2n+1) / (n! (2n+1))` (60
terms) for `|z| <= 3`, and for larger `|z|` erfc by its continued fraction
`exp(-z^2) / (sqrt(pi) * (z + (1/2)/(z + 1/(z + (3/2)/(z + ...)))))` evaluated
backwards (60 terms) on top of `nn_ops`' `exp-wide`. For `z < 0` the GELU uses
`erfc(-z)` so the negative tail does not cancel.

## Pinned inputs

- `kotoba/itonami/gelu_erf.kotoba`, SHA-256
  `36b4c98a4df8fd817695a62d1602320f23d93fb38ddf97c1fb124bdcbd7c18a6`; built on
  `nn_ops.kotoba` (`4693d227291a31b8c59378b0ab114715a983dfbfd17308d5b478181793a3604d`) and
  `log_mel.kotoba` (`daf0d273329a7b5a05aef4255b985f2e1c031c74f32596e9578add46980b1f5e`).
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.

## Result (2026-11-06)

```sh
bin/check-gelu-erf-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `37`: 18 GELU points
(from -8 to 8, both sides of the series/continued-fraction switch at x = 4.24, and
the negative tail), 14 erf points (from -6.5 to 6.5 around the switch at |z| = 3)
and 5 erfc points, each within 1e-9 (relative above 1) of the reference. Seven
mutations each stopped the gate returning 37: series length (21), switch point (33),
continued-fraction length (36), erf sign (35), erfc reflection (36), the 0.5 factor
(21), series sign (15). A mutation that only moved the switch to |z| = 1.5 still
passed: the 60-term continued fraction is accurate that far down, so that change
is behaviour-preserving; the control was strengthened to 0.2.

## One-off reference (not a gate input)

Python 3.15.0, stdlib only, `python3 -I oracle.py show`, 2026-11-06. The
reference is CPython's `math.erf` / `math.erfc` (the C library's), independent of
the series and continued fraction in the gate; the GELU reference uses `erfc` in
the negative tail. Not part of any build, CI or runtime path.

```python
# One-off reference for the exact-GELU gate (stdlib only): CPython's math.erf / math.erfc
# (the C library's) -- independent of the series and continued fraction in the gate.
import math, struct, sys
def bits(x): return struct.unpack('>q', struct.pack('>d', float(x)))[0]
XS = [-8.0, -5.0, -4.3, -4.2, -3.0, -2.0, -1.0, -0.5, -0.1, 0.0, 0.1, 0.5, 1.0, 2.0, 4.2, 4.3, 5.0, 8.0]
ZS = [-6.5, -3.5, -3.0, -2.9, -1.0, -0.3, 0.0, 0.3, 1.0, 2.9, 3.0, 3.1, 4.0, 6.5]
def gelu(x): return 0.5 * x * (1.0 + math.erf(x / math.sqrt(2.0)))
def gelu_stable(x):
    z = x / math.sqrt(2.0)
    return 0.5 * x * (math.erfc(-z) if z < 0 else 1.0 + math.erf(z))
if len(sys.argv) > 1:
    print("python", sys.version.split()[0])
    for x in XS: print(x, gelu(x), gelu_stable(x))
    print("erf", [(z, math.erf(z)) for z in ZS[:4]])
```

## Qualification limits

Scalar, fixed points, 1e-9. Not covered: real activations, a vector version, the
encoder around it (conv2d layers in `conv2d.kotoba`, LayerNorm and attention in the
earlier units), the LLM's SiLU/RMSNorm. The Amu CLI is an unmerged candidate. No
CLJK, EDN, JVM or Node was used; the reference is Python, as stated.
