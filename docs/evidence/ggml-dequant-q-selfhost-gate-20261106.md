# Itonami q4_0 / q5_0 native gate — 2026-11-06 (JUN-874)

`kotoba/itonami/ggml_dequant_q.kotoba` adds the two block formats most ggml
whisper models ship besides q8_0, in ggml's layouts:
`block_q4_0 { half d; uint8 qs[16] }` (18 bytes, 36 hex characters) and
`block_q5_0 { half d; uint8 qh[4]; uint8 qs[16] }` (22 bytes, 44 hex characters).
Output j is the low nibble of `qs[j]`, output j+16 the high nibble; q4_0 is
`(nibble - 8) * d`; q5_0 takes a fifth bit from the little-endian uint32 `qh`
(bit j for the low half, bit j+16 for the high half) and is
`((nibble | bit<<4) - 16) * d`. Blocks arrive as hex strings in memory order. A
block is valid only when it has the exact length, every character is hex and the
scale half is finite. Built on `itonami.ggml-dequant`; the products are exact
(at most 16 significant bits), so they equal ggml's float32 results.

## Reference: ggml's own code

This unit's reference is not a re-statement. `dequantize_row_q4_0` and
`dequantize_row_q5_0` from the local llama.cpp checkout
(`ggml/src/ggml-quants.c`, commit `749db7f034d43d6de01a01f6d8c872d02afa6c7b`) were compiled with a one-off C harness
(`cc -O1 -I ggml/include -I ggml/src h.c ggml/src/ggml-quants.c -lm`, Apple clang,
macOS arm64, four unreferenced link stubs) and run on the six fixture blocks; its
float32 outputs (`%.9g`, re-read as float32) are pasted into the module as f64
bit patterns. The harness is not part of any build, CI or runtime path:

```c
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include "ggml-quants.h"
static int hexv(char c){return c<='9'?c-'0':(c|32)-'a'+10;}
int main(int argc, char** argv){
  /* argv[1]=kind (q4_0|q5_0|q8_0), argv[2]=hex block */
  const char* h = argv[2]; size_t n = strlen(h)/2; uint8_t b[64];
  for (size_t i=0;i<n;i++) b[i]=hexv(h[2*i])*16+hexv(h[2*i+1]);
  float y[32];
  if(!strcmp(argv[1],"q4_0")) dequantize_row_q4_0((const block_q4_0*)b,y,32);
  else if(!strcmp(argv[1],"q5_0")) dequantize_row_q5_0((const block_q5_0*)b,y,32);
  else dequantize_row_q8_0((const block_q8_0*)b,y,32);
  for(int i=0;i<32;i++) printf("%.9g%c",y[i],i==31?'\n':' ');
  return 0;
}
/* link stubs: not reached by the dequantize_row_q4_0/q5_0/q8_0 paths */
#include <stdlib.h>
#include "ggml.h"
void ggml_abort(const char * f, int l, const char * fmt, ...) { (void)f; (void)l; (void)fmt; abort(); }
size_t ggml_row_size(enum ggml_type t, int64_t ne) { (void)t; (void)ne; abort(); }
const char * ggml_type_name(enum ggml_type t) { (void)t; return "stub"; }
size_t ggml_type_size(enum ggml_type t) { (void)t; abort(); }
```

## Pinned inputs

- `kotoba/itonami/ggml_dequant_q.kotoba`, SHA-256
  `4b32b8064dc9195f181b46a694eefba3e86aaaa36ce28e1a5cefef1ec8903f21`; built on
  `ggml_dequant.kotoba` (`c3f22e7c3f189346d8e6a6225c0ae41796369e6045d142dfb031e6fcb429e8d5`).
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.

## Result (2026-11-06)

```sh
bin/check-ggml-dequant-q-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `204`: 96 q4_0 and 96 q5_0
values (three blocks each: a mixed block, a `qh` of all ones and a `qh` of all
zeros for q5_0, different scales including a negative one) bit-equal to ggml's
output, 6 valid blocks, and 6 refusals (short, non-hex, non-finite scale for each
kind). Seven mutations each stopped the gate returning 204: q4 bias (108), high
nibble (110), q5 high-half bit index (194), q5 bit weight (156), qh byte order
(200), scale finiteness (202), q4 length (201). A source changed after locking
was refused.

## Qualification limits

Six fixed blocks, not a real whisper tensor. Not covered: q5_1, q4_1, K-quants,
reading a ggml/GGUF file or its header and tensor table, the matmul over
quantised rows, byte input (the hex-string route is a stand-in). The Amu CLI is
an unmerged candidate. No CLJK, EDN, JVM or Node was used; the C harness is the
reference and is not a gate path.
