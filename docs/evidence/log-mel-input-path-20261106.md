# Itonami log-mel frame input path — 2026-11-06 (JUN-866)

Follow-up to `log-mel-selfhost-gate-20261106.md`. The seed subset has no
vectors or byte reads (measured: `vector-f64-conj`, `vector-i64-at`,
`bytes-*` and `string-byte-at` all give `E2101`/`E2104`), while a native
guest receives data as strings: argv on `:cli/args` (`kotoba/agent_cli/*`)
and lines on `:io/read` (`inference/verify/native/gpu/gen_decode_tokens_guest.cljk`
serve loop). So a frame is one string.

## Decision

One frame = exactly 1600 hex characters: 400 samples x 4 digits, offset-binary
int16 (hex of sample + 32768, so `8000` is silence), sample n at characters
4n..4n+3, scaled by 1/32768 (upper and lower case hex accepted). The host
drives the 3000-frame window one such line per frame. `frame-valid?` refuses
any other length or a non-hex character; `power-bin` then answers -1.0 and
`log-mel-bin` 0.0, so an invalid frame cannot be mistaken for the 1e-10
floor (-10.0). The earlier built-in synthetic generator is removed.

## Pinned inputs

- `kotoba/itonami/log_mel.kotoba`, SHA-256
  `19f892cd437a994661354c3ec90963c3b5ef84ee4885cc157e3a96595049fcd4`.
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.

## Result (2026-11-06, `bin/check-log-mel-selfhost`)

Direct, source-locked and artifact runs all returned `89`: 80 log-mel values
of the quantised test frame within 1e-6 of the reference, 3 normalised values,
2 silence-frame floors (-10.0 at mel 0 and 79, which also exercises the 1e-10
floor the earlier unit never reached), and 4 frame checks (valid accepted;
short, non-hex and over-long refused). Eight mutations each stopped the gate
returning 89: Hann coefficient (7), quadrant sign (6), ln series length (20),
filter norm (6), sample offset (85), hex digit weight (6), length check
`=` -> `>=` (88), hex letter range (5). A source changed after locking was
refused. Runtime under one second.

## Reference

The reference is the earlier stdlib-only script plus int16 quantisation, so it
runs on exactly the values the frame string carries (Python 3.15.0,
`python3 -I oracle2.py kotoba` for the values, `... hex` for the frame). Diff
against the earlier script:

```diff
    26c26,29
    < x = frame()
    ---
    > x0 = frame()
    > Q = [max(-32768, min(32767, int(round(v * 32768.0)))) for v in x0]
    > x = [q / 32768.0 for q in Q]
    > HEX = "".join(format(q + 32768, "04x") for q in Q)
    37c40,42
    < if len(sys.argv) > 1 and sys.argv[1] == "kotoba":
    ---
    > if len(sys.argv) > 1 and sys.argv[1] == "hex":
    >     print(HEX)
    > elif len(sys.argv) > 1 and sys.argv[1] == "kotoba":
```

## Qualification limits

Not covered: the real wiring (a guest that reads frames from `:io/read` or
argv under a capability policy), the 30-second window and reflect padding,
FFT speed, real audio, any model. A frame decoded from a real WAV (little-endian
int16 to this hex form) is host work, not done here.
