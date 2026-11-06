# Itonami BF16 linear steps and Qwen3 MLP native gate — 2026-11-06 (JUN-884)

`kotoba/itonami/qwen_mlp.kotoba` has the weight-row step and the gated MLP of Qwen3
(`y = down(silu(gate(x)) * up(x))`, hidden 1024 to intermediate 3072 to 1024 in
Qwen3-ASR's LLM, weights BF16). A weight row arrives as a hex string of its BF16
elements and is decoded while it is multiplied in (`bf16-mac` adds `w[j] * x` to an
accumulator, using `bf16.kotoba`); SiLU is `qwen_ops`'. The seed has no vectors, so
the gate checks a small analytic MLP (hidden 4, intermediate 3, three input vectors,
weights exact in bf16) and three **real** weight rows.

## Pinned inputs

- `kotoba/itonami/qwen_mlp.kotoba`, SHA-256 `e6eca61967f49ac304b6b9e73842ef10edb43e40ed3ea2226a7c86085480d252`; built on
  `bf16.kotoba` (`09de868917eb1ba9764794cdd5e3c890e462ec22709d9ede4cf1e1554a33549b`), `qwen_ops.kotoba` (`a5a8744046a81edd21d0740395e874c89ed518e24092a5e889c78b2b2ba51576`),
  `nn_ops.kotoba` (`4693d227291a31b8c59378b0ab114715a983dfbfd17308d5b478181793a3604d`), `log_mel.kotoba` (`daf0d273329a7b5a05aef4255b985f2e1c031c74f32596e9578add46980b1f5e`) and
  `ggml_dequant.kotoba` (`c3f22e7c3f189346d8e6a6225c0ae41796369e6045d142dfb031e6fcb429e8d5`).
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.
- Real weights: the first 16 elements of rows 0, 1 and 2 of
  `thinker.model.layers.0.mlp.gate_proj.weight` (shape [3072, 1024], BF16) read from
  the downloaded `Qwen/Qwen3-ASR-0.6B` `model.safetensors` (revision
  `5eb144179a02`, SHA-256 matched the Hub's LFS digest when fetched, JUN-877).

## Result (2026-11-06)

```sh
bin/check-qwen-mlp-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `15`: the three real-row dot
products with an analytic input (16 columns each) and the 12 outputs of the small MLP
(3 inputs x 4 outputs), each within 1e-9 of the reference. Seven mutations each
stopped the gate returning 15: the byte offset of an element (0), no SiLU (3), SiLU on
the up branch instead of the gate (3), the down projection one term short (7), one
column short of 16 (12), the input vector index (7), the up row replaced by the gate
row (3). A source changed after locking was refused.

## One-off reference (not a gate input)

Python 3.15.0, stdlib only, `python3 -I oracle.py show`, 2026-11-06. bf16 is decoded
by struct's own IEEE binary32 decoder (bf16 to f32 to f64 is exact); the MLP is
written out in nested loops with `math.exp`. The three real rows are read from the
downloaded safetensors file by the script and pasted into the module as hex. Not part
of any build, CI or runtime path.

```python
# One-off reference for the BF16-linear / SiLU-MLP gate (stdlib only). bf16 decoding is struct's
# own IEEE binary32 decoder on bytes 00 00 <lo> <hi> (bf16 -> f32 -> f64 is exact). Real rows come
# from the downloaded Qwen/Qwen3-ASR-0.6B model.safetensors (revision 5eb144179a02...).
import json, math, struct, sys
def bits(x): return struct.unpack('>q', struct.pack('>d', float(x)))[0]
def bf16(h): return struct.unpack('<f', b'\x00\x00' + struct.pack('<H', h))[0]
def to_bf16_bits(x): return struct.unpack('<I', struct.pack('<f', x))[0] >> 16   # truncate (inputs below are exact in bf16)
PATH = "/Users/junkawasaki/github/workspaces/claude/jun-877-models/Qwen__Qwen3-ASR-0.6B/model.safetensors"
TENSOR = "thinker.model.layers.0.mlp.gate_proj.weight"
NCOL = 16
def real_rows(rows=(0, 1, 2)):
    with open(PATH, "rb") as f:
        n = struct.unpack("<Q", f.read(8))[0]
        hdr = json.loads(f.read(n)); hdr.pop("__metadata__", None)
        meta = hdr[TENSOR]; assert meta["dtype"] == "BF16"
        cols = meta["shape"][1]
        out = []
        for r in rows:
            f.seek(8 + n + meta["data_offsets"][0] + 2 * r * cols); out.append(f.read(2 * NCOL).hex())
    return meta["shape"], out
def xr(c): return ((c * 5 + 2) % 9 - 4) / 4.0            # input for the real-row dot
def hexrow(vals): return "".join(struct.pack('<H', to_bf16_bits(v)).hex() for v in vals)
# tiny MLP: hidden 4 -> intermediate 3 -> hidden 4, weights exactly representable in bf16
H, I = 4, 3
def wg(r, c): return ((r * 3 + c * 5 + 1) % 7 - 3) / 4.0
def wu(r, c): return ((r * 5 + c * 3 + 2) % 7 - 3) / 8.0
def wd(o, r): return ((o * 7 + r * 2 + 3) % 9 - 4) / 4.0
def xin(k, c): return ((k * 3 + c * 7 + 1) % 11 - 5) / 4.0
def silu(x): return x / (1.0 + math.exp(-x))
def mlp(k):
    x = [xin(k, c) for c in range(H)]
    g = [sum(bf16(to_bf16_bits(wg(r, c))) * x[c] for c in range(H)) for r in range(I)]
    u = [sum(bf16(to_bf16_bits(wu(r, c))) * x[c] for c in range(H)) for r in range(I)]
    a = [silu(g[r]) * u[r] for r in range(I)]
    return [sum(bf16(to_bf16_bits(wd(o, r))) * a[r] for r in range(I)) for o in range(H)]
def real_dots():
    shape, rows = real_rows()
    return shape, rows, [sum(bf16(int.from_bytes(bytes.fromhex(h)[2*c:2*c+2], 'little')) * xr(c) for c in range(NCOL)) for h in rows]
if len(sys.argv) > 1:
    shape, rows, d = real_dots()
    print("python", sys.version.split()[0], "tensor", TENSOR, shape); print("dots", d); print("mlp(0)", mlp(0))
```

## Qualification limits

16 of 1024 columns of 3 of 3072 rows, and a 4-3-4 toy MLP; not a full layer and not
Qwen3's own implementation. Not covered: the other projection matrices, the
residual connections and the norms around the MLP, accumulation order and precision
at 1024-3072 elements (the reference accumulates in f64, the real model in
bf16/f32), a vector or matrix API. The Amu CLI is an unmerged candidate. No CLJK,
EDN, JVM or Node was used at gate time; the reference is Python, as stated.
