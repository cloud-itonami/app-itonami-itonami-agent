# Native speech-to-text on the real Qwen3-ASR-0.6B weights (JUN-928, 2026-11-06)

The first end-to-end native transcription: one second of speech in, text out, every stage a native
guest built with the pinned Amu multi-module project CLI and run on this Mac, state kept in f64 files
in the directory granted on fd 3.

Input: `test/native/speech_clip_1s.hex`, 16,000 samples (1 s) of 16 kHz speech, each as 4 hex digits of
the int16 sample + 32768. It is the first second of `say -v Samantha -o speech.aiff "The quick brown
fox jumps over the lazy dog."` (macOS 26.4) converted with `afconvert -f WAVE -d LEI16@16000 -c 1`
(39,946 samples, WAV sha256 `904c0397...266d`). What is spoken in that second: "The quick brown fox".

Path (times on this Mac):
1. `audio_enc_cli mel`: whisper log-mel, 128 x 100 (2.2 s).
2. `audio_enc_cli front`: three conv2d blocks, conv_out, positions: 13 tokens x 896 (34 s, 265 MB).
3. `audio_enc_cli layer` x 18, full attention over the 13 tokens (23 s).
4. `audio_enc_cli tail`: ln_post, proj1, GELU, proj2: 13 x 1024 embeddings (0.2 s).
5. `qwen_decoder_cli embedaudio`: the 28-token chat prompt
   `<|im_start|>system\n<|im_end|>\n<|im_start|>user\n<|audio_start|>` + 13 x `<|audio_pad|>` +
   `<|audio_end|><|im_end|>\n<|im_start|>assistant\n` (from `chat_template.json`; the ids were looked up
   in `vocab.json` and `tokenizer_config.json`), with the 13 embedding rows written over the audio pads.
6. `qwen_decoder_cli layer` x 28 over the 28 tokens (2 min), then greedy decoding with the key/value
   caches, one token at a time (about 15 s each): `head` over five vocabulary slices, `embed`, 28 layers.
7. `qwen_detok_cli`: the ids back to text.

Result: ids `11528 6364 151704 785 3974 13876 38835 13 151645`, text
`language English<asr_text>The quick brown fox.<|im_end|>`: the model names the language, opens the
transcript and writes the words that are in the clip, then stops at `<|im_end|>`. (The full sentence in
the file has more words; only the first second was given.)

What was and was not checked against an independent reference:
- the encoder: a one-off CPython float64 pipeline of the same definition (direct DFT, Slaney filterbank
  from the librosa definition, struct/json weights, `math.erf/sin/cos/exp`; 8 minutes) agrees with the
  native mel, front end, 18 layers and tail on the sum and three values each, to 1e-9 (measured relative
  differences: 3.4e-15 on the mel sum, 5.2e-16 on the sum after 18 layers, 8.5e-15 on the tail sum and
  3e-16 to 1.2e-15 on three tail values; in ulps 17, 3, 45 and 45 to 2768, the large counts being values
  close to zero);
- the decoder: against CPython float64 by `check-qwen-decoder-selfhost` (JUN-926) on text prompts, not
  with audio (CPython needs about half an hour for the 28-token prefill);
- the vocabulary reader: 91 vocabulary entries (quotes, backslash, control bytes, multi-byte text),
  compared byte for byte with an independent read of `vocab.json` (`test/native/detok_cases.txt`);
- the transcript: against the words actually spoken.
The first token's logit is pinned at the value the native code computes (4629172127120524673): a
regression pin, because the id of the first token (`language`) does not depend on the audio. A
mutation that shifts the audio rows by half a row (`* ai 512`) still gives `language`, which is why the
logit is compared; `* ai 1023` was a smaller shift and also passed on the id alone.

Gate: `bin/check-asr-transcribe-selfhost <cli> <out-dir> <model.safetensors> <vocab.json> <clip.hex>`,
26 checks (16 encoder values, the first token and its logit, the ids, the text, the vocabulary fixture,
5 refusals) and 10 mutations caught (vocabulary: two byte-table mutations, the quote parity, the escape
skip; encoder: the mel reflection, the conv stride, the feature order, the attention scale; decoder:
the audio row offset and the audio pad id). A source changed after locking is refused. `qwen_vocab` and
`qwen_detok_cli` read `vocab.json` (2.8 MB) in 11 requests of 256 KiB.

Not claimed: equality with the HF feature extractor (the real one pads the audio to 30 s, which changes
the maximum used by the max-8 clamp and the reflection at the end), with the original model beyond this
transcript (mrope is treated as plain RoPE; its handling of audio positions was not checked), or
behaviour on longer audio: only one 100-frame chunk, 13 audio tokens and a prompt of at most 64 tokens
are supported. The 91-entry vocabulary fixture is not the whole vocabulary. The gate needs the 1.88 GB
model and is not CI-runnable. The Qwen3-ASR code path was never run in Python or llama.cpp here.

Findings for the Amu seed gaps in JUN-876: a module can pass `:vector-i64` values to another (shared
helper module `itonami.nn-vec`); a command substitution in the shell drops trailing newlines, so a
guest's output must go to a file when the bytes matter; the lock's module lines must be in byte order
(`itonami.audio-enc` before `itonami.bf16`), which `sort` gives only under `LC_ALL=C`.
