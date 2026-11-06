# Itonami ggml weight decoding native gate — 2026-11-06 (JUN-874)

`kotoba/itonami/ggml_dequant.kotoba` decodes the weight formats the whisper
path needs first: IEEE binary16 to f64 (exact, subnormals included; inf and nan
refused) and one `block_q8_0` (`{ half d; int8 qs[32] }`, 34 bytes in ggml
memory order) to `d * qs[i]`. The seed cannot read bytes (`bytes-*` give
`E2101`/`E2104`), so an element or block arrives as a hex string of its bytes,
the same string route as the log-mel frame; halves are little-endian. A block is
valid only when it is exactly 68 hex characters (every one checked) and its
scale is finite. q5_0, q4_x and K-quant blocks are not done, and nothing reads a
model file.

## Pinned inputs

- `kotoba/itonami/ggml_dequant.kotoba`, SHA-256
  `c3f22e7c3f189346d8e6a6225c0ae41796369e6045d142dfb031e6fcb429e8d5`.
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.

## Result (2026-11-06)

```sh
bin/check-ggml-dequant-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `50`: 10 half values
(zero, negative zero, 1.0, -2.5, 65504, the smallest subnormal, the smallest
normal, 0x3555, the largest subnormal, -65504) equal to the reference **bit for
bit**, 3 non-finite halves refused, the largest finite half accepted, 32 block
values bit-equal to `d * q`, and 4 block validity checks (valid accepted;
short, bad scale and a non-hex character mid-block refused). Seven mutations each
stopped the gate returning 50: sign threshold (49), subnormal exponent (48),
normal bias (13), finite limit (47), int8 sign (33), byte order (19), hex check
range (49). A source changed after locking was refused.

## One-off reference (not a gate input)

Python 3.15.0, stdlib only, `python3 -I oracle.py show`, 2026-11-06. The half
values come from CPython's own binary16 codec (`struct` format `e`), so the
reference is independent of the bit rules written in the gate. Not part of any
build, CI or runtime path.

```python
# One-off reference for the ggml dequantise gate (stdlib only). IEEE half
# decoding uses struct's 'e' format (CPython's own binary16 codec), not a
# re-statement of the bit rules; block_q8_0 is {half d; int8 qs[32]} (ggml
# memory order, d little-endian) and dequantises to d * q.
import struct, sys
def bits(x): return struct.unpack('>q', struct.pack('>d', float(x)))[0]
def half(h): return struct.unpack('<e', struct.pack('<H', h))[0]
F16 = [0x0000, 0x8000, 0x3C00, 0xC100, 0x7BFF, 0x0001, 0x0400, 0x3555, 0x03FF, 0xFBFF]
NONFINITE = [0x7C00, 0xFC00, 0x7E00]
D_BITS = 0x2E66
Q = [((i * 37 + 11) % 256) - 128 for i in range(32)]
def block_hex():
    return "".join(format(b, "02x") for b in [D_BITS & 255, D_BITS >> 8] + [(q + 256) % 256 for q in Q])
def block_values():
    d = half(D_BITS)
    return [d * q for q in Q]
if len(sys.argv) > 1:
    print("python", sys.version.split()[0])
    print("F16", [(hex(h), half(h)) for h in F16])
    print("d", half(D_BITS), "block", block_hex())
    print("vals", block_values()[:4])
```

## Qualification limits

One fixed block and ten halves, not a real whisper tensor. Not covered: q5_0
and other quantisations, reading a ggml/GGUF file or its header and tensor
table, f16 matrices at scale, byte input (the hex-string route is a stand-in),
model source and digest (owner decision). The Amu CLI is an unmerged candidate.
No CLJK, EDN, JVM or Node was used; the reference is Python, as stated.
