# Itonami log-mel native scalar core gate — 2026-11-06 (JUN-866)

`kotoba/itonami/log_mel.kotoba` is the first native step of speech
recognition: whisper's log-mel front end for one 400-sample frame. The pinned
Amu seed subset has no sin/cos/exp/log and no vectors (measured with
`amu check`: `E2101 unknown or unsupported form` for `f64-cos-bounded`,
`f64-log-bounded`, `f64-exp-bounded` and `vector-f64-conj`; `[x :f64] :f64`
gives `E2104`; float literals longer than about 12 characters give
`E2102`). So the math is built from f64 add/sub/mul/div on bit patterns held
in i64: cos/sin of a rational turn by integer quarter-turn reduction and a
12-term series, a 30-term exp series (|x| <= 4), and ln by halving into
[1/sqrt2, sqrt2) plus a 2*atanh series. On top of it: periodic Hann window,
Slaney mel scale and filterbank weights (80 mels, 0-8000 Hz, sr 16000,
n_fft 400, librosa htk=False norm=slaney), log10 with the 1e-10 floor and
whisper's normalisation.

The direct-DFT power bin runs on a built-in synthetic frame (two tones and a
quadratic chirp) because a frame cannot be passed in until the seed admits
vectors or byte reads. That driver is the part a later unit replaces.

## Pinned inputs

- `kotoba/itonami/log_mel.kotoba`, SHA-256
  `01a83e67602e1270c2b0637d504f3fbe827eeabfd9a8fb493d9990e320805bef`.
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.
- Host: macOS Apple Silicon.

## Reproduction

```sh
bin/check-log-mel-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Measured 2026-11-06: direct, source-locked `run` and artifact `run` all returned
`83` (80 log-mel values within 1e-6 of the reference, plus 3 normalised
values), in under one second. Negative controls (each changes the source and
must stop returning 83): Hann coefficient 0.5 -> 0.46 returned 1; the sign of
the second quadrant returned 0; ln series cut to 1 term returned 14; filter
area norm 2 -> 1 returned 0. A source changed after locking was refused with
`EINPUT a locked project source`. Module lines in the lock must be sorted by
name (`itonami.log-mel` before `itonami.log-mel-gate`); an unsorted lock is
refused with `ELOCK`.

## One-off reference (not a gate input)

Expected values were produced once by this stdlib-only f64 script (Python
3.15.0, 2026-11-06, run as `python3 -I oracle.py kotoba`) that follows
`librosa.filters.mel` and `whisper.audio.log_mel_spectrogram` per frame. It
is a re-statement of the formulas, not the librosa or whisper packages
(neither was installed), and its constants are pasted into the gate. It is not
part of any build, CI or runtime path.

```python
# One-off f64 reference for the whisper log-mel frame (stdlib only).
# librosa.filters.mel(sr=16000,n_fft=400,n_mels=80,fmin=0,fmax=8000,htk=False,norm='slaney')
# whisper.audio.log_mel_spectrogram per-frame steps.
import math, sys
SR, NFFT, NMELS = 16000, 400, 80
NB = NFFT // 2 + 1
F_SP = 200.0 / 3.0
LOGSTEP = math.log(6.4) / 27.0
def hz2mel(f):  return f / F_SP if f < 1000.0 else 15.0 + math.log(f / 1000.0) / LOGSTEP
def mel2hz(m):  return F_SP * m if m < 15.0 else 1000.0 * math.exp(LOGSTEP * (m - 15.0))
lo, hi = hz2mel(0.0), hz2mel(8000.0)
mel_f = [mel2hz(lo + i * (hi - lo) / (NMELS + 1)) for i in range(NMELS + 2)]
fdiff = [mel_f[i + 1] - mel_f[i] for i in range(NMELS + 1)]
fft_f = [k * SR / NFFT for k in range(NB)]
W = []
for i in range(NMELS):
    row = []
    for k in range(NB):
        lower = -(mel_f[i] - fft_f[k]) / fdiff[i]
        upper = (mel_f[i + 2] - fft_f[k]) / fdiff[i + 1]
        row.append(max(0.0, min(lower, upper)) * 2.0 / (mel_f[i + 2] - mel_f[i]))
    W.append(row)
def frame():
    return [0.5 * math.sin(2 * math.pi * ((n % 16)) / 16) + 0.25 * math.sin(2 * math.pi * ((3 * n) % 16) / 16) + 0.2 * math.sin(2 * math.pi * ((n * n) % 400) / 400) for n in range(NFFT)]
def hann(n): return 0.5 - 0.5 * math.cos(2 * math.pi * n / NFFT)
x = frame()
xw = [x[n] * hann(n) for n in range(NFFT)]
P = []
for k in range(NB):
    re = sum(xw[n] * math.cos(2 * math.pi * ((k * n) % NFFT) / NFFT) for n in range(NFFT))
    im = sum(xw[n] * math.sin(2 * math.pi * ((k * n) % NFFT) / NFFT) for n in range(NFFT))
    P.append(re * re + im * im)
E = [sum(W[m][k] * P[k] for k in range(NB)) for m in range(NMELS)]
L = [math.log10(max(e, 1e-10)) for e in E]
mx = max(L)
N = [(max(v, mx - 8.0) + 4.0) / 4.0 for v in L]
if len(sys.argv) > 1 and sys.argv[1] == "kotoba":
    print(" ".join(repr(v) for v in L))
    print("MAX", repr(mx))
    print(" ".join(repr(v) for v in N))
else:
    print("python", sys.version.split()[0], "bins", NB, "peak mel", L.index(mx), "max", mx, "min", min(L))
```

## Qualification limits

Agreement is with the formulas above, to 1e-6 on one synthetic frame, not with
the whisper package, real audio, or a model. Not covered: a frame supplied by
the host (needs vectors or byte reads in the seed), the 30-second 3000-frame
window and reflect padding, FFT speed (the DFT is O(N^2)), the floor value
(no bin of the test frame reaches it), f16 weights, the encoder and decoder.
The Amu CLI is an unmerged candidate. No CLJK, EDN, JVM or Node was used; the
reference script is Python, as stated.
