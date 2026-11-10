# Verification record — S6b-16 browser policy units — 2026-11-10

JUN-1036 S6b-16: the pure policy core of the itonami browser proxy.
`browser.cljc` ports the testable heads of `itonami.agent.browser`:
`secret-url?` (credential shapes raw or percent-encoded; malformed
encoding fails closed, exactly like the legacy catch), `allowed-host?`
(bracket-strip + lowercase + allowlist + metadata denylist),
`parse-connect-target` (`[v6]:port` and `host:port` with
`parseInt`-prefix port semantics), `scrub-output` (the four proxy
redactions), `redact-cdp-output` (recursive, `[redacted]` lowercase),
`clamp-timeout` (1..1800s, 300 default) and `screenshot-path` (last
regex hit that exists, `file?` injected like the outbox stubs).
`:browser` verbs (`secret` / `allowed` / `connect` / `scrub` /
`redact` / `clamp` / `shot`) wired through `ops-browser` in
`cli.cljc`. 19 shared cases + 2 must-fail controls; 894 cases + 102
must-fail green on basilisp 0.5.1 / bb 1.12.218 / nbb, canon
byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/browser.cljc`: new, 7 public fns
  (+ private `hexval` / `percent-decode` / `parse-digits` /
  `sub-match`).
- `otehon/src/itonami/otehon/cli.cljc`: browser require, declare,
  `:browser` dispatch, `ops-browser` (+ `browser-file-stub`
  with `all` / `none` / `png-only` witnesses).
- `otehon/COMPAT.md`: S6b-16 entry + browser table row updated.
- `test/cases.edn` +19, `test/must-fail.edn` +2.

## Portability notes

- `(int \3)` is the codepoint (51) on bb/basilisp but the digit
  value (3) on nbb/SCI — found by the gate (`connect-*` nil,
  `clamp-str` 1 on nbb only). Digits now convert via alphabet
  index, and every pattern uses `[0-9]`, never `\d` (which is
  Unicode-aware on basilisp).
- `str/replace` with a function gets `[full group]` on bb/nbb but
  the bare match string on basilisp — found by the gate
  (`secret-encoded` nil on basilisp only). The fn normalizes
  before slicing.
- Host `$1` replacement syntax is left literal by basilisp, and
  bb/SCI string-match replace takes no function at all — so the
  group splice is explicit (`sub-match`) with a plain-string
  replacement. Safe because group 1 only ever holds fixed key
  words and whitespace.
- `secret-url?` keeps the legacy `or` shape verbatim: match string,
  nil when clean, false when malformed (each pinned by a case).
- `redact-cdp-output` recurses over string/map/vector only; lists
  pass through, as in the legacy (no list case pinned).
- DNS resolution and pinning, sockets, the proxy and CDP servers,
  process spawn, the proxy config-file write, clock reads, the
  file-existence check and the `browser` wiring stay S6.

## Runs

- bb 1.12.218: 894 cases, 0 fail; must-fail 102/102 FAIL (as designed).
- nbb (node v26.3.0): 894 cases, 0 fail; must-fail 102/102 FAIL.
- basilisp 0.5.1: 894 cases, 0 fail; must-fail 102/102 FAIL.
- Canon byte-identical across the three runners.
