# Otehon S5d unit 1: peer messaging plane + send command (JUN-1036) — 2026-11-09

Unit 1 (this doc): the peer messaging plane in `peer.cljc` — did:key
parse/valid/pub->did, signed envelopes (sign/open) + the
X-Itonami-Envelope auth header, `peers.edn` read/write/add, rail
ensure, path rebase, bundle materialize/fetch, kekkai service-record
projection/overlay, the fetch response mapping — plus the `send`
command (`run-send-step`/`ops-send`) in `cli.cljc`, ported to `.cljc`
behind the same observable API (same args/files/returns/exit codes).
Crypto (sign/verify), time, nonces and fetch stay injected seams
(`:sign` `:verify` `:now-ms` `:random-hex` `:nonces` `:fetch`); the
live `send`/`gateway-serve`/`peer` socket commands stay host-side
(S6). Unit 2 (discover/remote-run/serve routes) follows separately.

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-s5d/otehon
rm -rf test/scratch
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-09)

Pre-rebase (unit-1 branch on f5646bd): 433 cases / 48 must-fail,
all green on all three runners, canon byte-identical.

Post-rebase union with main f544ca0 (S2c confine + quiet, +35 cases
+2 must-fail), re-run from a clean `test/scratch`:

```
PASS  basilisp cases: {:cases 468, :fail [], :negative-controls 27, :pass 468, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 50/50 reported FAIL
PASS  bb cases: {:cases 468, :fail [], :negative-controls 27, :pass 468, :runner "babashka 1.12.218"}
PASS  bb must-fail: 50/50 reported FAIL
PASS  nbb cases: {:cases 468, :fail [], :negative-controls 27, :pass 468, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 50/50 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

Unit-1 contribution: 15 cases + 7 must-fail (was 418 / 41): +2 send (ok-and-errors incl. slack-adapter /
--chat-id / empty-text / HTTP-500 paths, platform case-fold + thread
routing) +13 peer (did roundtrip incl. bad-alphabet/short rejects,
peers.edn read/add/write/read, envelope sign/open roundtrip incl.
replayed-nonce, bad-signature / bad-did / verify-throw / untrusted /
stale verdicts, auth-header percent-encoding, bundle materialize+read
incl. missing, bundle fetch ok incl. CID verification, ensure-rail
incl. already-present + nil rail, path rebase incl. nil text,
service-records projection incl. invalid-record drops, service-record
overlay, http mapping ok/500). must-fail 48/48 (was 41/41: bad-char
parse, unsafe bundle path, fetch-500, bad-CID fetch, fetch throw,
unknown verb, garbage-payload open). Canon outputs byte-identical
across runners.

## Verified divergences / fixes during unit 1

- `run-send-step` takes the full step vector and strips the verb
  (`[_ platform chat-id thread-id text]`), like the `:peer` / `:chat` /
  `:gateway` step fns; the first draft destructured without the verb.
- `service-records->peer-infos` / `overlay-service-records` answer
  keyword `:models` / `:load` (not string keys), so overlaying records
  onto discovered infos overwrites instead of duplicating the key.
- The `ensure` step passes a nil rail through as nil (no `(str nil)`
  coercion), so `ensure-rail` leaves the config unchanged.
- Envelope maps keep string keys end to end (`sign-envelope` builds
  them, `open-envelope` destructures `{:strs ...}`); peer-info and
  trust maps keep keyword keys. Case expectations follow the same
  split.
- Case DIDs are real did:key ids: `valid-did?` rejects short/fake
  key text, so envelope cases use a 32-byte key id throughout.
