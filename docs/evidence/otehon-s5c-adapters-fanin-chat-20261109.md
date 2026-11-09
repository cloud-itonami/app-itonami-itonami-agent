# Otehon S5c watchers, lane adapters, gateway fan-in, chat (JUN-1036) — 2026-11-09

Unit 2 (this doc): the per-lane request builders + send adapters
(`platform_adapters.cljk`), the completion fan-in
(`gateway.cljk` send-transport!/route-key/identities/admission/
format-coalesced/fan-in/enqueue/flush/cancel + hooks) and the inbound
chat platform (`chat_platform.cljk` normalize/guard/route/history/
dispatch/poll/serve/chunk/prepare/send-reply), ported to `.cljc`
behind the same observable API (same args/files/returns/exit codes).
Transports stay injected (`:fetch` stubs answer response maps);
promise fan-out, background timers and the live run-job loop stay
host-side. Unit 1 (process/quiet/watcher notify seam) is recorded in
the S5c unit-1 commit; this doc covers the unit-2 additions.

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-s5c/otehon
rm -rf test/scratch
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-09)

```
PASS  basilisp cases: {:cases 418, :fail [], :negative-controls 27, :pass 418, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 41/41 reported FAIL
PASS  bb cases: {:cases 418, :fail [], :negative-controls 27, :pass 418, :runner "babashka 1.12.218"}
PASS  bb must-fail: 41/41 reported FAIL
PASS  nbb cases: {:cases 418, :fail [], :negative-controls 27, :pass 418, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 41/41 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

418 cases (was 383): 10 adapters (11-lane build matrix incl. matrix
txn-id /TXN normalization, token-env/defaults, resolve incl. env-absent
and case-insensitive lanes, result-mapped sends incl. chat override,
feishu/dingtalk token exchange ok/fail, webhook refusals, matrix +
whatsapp sends) + 9 gateway (send-transport native/relay, route-key,
delivery/producer identities incl. invalid_params throw, admission
match/miss/no-authority, coalesced format incl. truncation + omit
line, fan-in deliver/dedupe-then-next/admitted-filtered/cancel) +
16 chat (5-platform normalize incl. no-turn/unknown-platform nils,
guard ok/duplicate/empty-identity/empty/oversized + 8000-char
boundary, explicit/delivery routing, full dispatch replied/unrouted/
dropped/turn-failed/executor-throw incl. history append + send log,
serve ticks/offset/error-backoff, chunking incl. newline preference,
prepare explicit/webhook/unknown, send-reply guards + chunked send,
token/backoff incl. missing, history roundtrip + limit default,
poll offset/error). must-fail 41/41 (was 35/35: unknown lane/step,
unknown fan-in verb, invalid producer identity, zero chunk limit,
unknown chat verb). Canon outputs byte-identical across runners.

## Compat notes (legacy vs otehon)

- `feishu-request` URL is `.../messages?receive_id_type=chat_id`
  (verbatim legacy; the first port draft wrote `receive_type` and a
  shared case pins the legacy form).
- `whatsapp-request` Content-Type is `application/json; charset=utf-8`
  (verbatim legacy; ditto).
- Error strings are verbatim legacy (`slack chat.postMessage
  failed: ...`, `feishu token exchange failed: ...`, `discord
  webhook serves chat ...; refusing reply to ...`, `invalid_params`,
  `chunk-text: limit must be a positive integer`, the three
  send-reply refusals).
- Token secrets come from the environment (never config);
  `for-lane` takes an injectable env (3-arity; 2-arity delegates to
  the live environment).
- `make-dispatcher` dispatches inline (no per-chat queue): the queue
  serializes concurrent turns host-side and has no single-message
  observable effect; all statuses, events, history rows and replies
  are identical.
- `serve!` is bounded (`:max-ticks`/`:stop?`, `:sleep-fn` records
  instead of sleeping) and answers `{:status :max-ticks/:stopped
  :ticks n}` instead of `{:stop :done}`; poll/ack/offset order
  (dispatch every update, persist the consumed offset only after
  every dispatch settles) is verbatim.
- Fan-in flushes run explicitly (`flush-batch!`, no background
  timers); the waiter a legacy promise settles is a result atom.
  Delivery-candidate order (dedupe-then-next), admitted filtering
  (settles true without delivery), coalesced text (redact before
  slicing, 800-char tail truncation, omit line) and the cancel
  (everything pending settles false) are verbatim.
- `producer-identity` renders whole doubles integral and encodes
  compact JSON, matching `JSON.stringify` byte-for-byte.
- `ops-chat` wipes its per-case scratch dir first (history jsonl and
  the serve offset would otherwise leak from a previous run),
  same as the drain rail's `wipe-dir!` precedent.
- `build` results carry live-shaped `Authorization: Bearer <token>`
  headers; observed command output masks the secret
  (`Bearer [REDACTED]`) while case comparison runs on the unredacted
  data and cross-runner canon stays byte-identical.
