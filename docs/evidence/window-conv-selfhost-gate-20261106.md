# Itonami window framing and conv1d native gate — 2026-11-06 (JUN-868)

`kotoba/itonami/window_conv.kotoba` holds the index rules the whisper encoder
front end needs around log-mel, plus a scalar convolution check:

- 30-second window: 480000 samples give 3000 frames of 400 samples at hop 160
  after `torch.stft`'s center padding, i.e. 200 samples of reflection on each
  side. `frame-source-index` maps (frame t, sample n) to the source sample the
  host must place there; `reflect-index` is the no-edge-repeat reflection;
  `frame-count` is len/160.
- Conv1d, kernel 3, padding 1: `conv-out-len` and `conv-tap` (input position
  read by tap k of output t, or -1 in the padding), plus a 2-in/2-out channel
  fixture of length 9 convolved at stride 1 (9 outputs per channel) and stride 2
  (5 outputs per channel), with bias.

The seed has no vectors, so the convolution is checked on an analytic fixture
and a host or later unit drives it over real rows.

## Pinned inputs

- `kotoba/itonami/window_conv.kotoba`, SHA-256
  `a8549b6271868e354f0777f4138bdb62864da1680f977afe23339063d6ae798d`.
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.

## Result (2026-11-06)

```sh
bin/check-window-conv-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `46`: 5 reflect cases,
4 frame mappings (including both ends of a 480000-sample signal), 2 frame
counts, 3 output lengths, 4 taps, 18 stride-1 and 10 stride-2 convolution
outputs within 1e-9. Six mutations each stopped the gate returning 46: reflect
upper bound (43), pad offset 200 -> 199 (42), hop 160 -> 161 (44), output
length without +1 (43), tap offset (15), weight index (19). A source changed
after locking was refused. The first run returned 45 because one hand-written
expectation was wrong (`conv-tap 4 2 2 9` is -1, not 9); the code was right
and the expectation was corrected to the reference, not the reverse.

## One-off reference (not a gate input)

Python 3.15.0, stdlib only, `python3 -I oracle.py show`, 2026-11-06. It states
the reflection rule of `torch.stft(center=True)` and the Conv1d definition; it
is not the torch package (not installed). Its values are pasted into the gate.

```python
# One-off reference for the window/conv gate (stdlib only): torch.stft center
# reflect-pad index rule (n_fft 400, hop 160) and Conv1d(k=3, pad=1, stride s).
import struct, sys
def bits(x): return struct.unpack('>q', struct.pack('>d', float(x)))[0]
def reflect(i, n):
    if i < 0: return -i
    if i >= n: return 2 * (n - 1) - i
    return i
def frame_src(t, n, ln): return reflect(160 * t + n - 200, ln)
C_IN, C_OUT, L = 2, 2, 9
def x(c, t): return ((c * 7 + t * 3) % 11 - 5) / 4.0
def w(o, c, k): return ((o * 5 + c * 3 + k * 2) % 7 - 3) / 5.0
def b(o): return o * 0.5 - 0.25
def conv(stride):
    out_len = (L + 2 - 3) // stride + 1
    out = []
    for o in range(C_OUT):
        for t in range(out_len):
            acc = 0.0
            for c in range(C_IN):
                for k in range(3):
                    p = t * stride + k - 1
                    if 0 <= p < L:
                        acc += w(o, c, k) * x(c, p)
            out.append(acc + b(o))
    return out_len, out
if len(sys.argv) > 1:
    print("python", sys.version.split()[0])
    print("reflect", [reflect(i, 10) for i in (-3, 0, 9, 10, 12)])
    print("frame_src", frame_src(0, 0, 480000), frame_src(0, 200, 480000), frame_src(2999, 399, 480000), frame_src(1, 5, 480000))
    print("conv1", conv(1)); print("conv2", conv(2))
```

## Qualification limits

Agreement is with these formulas on fixed cases, not with torch, whisper.cpp or
real audio. Not covered: a host driver that assembles frames from audio using
`frame-source-index`, the encoder's channel counts (80 -> d_model), GELU
between the convolutions (see `nn_ops.kotoba`), weights, GEMM, attention.
The Amu CLI is an unmerged candidate. No CLJK, EDN, JVM or Node was used; the
reference is Python, as stated.
