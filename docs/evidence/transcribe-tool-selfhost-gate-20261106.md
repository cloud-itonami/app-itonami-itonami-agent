# `itonami-agent stt`: a WAV file in, the transcript out (JUN-941, 2026-11-06)

The native Qwen3-ASR path (JUN-928/935/940) is now one tool.

- `kotoba/itonami/wav_reader.kotoba` and `kotoba/agent_cli/wav_hex_cli.kotoba`: a native WAV reader. It walks
  the RIFF chunks in order (afconvert writes a 4,044-byte `FLLR` filler between `fmt ` and `data`; chunks are
  padded to an even length) and accepts PCM, mono, 16,000 Hz, 16-bit, 4,000 to 64,000 samples (0.25 to 4 s), at
  most 262,144 bytes (one FS2 E request). Everything else is refused with exit 2, nothing on stdout and a named
  reason on stderr. The hex of the two real files equals what CPython's `wave` module reads.
- `bin/build-transcribe-guests <amu-cli> <out-dir>`: builds the five guests (wav, audio encoder, decoder,
  vocabulary reader, calc) from this checkout with one source lock each, verifies their signatures and writes
  `guests.sha256`. Nothing else should be copied into the guest directory.
- `bin/itonami-transcribe <file.wav>`: chains the stages. Environment: `ITONAMI_STT_GUESTS` (the guest
  directory) and `ITONAMI_STT_MODEL` (a directory with `model.safetensors` and `vocab.json`); optional
  `ITONAMI_STT_MAX_NEW`, `ITONAMI_STT_KEEP`, `ITONAMI_STT_DEBUG`. stdout is the transcript, stderr names the
  language and the sizes. Exit 0, 2 for a refused input, 78 when unconfigured, 1 when a stage fails.
- `bin/itonami-agent stt <file.wav>`: the launcher route; it execs the driver.

Results on the two real WAV files (`test/native/wav/`, made with `say` and `afconvert`): `The quick brown fox
jumps over the lazy dog.` (39,946 samples, 33 audio tokens, 13 tokens decoded, 9.5 min) and `今日はとてもいい天気ですね。`
(38,367 samples, 31 audio tokens, 10 tokens decoded).

Gate: `bin/check-transcribe-tool <amu-cli> <out-dir> <model-dir>`, 26 checks: the guest build; the reader on the
two real files and on 15 refused files (12 broken in different ways, and three at the limits: 64,001 samples, 3,999
samples, and a data chunk one byte short), and on the accepted limits (64,000 and 4,000 samples, a data
chunk that fits exactly); the driver's refusals (no argument, an unset guest directory, an unset model, a missing
file, a stereo file, a file over 4 s); the launcher's refusals (unset environment 78, no argument 2); the tool on
both WAV files (stdout equals the spoken sentence, stderr names the language and the sizes); the launcher on the
English WAV. Ten mutations of the reader are caught (header offsets of the RIFF tag, the sample rate, the channel
count and the bit depth; the chunk padding; the sample bias and byte order; the 4 s and 0.25 s limits; the
truncation check). 26 min 19 s.

Mistakes made on the way, kept here:
- The first runs of the driver printed garbage (`language English<LM THE QUICK BROWN FOX ...`, repeated). The
  cause was a stale `qwen_calc_cli` copied into the guest directory by hand: it had no `greater`, and the
  driver treated the failed comparison (exit 2) as "not larger", so every token came from the first
  vocabulary slice. The driver now stops on any exit code other than 0 or 1 from `greater`, and the guests
  are built by `bin/build-transcribe-guests` instead of copied. The suspicion that the 249- or 250-frame
  length mattered was wrong (a 40,000-sample WAV gave the same garbage); it was settled by comparing the guest
  binaries' digests with the gate's.
- Three mutations were first missed because the test files did not reach the boundary they change (a limit
  moved by one sample, a check moved by one byte): files at 64,001, 3,999 samples and one byte short were added;
  one pattern also needed a `\*`.
- The expected count was written as 36, then 23 and 25 before 26 (counted from what the script runs).
- `model.safetensors` of the ASR and TTS models in `jun-877-models/` was found missing at 20:20 although
  nothing here deletes it (the driver only removes its own work directory). The cause was not found; disk
  free space had been 35 GB of 927 GB. The ASR file was restored as an APFS clone of a work copy whose sha256
  matched the manifest (79d6cbd4...). The TTS model was not touched and is still missing.

Not claimed: other formats, languages or speakers; audio over 4 s (an 8 s clip needs a larger argument
budget); streaming; the original model beyond the two transcripts; CI (the model is 1.88 GB). The gate takes
the Amu CLI pinned in `bin/build-transcribe-guests`. HF feature-extractor padding to 30 s and mrope on audio
positions remain open (JUN-935).
