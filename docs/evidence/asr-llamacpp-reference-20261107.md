# Qwen3-ASR: llama.cpp reference run on the mid-speech-cut clips (JUN-935, 2026-11-07)

Question: clips cut from a longer synthetic sentence were read by the native pipeline as language Japanese (katakana
rendering of English), with or without appended silence or a fade-out, while two other clips were read as English
(JUN-935, JUN-944). Is that the native pipeline or the model?

Method: a one-off local oracle (never CI, gate or runtime): `llama-mtmd-cli` 0.7.0 with the bf16 GGUF of
Qwen3-ASR-0.6B and its bf16 mmproj from ggml-org at a pinned revision, sizes and sha256 equal to the Hub values
(receipt). Greedy decoding (`--temp 0`), the prompt is only the media marker. Nine clips, all of them also run through
the native `itonami-transcribe`.

Result: the reference gives the same language as the native pipeline on all nine clips. Japanese on the seven clips the
native pipeline reads as Japanese (5.5, 6.0 and 6.5 s cuts, the offset cut, the cut with 0.25 s of silence, the cut with
a fade-out, and the first 7.0 s cut mid-word); English on the 8 s and the padded 7.0 s clip, with text identical to the
native transcript. So this is the model's behaviour on this audio, not a native defect. The Japanese texts are similar
failures but differ in detail from the native ones (different audio front end and numerics); they were not compared
token by token.

Not claimed: equality with the Hugging Face extractor or the original Python (llama.cpp has its own mel front end and
conversion); logit-level agreement; anything about other voices, sentences or real recordings; why the model prefers
Japanese on these clips. Exact commands, versions, digests and outputs: `asr-llamacpp-reference-20261107-receipt.txt`.
