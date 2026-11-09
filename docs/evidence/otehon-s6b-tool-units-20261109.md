# Verification record — S6b tool units wired — 2026-11-09

JUN-1036 S6b (`clarify` / `collect` / `tts-shape` / `webguard-pure`):
the four pure tool cores restored on branch
`jun784/jun-1036-otehon-s6b` (files only, no CLI wiring, no shared
cases) are wired here with `:tts` / `:collect` / `:clarify` /
`:webguard` case ops, 64 shared cases and 8 must-fail controls.

## Files (all new or added here, base 21e52b6)

- `src/itonami/otehon/tts.cljc` (new, byte-identical from the s6b
  branch): `configured` / `speech-request` / `safe-format` /
  `clamp-timeout-s`. 15 cases.
- `src/itonami/otehon/collect.cljc` (new, byte-identical): 2-layer
  shadow-write outbox core, `certify-row` / `certify` / `reconcile`
  with injected `sign-fn` / `append-fn`; fail-closed refusals
  (`:not-pending` / `:bad-id` / `:bad-canonical` / `:no-signature` /
  `:no-receipt` / `:append-failed`). 11 cases.
- `src/itonami/otehon/clarify.cljc` (new, byte-identical): Hermes
  clarify_tool semantics, `strip-recommended` / `mark-recommended` /
  `clean-answer` / `normalize-questions` / `response-status` /
  `result-json`. 15 cases.
- `src/itonami/otehon/webguard.cljc` (new, repaired): 2-char restore
  repair (query-rules vector close) plus a portability rewrite, see
  below. 26 cases.
- `src/itonami/otehon/cli.cljc`: require + declare + dispatch +
  `ops-tts` / `collect-stub`+`ops-collect` / `ops-clarify` /
  `ops-webguard` (same verb-dispatch idiom as the `:sse` op).
- `test/cases.edn` +64, `test/must-fail.edn` +8.

## Portability notes

- Word-boundary `\b` never matches on the bb runner (empirically:
  `\d`/`\s`/`^`/`$`/lookahead/`\B` all work on bb; `\b` does not, via
  both regex literals and programmatic `re-pattern`). The cause is
  likely SCI string-unescape handling (`\b` arriving as backspace).
  `webguard` token edges were rewritten to explicit
  `(?:^|[^A-Za-z0-9_])` / `(?:[^A-Za-z0-9_]|$)` guards with identical
  refusal behavior on all runners; the constraint is recorded in the
  namespace docstring.
- String-literal `\b` (json-escape in `bundle`/`io`) and char
  literals (`ed25519` hex digits) are unaffected — only regex `\b`.
- Open (not touched, needs owner decision): `process.cljc`
  `redact-command` uses `(?i)\bBearer\s+...` with no shared-case
  coverage, so the suite is green while the bb path may skip Bearer
  masking. Proposed as its own slice (op + pinning cases).

## Runs

`./bin/run-shared-cases basilisp bb nbb` after the final edit:
621/621 pass, 28 negative controls, must-fail 72/72,
`bb`/`nbb` output identical to basilisp. Runners:
basilisp 0.5.1 / bb 1.12.218 / nbb on node v26.3.0.

## Addendum — redact-command slice (same day, done)

`process/redact-command` made public with a `:process` op
(`redact`, 8 cases + 1 must-fail): the secret-phone case caught a real
bb/SCI divergence (`\b` never matches), fixed with explicit
boundaries; the fix then caught a second one (replacement-string `$1`
unexpanded on bb, and the bb replacement fn receives the bare match
string instead of a vector), fixed with dual-shape whole-match fns
plus hand-parsed boundaries (char sets only).
629/629 x3, must-fail 73/73, byte-identical.

## Not done

No commit/push made. s2e (`jun784/jun-1036-s2e-basilisp`, SSE unit,
verified 558/558 x3) stays a separate branch for the owner PR; the
only foreseen merge contact with these slices is the shared `declare`
line in `cli.cljc` (one-line union per added op).
