# Real speech through the native Qwen3-ASR audio encoder (JUN-925, 2026-11-06)

The native log-mel front end (JUN-866..880) and the native audio encoder (JUN-922, JUN-923) are
now one path on real audio.

Input: `test/native/speech_clip.hex`, 3840 samples (0.24 s) of 16 kHz speech, each as 4 hex digits
of the int16 sample + 32768. It was cut from `say -v Samantha -o speech.aiff "The quick brown fox
jumps over the lazy dog."` (macOS 26.4) converted with `afconvert -f WAVE -d LEI16@16000 -c 1`
(39,946 samples, WAV sha256 `904c0397...266d`), starting at sample 6400, the loudest 0.24 s window
(rms 6668). The macOS tools only made the input; the clip file is the pinned input of the gate.

Stages, each a separate native guest:
1. `audio_mel_cli`: reflect padding of 200 samples on both sides, 400-sample periodic Hann frames
   with hop 160 (24 frames; the 25th frame of a centred STFT is dropped, as whisper does), 128
   Slaney mel bins (`itonami.log-mel`), log10 with a 1e-10 floor, the clamp at (maximum - 8), then
   (x + 4) / 4: 128 x 24 values, index bin * 24 + frame, 0.8 s.
2. `audio_front_cli <model> <mel>`: three conv2d blocks, conv_out, positions (8 s).
3. `audio_layer_step_cli` x 18 (10 s in all).
4. `audio_tail_cli`: ln_post, proj1, GELU, proj2: three 1024-wide embeddings (0.4 s).

Gate: `bin/check-audio-asr-selfhost <cli> <out-dir> <model.safetensors> <speech_clip.hex>`, 21
checks: the sum and three values after each of the four stages (16) and five refusals (no clip, a
clip one character short, a non-hex character, a mel one character short, a missing model: exit 2,
nothing on stdout). Expected values come from a one-off CPython float64 pipeline of the same
definition (direct DFT, the Slaney filterbank written from the librosa definition, struct/json
weights, `math.erf`, `math.sin/cos/exp`; 143 s). Measured agreement (relative, absolute below
magnitude 1), worst 2.7e-14: mel 0 to 7e-15, front end 1.5e-16 to 3.5e-15, after 18 layers 6e-16 to
2.7e-14, tail 1.7e-16 to 1.7e-14. The tolerance is 1e-11. Eight mutations (reflect pad, pad offset,
hop size, bin layout, bin count, max clamp, normalisation, the front end ignoring the mel) are
caught; a source changed after locking is refused.

Not claimed: equality with the HF `WhisperFeatureExtractor`. The clip is not padded to 30 s (the
real extractor pads, so the maximum used by the clamp and the reflect padding at the end of a
padded signal can differ), and the position table is float64 where the model's is float32. Not
covered: clips longer than 24 frames (chunking, the windowed attention of long audio), the language
model and any text output, a node, the agent turn, the launcher. The gate needs the 1.88 GB model.
