# Itonami safetensors header reader native gate — 2026-11-06 (JUN-885)

`kotoba/itonami/safetensors.kotoba` reads a safetensors file's header: the first 8 bytes
are the header length (little-endian u64, `st-header-len` reads them from hex), then
that many bytes of JSON map each tensor name to `{"dtype", "shape", "data_offsets":
[begin, end]}` (offsets relative to the data area). The header arrives as a string and
is read by plain string search: the entry is found by its exact quoted name followed by
an object (so `layers.1` never matches `layers.10`), fields by key inside that entry (so
key order does not matter). `st-dtype`, `st-ndim` (0 for a scalar), `st-dim`, `st-begin`,
`st-end`; absent names give `-1` or an empty string. The seed has no JSON parser or byte
reader; the data area is not read here (the bf16 and ggml decoders read element bytes
from hex).

## Pinned inputs

- `kotoba/itonami/safetensors.kotoba`, SHA-256 `20562030183d7a09fd43303821ecab5ed9065275a1ad68494eb3bb456e9c8f75`; built on
  `ggml_dequant.kotoba` (`c3f22e7c3f189346d8e6a6225c0ae41796369e6045d142dfb031e6fcb429e8d5`).
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.
- The real header: the JSON of the downloaded `Qwen/Qwen3-ASR-0.6B` `model.safetensors`
  (revision `5eb144179a02`): 74,544 bytes, 612 tensors, SHA-256 of the JSON text
  `18240cd03795e0e0f1e4ed0a6f169c581af11ef81646d37779b6930d2209fa5f`; its 8-byte prefix is `3023010000000000`.

## Result (2026-11-06)

```sh
bin/check-safetensors-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `44`: five real tensors
(`conv2d1.bias`, `conv_out.weight`, `layers.1.fc1.weight`, `layers.10.fc1.weight`,
`embed_tokens.weight`; the name-prefix pair included) x six fields (dtype, ndim, dim 0,
dim 1, begin, end) equal CPython's `json` reading of the real header; two synthetic
entries (keys in a different order; a scalar with an empty shape); three absent-name
checks; and the header-length reads (the real prefix gives 74,544; a non-hex prefix is
refused). The gate's header is made of exact raw-text slices of the real one. Eight
mutations each stopped the gate returning 44: dtype offset (38), list start offset
(30), end-offset comma (37), ndim count (38), scalar ndim (43), little-endian scale
(43), entry end (13), dim index (35). A source changed after locking was refused.

**Full header, measured once outside the repo.** A scratch program with the **whole**
74,544-character header as one string literal and the same module answered 12 of 12
queries (dtype, ndim, begin, end of three tensors near the end of the file, including
`thinker.lm_head.weight` or `thinker.model.norm.weight` as present) correctly in 0.56 s
wall time; the 74 KB literal is not committed.

## One-off reference (not a gate input)

Python 3.15.0, stdlib only, `python3 -I oracle.py show`, 2026-11-06; CPython's `json`
on the real header. Not part of any build, CI or runtime path.

```python
# One-off reference for the safetensors header gate (stdlib only): the real header of the
# downloaded Qwen/Qwen3-ASR-0.6B model.safetensors is read with CPython's json; the gate's
# snippet is made of exact raw-text slices of that header (a name-prefix collision pair included).
import json, re, struct, sys
PATH = "/Users/junkawasaki/github/workspaces/claude/jun-877-models/Qwen__Qwen3-ASR-0.6B/model.safetensors"
NAMES = ["thinker.audio_tower.conv2d1.bias", "thinker.audio_tower.conv_out.weight",
         "thinker.audio_tower.layers.1.fc1.weight", "thinker.audio_tower.layers.10.fc1.weight",
         "thinker.model.embed_tokens.weight"]
def load():
    with open(PATH, "rb") as f:
        raw8 = f.read(8)
        n = struct.unpack("<Q", raw8)[0]
        text = f.read(n).decode("utf-8")
    return raw8, n, text, json.loads(text)
def snippet(text):
    parts = []
    for nm in NAMES:
        m = re.search(r'"%s":\{[^{}]*\}' % re.escape(nm), text)
        parts.append(m.group(0))
    return "{" + ",".join(parts) + "}"
if len(sys.argv) > 1:
    raw8, n, text, hdr = load()
    print("python", sys.version.split()[0], "header bytes", n, "tensors", len(hdr) - 1)
    print("prefix hex", raw8.hex())
    s = snippet(text); print(len(s), s[:200])
    for nm in NAMES: print(nm, hdr[nm])
```

## Qualification limits

String search, not a JSON parser: it relies on the safetensors writer's compact form
(no spaces, no nested objects inside entries, names without escapes). The header comes
in as a string; getting it from a file needs a byte read the seed lacks. Not covered:
the `__metadata__` entry, reading tensor data, files above the string limits found so
far (74 KB works). The Amu CLI is an unmerged candidate. No CLJK, EDN, JVM or Node was
used at gate time; the reference is Python, as stated.
