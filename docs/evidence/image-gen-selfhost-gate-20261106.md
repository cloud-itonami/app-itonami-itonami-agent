# Itonami image_generate native core gate — 2026-11-06 (JUN-863)

`kotoba/itonami/image_gen.kotoba` is the pure core of the `image_generate`
tool: the size policy (`WxH`, each side 64..2048, plain digits), the JSON
request body for an OpenAI-compatible `POST {base}/images/generations` (the
shape murakumo's ComfyUI gateway serves: `{prompt n size}` ->
`{data [{b64_json}]}`), and extraction of the first `b64_json` value from the
response. A prompt with a control byte other than `\n \r \t`, an empty prompt
or model, or an invalid size yields an empty body; a response that is not
canonical-length base64 (absent, null, escaped, empty) yields an empty string.

## Pinned inputs

- `kotoba/itonami/image_gen.kotoba`, SHA-256
  `c7c4a0353a5e507c317b229f8d031679a58d40dea8e9362d173dc427a0065525`.
- Amu candidate commit `d673111b66f43a8569e545c772a70e51737e1707`, CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.
- Host: macOS Apple Silicon.

## Reproduction

```sh
bin/check-image-gen-selfhost <amu-cli.macho> /Users/junkawasaki/github/workspaces/<agent>/<new-dir>
```

Measured 2026-11-06: direct, source-locked `run` and artifact `run` all
returned `7` (all three test entries true); a mutation that widens the size
ceiling to 4096 returned `6`; a source changed after locking was refused with
`EINPUT a locked project source` and left no artifact. The module lock lists
modules sorted by name (`itonami.image-gen` before `itonami.image-gen-gate`);
an unsorted lock is refused with `ELOCK`.

## Qualification limits

Pure string logic only. Nothing here calls a model or the murakumo gateway,
writes an image file, registers a tool in the native turn loop, or qualifies a
launcher route: those need the host effect ABI (AWAI-131) and remain open. The
Amu CLI is an unmerged candidate. No CLJK, EDN, JVM, Node, Python or model
oracle was used. Not yet measured: a real murakumo `/v1/images/generations`
response against `extract-b64` (the gateway source documents the shape; no live
call was made).
