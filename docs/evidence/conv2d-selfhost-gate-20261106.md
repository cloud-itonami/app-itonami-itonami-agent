# Itonami conv2d native scalar gate — 2026-11-06 (JUN-880)

`kotoba/itonami/conv2d.kotoba` is the convolution Qwen3-ASR's audio tower starts with
(JUN-879 read its header: `conv2d1.weight [480,1,3,3]`, `conv2d2/3.weight
[480,480,3,3]`, three 3x3 layers, 8x reduction overall): kernel 3x3, padding 1,
stride 1 or 2, several input and output channels, with bias. Integer rules
(`conv2d-out-len`, `conv2d-tap` giving the input position or -1 in the padding)
plus a scalar convolution checked on an analytic fixture (2 in / 2 out channels,
a 5 x 6 input) at stride 1 (5 x 6 outputs per channel) and stride 2 (3 x 3). The
seed has no vectors, so a host or later unit drives it over real tensors.

## Pinned inputs

- `kotoba/itonami/conv2d.kotoba`, SHA-256
  `8a84d129a76ff02aa6178abfe62cc49104136514bfe975343830e0bd34e7c97f`.
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.

## Result (2026-11-06)

```sh
bin/check-conv2d-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `88`: 60 stride-1 and 18
stride-2 outputs within 1e-9 of the reference, 6 output-length checks (including
Qwen's three stride-2 layers, 128 -> 64 -> 32 -> 16, and 100 -> 50), and 4 tap
checks. Seven mutations each stopped the gate returning 88: tap offset (7), output
length (82), kernel x index (10), kernel axis order (10), channel sum range (10),
bias (10), padding check on x (62). A source changed after locking was refused.
The first run returned 87 because one hand-written tap expectation was wrong
(`conv2d-tap 2 2 2 6` is 5, a valid position; the case meant for the padding uses
n = 5); the code was right and the expectation was corrected.

## One-off reference (not a gate input)

Python 3.15.0, stdlib only, `python3 -I oracle.py show`, 2026-11-06; a direct
definition of Conv2d (kernel 3x3, padding 1, stride s, bias) in nested loops, not
the torch package. Values are pasted as f64 bit patterns. Not part of any build,
CI or runtime path.

```python
# One-off f64 reference for the conv2d gate (stdlib only): Conv2d(kernel 3x3, padding 1,
# stride s, bias), NCHW-style (C_in=2, C_out=2, H=5, W=6).
import struct, sys
def bits(x): return struct.unpack('>q', struct.pack('>d', float(x)))[0]
CI, CO, H, W = 2, 2, 5, 6
def x(c, y, xx): return ((c * 7 + y * 5 + xx * 3) % 11 - 5) / 4.0
def w(o, c, ky, kx): return ((o * 5 + c * 3 + ky * 2 + kx * 7) % 9 - 4) / 5.0
def b(o): return o * 0.5 - 0.25
def out_len(n, s): return (n + 2 - 3) // s + 1
def conv(s):
    oh, ow = out_len(H, s), out_len(W, s)
    out = []
    for o in range(CO):
        for oy in range(oh):
            for ox in range(ow):
                acc = 0.0
                for c in range(CI):
                    for ky in range(3):
                        for kx in range(3):
                            py, px = oy * s + ky - 1, ox * s + kx - 1
                            if 0 <= py < H and 0 <= px < W:
                                acc += w(o, c, ky, kx) * x(c, py, px)
                out.append(acc + b(o))
    return oh, ow, out
if len(sys.argv) > 1:
    print("python", sys.version.split()[0]); print("s1", conv(1)[:2], "s2", conv(2)[:2])
    print("s2 vals", conv(2)[2][:6])
```

## Qualification limits

A 2 x 2 channel, 5 x 6 fixture, not 480 channels over 128 x T, and no real weights
(the real `conv2d1.weight` is BF16, see `bf16.kotoba`). Not covered: the GELU after
each convolution, the `conv_out` projection (896 x 7680), the 18 transformer
layers, dilation or other kernel sizes. The Amu CLI is an unmerged candidate. No
CLJK, EDN, JVM or Node was used; the reference is Python, as stated.
