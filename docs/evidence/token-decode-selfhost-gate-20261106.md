# Itonami token decoding native gate — 2026-11-06 (JUN-875)

`kotoba/itonami/token_decode.kotoba` turns the decoder's token ids back into
bytes. whisper's text tokens (ids below 50257) are UTF-8 byte strings and one
character can be split across tokens; id 50257 is end of text (decoding stops);
ids 50258 up to 51864 are control tokens (language, task, timestamps) and are
skipped; an id at or above 51865, an id without a vocabulary entry, or a
malformed id list gives `!`. The vocabulary is one string of `\n<id>=<hex bytes>`
entries and ids arrive as a comma-separated decimal string. `utf8-valid?` is a
strict UTF-8 check over a hex string (no overlongs, surrogates or code points
above U+10FFFF). Built on `itonami.ggml-dequant`'s `byte-at`.

The seed has no primitive that builds a string from bytes or code points
(`string-from-code-point`, `string-char` and `utf8-encode` give `E2101`), so
the decoder's result is a hex string and turning it into text is left to the
caller until Amu adds one.

## Pinned inputs

- `kotoba/itonami/token_decode.kotoba`, SHA-256
  `75875a660dfb61f4ed5f9020fe6679fe1669f7bbed2a59bc814685019aa7bde0`; built on
  `ggml_dequant.kotoba` (`c3f22e7c3f189346d8e6a6225c0ae41796369e6045d142dfb031e6fcb429e8d5`).
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.

## Result (2026-11-06)

```sh
bin/check-token-decode-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Direct, source-locked and artifact runs all returned `21`: a Japanese sentence
with an emoji and ASCII (`こんにちは、世界🙂 ok`) split into 11 tokens whose
boundaries cut characters (the first token alone is the ill-formed `e381`),
preceded by four control tokens and followed by end of text and a stray token,
decodes to the exact UTF-8 bytes; stop-at-end, control-skipping, unknown id,
empty element, non-digit element and empty list behave as stated; 6 well-formed
and 7 ill-formed byte strings (truncated, lone continuation, overlong, surrogate,
lead above F4, C0, code point above U+10FFFF) agree with CPython's strict
decoder. Eight mutations each stopped the gate returning 21: id range (20),
end of text (19), control skip (19), concatenation order (20), overlong lead
(20), surrogate range (20), max code point (20), lead range (20). A source
changed after locking was refused. The first run returned 20: the gate caught a
real defect, ids above the vocabulary size were skipped as control tokens
instead of refused; fixed by the `51865` bound.

## One-off reference (not a gate input)

Python 3.15.0, stdlib only, `python3 -I oracle.py show`, 2026-11-06. UTF-8
validity comes from CPython's strict decoder (independent of the byte rules
written in the gate); the decode rule is a re-statement. Not part of any build,
CI or runtime path.

```python
# One-off reference for the token decode gate (stdlib only): the decode rule
# (specials >= 50257 skipped, 50257 = end of text stops, text tokens' bytes
# concatenated) and CPython's strict UTF-8 decoder as the validity oracle.
import sys
TEXT = "こんにちは、世界🙂 ok"
RAW = TEXT.encode("utf-8")
CH = [2, 3, 4]
chunks, p, k = [], 0, 0
while p < len(RAW):
    n = CH[k % 3]; chunks.append(RAW[p:p + n]); p += n; k += 1
IDS_TEXT = list(range(100, 100 + len(chunks)))
VOCAB = "".join("\n%d=%s" % (i, c.hex()) for i, c in zip(IDS_TEXT, chunks)) + "\n"
SPECIAL = [50258, 50266, 50359, 50363]
SEQ = SPECIAL + IDS_TEXT + [50257, IDS_TEXT[0]]
EXPECT_HEX = RAW.hex()
def valid(h):
    try: bytes.fromhex(h).decode("utf-8", errors="strict"); return True
    except UnicodeDecodeError: return False
CASES = ["e38193e38293", "", "41", "f09f9980", "e381", "80", "e08080", "eda080", "f5808080", "c0af", "ed9fbf", "f48fbfbf", "f4908080"]
if len(sys.argv) > 1:
    print("python", sys.version.split()[0])
    print("chunks", len(chunks), "hex", EXPECT_HEX)
    print("seq", ",".join(map(str, SEQ)))
    print("cases", [(c, valid(c)) for c in CASES])
    print("first token valid alone:", valid(chunks[0].hex()), chunks[0].hex())
```

## Qualification limits

A 11-entry fixture vocabulary, not whisper's 51865-entry one (its source and
digest are an owner decision). Lookup scans the vocabulary string for each id,
fine for a fixture and not for the real vocabulary. Not covered: BPE encoding,
timestamp-token semantics, language and task token selection, suppress lists,
producing a text string. The Amu CLI is an unmerged candidate. No CLJK, EDN, JVM
or Node was used; the reference is Python, as stated.
