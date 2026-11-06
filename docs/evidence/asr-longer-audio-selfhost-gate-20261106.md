# Native transcription of a full sentence: several chunks (JUN-935, 2026-11-06)

`bin/check-asr-transcribe-selfhost` now transcribes two clips of the same utterance, `say -v Samantha
-o speech.aiff "The quick brown fox jumps over the lazy dog."` converted with
`afconvert -f WAVE -d LEI16@16000 -c 1` (39,946 samples; WAV sha256 `904c0397...266d`): its first second
(one chunk) and the whole sentence (40,000 samples, the last 54 zero-padded: 2.5 s).

Chunking (`itonami.audio-enc`, from the local llama.cpp `mtmd-audio.cpp` and the HF output-length
formula; the original Python was not read):
- N samples give T = N / 160 mel frames (whisper's centred STFT with the last frame dropped), the maximum
  for the max-8 clamp is taken over the clip (no padding to 30 s, as llama.cpp does);
- frames are cut into chunks of 100, the last one zero-padded to 100 columns (the model's padded
  chunk); every chunk goes through the three conv blocks (13 time steps);
- tokens: 13 per full chunk and ceil(L / 8) for a last chunk of L frames (50 frames: 25, 13, then 7);
  positions restart at 0 in every chunk; all tokens attend to all tokens (one window, up to 104 tokens);
- the decoder prompt carries as many `<|audio_pad|>` as there are tokens (15 + rows prompt tokens).

The 2.5 s clip: T = 250 frames, 3 chunks (100, 100, 50), 33 audio tokens, a 48-token prompt.
Result: ids `11528 6364 151704 785 3974 13876 38835 34208 916 279 15678 5562 13 151645`, text
`language English<asr_text>The quick brown fox jumps over the lazy dog.<|im_end|>`: every word of the
sentence. Times on this Mac: mel 3 s, front end 96 s (3 chunks), 18 layers 51 s, decoder prefill about 3.5
minutes, 13 decoded tokens at about 16 s.

Checks against an independent reference: the encoder's mel (four points of the valid 128 x 250 values),
front end, 18 layers and tail (sum and three values) against a one-off CPython float64 pipeline of the
same definition (about 40 minutes), relative 4e-15 on the mel, 1e-15 to 2e-15 on the sums of the later
stages (a few to some tens of ulps), tolerance 1e-9. The 1 s clip keeps its earlier checks, and
the single-chunk output is bit-identical to what the one-chunk code produced before this change (audio.bin
sum 4621466046952091410).

Gate: 46 checks (the 1 s clip: 16 encoder values, the first token and its logit, ids, text, the
vocabulary fixture, 5 refusals; the sentence: 16 encoder values, the first token's logit, ids, text) and 13
mutations (vocabulary 4, encoder 7: mel reflection, conv stride, feature order, attention scale, the
token count of the last chunk, the position restart, the zero padding; decoder 2). Two changes made the
gate faster, from about 40 minutes to 21 to 27 minutes (21 min 26 s and 26 min 50 s in two runs, the second one
with other work running on the machine): `ASR_GATE_JOBS` (default 4) runs the mutations in
parallel, each in its own state directory (a clone of the model costs no disk); and
`ASR_GATE_TIER=quick` runs only the 1 s clip, the vocabulary fixture and the refusals (26 checks, 4 min 42 s).

A mutation that did not change the behaviour was replaced: `ceil(L / 8)` written as `(L + 8) / 8` gives
the same 7 tokens for L = 50 (the test clip), so the mutation is `(L + 14) / 8`, which gives 8.
The earlier serial run printed "46 of 39" and exited 1: the 46 checks all passed and the expected total in
the script had not been updated (it is 46 for the full tier, 26 for the quick one).

Not claimed: HF's padding of the audio to 30 s (changes the maximum used by the clamp and the reflection
at the end) or the original model beyond these transcripts (mrope as plain RoPE; positions of audio
tokens unchecked); audio over 8 s (one attention window, 104 tokens; prompts over 64 tokens are refused);
the decoder with audio against CPython (about half an hour per prefill). The 33-token decoder logits
are a regression pin. The gate needs the 1.88 GB model and is not CI-runnable.
