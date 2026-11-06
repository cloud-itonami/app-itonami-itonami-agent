# Itonami bf16 decoding native gate — 2026-11-06 (JUN-879)

`kotoba/itonami/bf16.kotoba` decodes bfloat16 to f64 exactly. bfloat16 is the high
16 bits of an IEEE binary32 (1 sign, 8 exponent bits with bias 127, 7 mantissa
bits), so normal values are `(128 + m) * 2^(e - 134)`, subnormals `m * 2^-133`,
and exponent 255 (inf, nan) is refused. An element arrives as 2 little-endian
bytes in a hex string, read with `itonami.ggml-dequant`'s 16-bit reader. Qwen3-ASR
and Qwen3-TTS ship their weights in BF16 (612 and 402 tensors; the TTS speech
tokenizer is F32).

## Pinned inputs

- `kotoba/itonami/bf16.kotoba`, SHA-256
  `09de868917eb1ba9764794cdd5e3c890e462ec22709d9ede4cf1e1554a33549b`; built on
  `ggml_dequant.kotoba` (`c3f22e7c3f189346d8e6a6225c0ae41796369e6045d142dfb031e6fcb429e8d5`).
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.
- Real weights: the first 64 elements of `thinker.audio_tower.conv2d1.bias`
  (the tensor with the lowest data offset) read from the downloaded
  `Qwen/Qwen3-ASR-0.6B` `model.safetensors` (revision `5eb144179a02`, whose
  SHA-256 matched the Hub's LFS digest when it was fetched, JUN-877).

## Result (2026-11-06)

```sh
bin/check-bf16-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `79`: 10 special values
(zero, negative zero, 1.0, -1.0, the largest finite, the smallest subnormal, the
smallest normal, 0x00FF, 0x4049, the most negative finite) and the 64 real weights
equal the reference **bit for bit**, 3 non-finite elements refused, the largest
finite accepted, and an unreadable element refused. Six mutations each stopped the
gate returning 79: sign threshold (78), subnormal exponent (78), normal bias (8),
the implicit leading one (8), the finite limit (76), the exponent field (6). A
source changed after locking was refused.

## One-off reference (not a gate input)

Python 3.15.0, stdlib only, `python3 -I oracle.py show`, 2026-11-06. The reference
is struct's own IEEE binary32 decoder applied to the bytes `00 00 <lo> <hi>`
(bf16 to f32 to f64 is exact), independent of the bit rules in the gate. Not part
of any build, CI or runtime path.

```python
# One-off reference for the bf16 decode gate (stdlib only). bf16 -> f32 is "the high 16
# bits of an f32", so the independent reference is struct's own IEEE f32 decoder on
# bytes 00 00 <lo> <hi> (little-endian); f32 -> f64 is exact. Real weights come from the
# downloaded Qwen/Qwen3-ASR-0.6B model.safetensors (revision 5eb144179a02...).
import json, struct, sys
def bits(x): return struct.unpack('>q', struct.pack('>d', float(x)))[0]
def bf16(h): return struct.unpack('<f', b'\x00\x00' + struct.pack('<H', h))[0]
SPECIAL = [0x0000, 0x8000, 0x3F80, 0xBF80, 0x7F7F, 0x0001, 0x0080, 0x00FF, 0x4049, 0xFF7F]
NONFINITE = [0x7F80, 0xFF80, 0x7FC0]
PATH = "/Users/junkawasaki/github/workspaces/claude/jun-877-models/Qwen__Qwen3-ASR-0.6B/model.safetensors"
def real_hex(n_elems=64):
    with open(PATH, "rb") as f:
        n = struct.unpack("<Q", f.read(8))[0]
        hdr = json.loads(f.read(n)); hdr.pop("__metadata__", None)
        # first tensor by data offset
        name, meta = min(hdr.items(), key=lambda kv: kv[1]["data_offsets"][0])
        assert meta["dtype"] == "BF16"
        f.seek(8 + n + meta["data_offsets"][0])
        raw = f.read(2 * n_elems)
    return name, raw.hex()
if len(sys.argv) > 1:
    name, h = real_hex()
    print("python", sys.version.split()[0]); print("tensor", name); print("hex", h[:64], "...")
    vals = [bf16(int.from_bytes(bytes.fromhex(h)[2*i:2*i+2], "little")) for i in range(64)]
    print("first values", vals[:4], "min", min(vals), "max", max(vals))
```

## Qualification limits

64 elements of one small tensor (a bias), not a weight matrix, and nothing reads
the safetensors header or tensor table at run time. Not covered: F32 tensors,
matrices, the encoder and decoder structure. For the plan, the header of this
file (read, not executed) shows the audio tower is **conv2d**-based, not conv1d:
three 3x3 convolutions with 480 channels (`conv2d1.weight [480,1,3,3]`,
`conv2d2/3.weight [480,480,3,3]`), then `conv_out.weight [896,7680]` (7680 = 16 x
480, i.e. 128 mel / 8), 18 transformer layers (LayerNorm, 14-head attention
with q/k/v/out projections and biases, fc1 896 to 3584, fc2), `ln_post`, and
`proj1 [896,896]` / `proj2 [1024,896]` into the 1024-wide LLM. The conv1d of
`window_conv.kotoba` therefore does not apply to this model; a conv2d
(3x3, stride 2 per the 8x reduction) is the missing kernel. The Amu CLI is an
unmerged candidate. No CLJK, EDN, JVM or Node was used; the reference is Python,
as stated.
