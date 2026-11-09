# Otehon S5d unit 2: discover / remote-run / serve routes (JUN-1036) — 2026-11-09

Unit 2 (this doc): the peer client + serve routes in `peer.cljc` —
`peer-info`, `discover` (one gossip hop), `pick-peer`, `infer-peers`,
`infer-via-peers`, `remote-run` (submit + poll loop + result mapping +
workspace-write application), the seven serve routes
(`peer-route`, `models-route`, `chat-route`, `infer-route`,
`bundle-route`, `run-accept-route`, `run-poll-route`) behind the
`serve-route` dispatch, plus `local-gateway-complete` and the
`placement` order loop — ported to `.cljc` behind the same observable
API (same args/files/returns/exit codes). The socket, body reading,
JSON printing, child processes, the agent loop, live rails, syscalls
and the live `peer serve` / `gateway serve` commands stay host-side.
Unit 1 (messaging plane + send) is recorded in
`otehon-s5d-peer-send-20261109.md`.

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-s5d2/otehon
rm -rf test/scratch
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-09)

```
PASS  basilisp cases: {:cases 487, :fail [], :negative-controls 27, :pass 487, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 57/57 reported FAIL
PASS  bb cases: {:cases 487, :fail [], :negative-controls 27, :pass 487, :runner "babashka 1.12.218"}
PASS  bb must-fail: 57/57 reported FAIL
PASS  nbb cases: {:cases 487, :fail [], :negative-controls 27, :pass 487, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 57/57 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

Unit-2 contribution: 19 cases + 7 must-fail (was 468 / 50):
peer-key/header/trust, peer-info ok, discover + gossip hop,
pick (trusted/reachable/not-full/not-me, cid-holding first, then
load), infer-peers (model + auto), infer-via ok + 500-fallback +
no-lending-peer, remote-run ok (submit 202 + poll 200 + receipt +
writes incl. unsafe-path skips) + submit-refused + poll deadline,
serve peer/models routes + loopback 403, chat/infer routes +
own-rails-failed 502, bundle/run routes (cid match, unknown cid,
missing header, accept 202 + running poll + not-your-run +
names-another-run + no-such-run) + busy 429 + not-found, placement
peer-then-local fallback + no-peer fallback + local-first, list
lines (trusted/untrusted/unreachable/self-skipped). must-fail 7:
peer-info 500, pick wrong peer, infer-route bad did, remote-run
bundle mismatch, run-busy-as-202, bundle wrong-cid as 200,
chat-remote as 200. Canon outputs byte-identical across runners.

## Verified divergences / fixes during unit 2

- Discovery infos are keyword-normalized (`:load` `:max-runs`
  `:models` `:agent-models` `:profiles` `:peers`; the legacy reads
  mixed string keys here). The wire JSON is untouched.
- Verified `/peer` payload signing fields (`ts` `nonce` `protocol`)
  are kept in the normalized info, verbatim from the legacy.
- `remote-run` `:writes` are sorted: parsed-JSON key order differs
  per runner (observed nondeterministic across runs), so the legacy
  document order cannot be reproduced portably.
- `remote-run` creates the run `workspace/` dir before applying
  writes (the harness has no pre-existing profile home; on existing
  homes this is a no-op).
- Crypto/time/sleep/rails/local-run/fetch ride injected seams
  (`:sign` `:verify` `:now-ms` `:random-hex` `:nonces` `:fetch`
  `:sleep` `:complete` `:run-local` `:bundles`); the scripted clock
  pops once per read. The socket, child processes, agent loop, live
  rails, node health syscalls and the live serve commands stay
  host-side (S6).
- `did->public-key` is not ported: its only legacy role is building
  the host verify key, and verification is an injected seam.
- The run-poll done/failed dissoc branches have no case: no
  harness step can complete a run in the shared `:runs` atom
  (execution stays host-side). The 202 running read, the 404, the
  requester check and the poll-name check are covered.
