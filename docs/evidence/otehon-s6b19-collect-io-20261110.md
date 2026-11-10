# Verification record — S6b-19 collect-io receipt — 2026-11-10

JUN-1036 S6b-19: the pure receipt mapping of the outbox collector.
`collect-io.cljc` ports `itonami.agent.collect-io/receipt`: an SDK
append result becomes collect.cljc's receipt contract (transfer id,
signer, stamp) or nil on any doubt — empty id, missing id, error
maps and blank signers all fail closed, and unknown extra fields
are dropped. `:collect-io` verb wired through `ops-collect-io` in
`cli.cljc`. 5 shared cases + 2 must-fail controls; 933 cases + 110
must-fail green on basilisp 0.5.1 / bb 1.12.218 / nbb, canon
byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/collect_io.cljc`: new, `receipt`.
- `otehon/src/itonami/otehon/cli.cljc`: require, declare,
  `:collect-io` dispatch, `ops-collect-io`.
- `otehon/COMPAT.md`: S6b-19 entry + collect table row updated.
- `test/cases.edn` +5, `test/must-fail.edn` +2.

## Portability notes

- The legacy keyword read (`:transfer-id`) on a JSON-shaped
  string-keyed result returns nil — identically on all three
  runners. The first draft cases used string keys and the gate
  rejected both positive cases on every runner; the port keeps
  the accessor verbatim (code first) and the cases use keyword
  keys, which is also the collect.cljc data convention. Which key
  convention real SDK results carry gets pinned when the SDK
  wiring lands (S6) — the docstring records this.
- No other runner divergence: plain map predicates only.

## Runs

- bb 1.12.218: 933 cases, 0 fail; must-fail 110/110 FAIL (as designed).
- nbb (node v26.3.0): 933 cases, 0 fail; must-fail 110/110 FAIL.
- basilisp 0.5.1: 933 cases, 0 fail; must-fail 110/110 FAIL.
- Canon byte-identical across the three runners.
