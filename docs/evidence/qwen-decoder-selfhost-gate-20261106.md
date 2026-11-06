# The native Qwen3 decoder on the real Qwen3-ASR-0.6B weights (JUN-926, 2026-11-06)

`kotoba/itonami/qwen_decoder.kotoba` runs the language-model half of Qwen3-ASR: embedding lookup, 28
pre-RMSNorm layers (16 query heads and 8 key/value heads of 128, QK-norm, RoPE theta 1e6 in the
rotate-half layout, SiLU MLP of 3072, RMSNorm eps 1e-6), the final RMSNorm and the 151,936-row
`lm_head`. `config.json` says `tie_word_embeddings: true`; the file also carries `lm_head.weight`, which
the head uses. The model's mrope (sections 24/20/20, interleaved) is treated as plain RoPE, which is
the same function when all three position axes are equal (an assumption for audio positions, not
checked against the original code).

State is binary f64 files (8 bytes per value, little endian) in the granted directory: `/d/st/h.bin`
(the hidden states of the tokens being processed) and `/d/st/k<l>.bin`, `/d/st/v<l>.bin` (the key/value
caches of layer l). They are written with FS2 B/C requests through `:fs/app-data-bytes` (up to 262,144
bytes per request, the first piece creates the file) and read with E. The CLI
(`kotoba/agent_cli/qwen_decoder_cli.kotoba`) has three commands: `embed <model> <ids>`,
`layer <model> <layer> <pos0> <n>` (n tokens at positions pos0.., caches extended) and
`head <model> <start> <end>` (best row, its logit bits and the sum of the logits of lm_head rows
[start, end) for the last token). A layer reads each weight row once and applies it to all tokens
(`linear-multi`); because the runtime never reclaims memory and keeps about eight bytes for every byte
read, one layer is one process (about 290 MB, 1.3 s for 6 tokens) and the head runs in slices of up to
30,000 rows (0.6 s each). The 28 layers take about 28 s for a prompt.

Gate: `bin/check-qwen-decoder-selfhost <cli> <out-dir> <model.safetensors>`, 14 checks against one-off
CPython float64 runs of the same definition (struct/json weights, `math.exp/sin/cos`; 232-297 s each):
- six prompt tokens `<|im_start|>system\n<|im_end|>\n<|im_start|>`: best row 151645, its logit
  14.453661670645674 (native differs by 26 ulps), the sum of all 151,936 logits (relative 1e-13);
- "The capital of France is" (5 tokens): best row 12095 ` Paris`, logit 26.519131585045;
- one more token (` Paris`, id 12095) at position 5 through the key/value caches, compared with the
  CPython run over all six tokens: best row 13 `.`, logit 29.566585907056155;
- five refusals (layer 28, an id past the vocabulary, an empty slice, a missing model, an unknown
  command; each exits 2 with nothing on stdout).
The tolerance is 1e-9 relative. Twelve mutations (RoPE theta, the rotation sign, the GQA head mapping,
the score scale, the causal length, the Q-norm weights, the MLP gate, the RMSNorm eps, the attention
residual, the cache offset, the final norm, the position offset) are caught; a source changed after
locking is refused.

A sanity check that does not come from the same author as the reference: greedy generation from
"The capital of France is" with the key/value caches gave ` Paris`, `.`, ` The` (about 15 s per token),
and recomputing the prefix without a cache picked the same tokens. The first prompt's best token is
`<|im_end|>` rather than `user`: this is an ASR model and the prompt is an empty system message
followed by a bare `<|im_start|>`, which is not what it is trained to continue; the Python reference
and the native code agree on it.

Not covered: the audio tokens (the projector output is not yet injected at the `<|audio_pad|>`
positions), the tokenizer and the prompt template (token ids are given by hand), a generation loop
with a stop token, the `language ...<asr_text>` prefix, more than 64 tokens per call, a node, the agent
turn, the launcher. The gate needs the 1.88 GB model and is not CI-runnable.

Findings for the Amu seed gaps in JUN-876:
- a `:vector-i64` can be an argument and a result of an exported function (a module can share vector
  helpers); only `:vector-f64` cannot;
- `vector-alloc` plus `vector-assoc!` fills in place (100 allocations of 3584 values take 4 MB), while
  a `vector-f64` built by `conj` copies on every append;
- `E6015` also appears when the lock lists a module that nothing requires (here `gelu-erf`), not only for
  unbalanced parentheses;
- the FS2 write requests (`B`/`C` through `:fs/app-data-bytes`) work in `amu-command-fs35`.
