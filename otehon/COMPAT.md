# Otehon API/command compatibility (JUN-1036)

Owner instruction 2026-11-08: reach implementation compatibility 100% in
`.cljc` first, as Basilisp. Compatibility is at the **API and command**
level — arguments, files read and written, return shapes, exit codes.
Internals may differ freely from the legacy `.cljk`.

Tracking: [JUN-1036](https://linear.app/kotobalabs/issue/JUN-1036),
child of JUN-1008. Runners: Basilisp 0.5.1 (baseline), Babashka 1.12.218,
nbb 1.4.208. Constraints: [PORTABILITY.md](./PORTABILITY.md).

## CLI routes

Legacy reference: `src/itonami/agent/main.cljk` (`-main` dispatch and the
`*-cmd` handlers). `done` = ported, same args/files/outputs/exit codes,
covered by shared cases on all three runners.

| legacy route | legacy ref | otehon command | status |
|---|---|---|---|
| oneshot `-p/-z` (+`-m/--provider/-t`) | `oneshot`, `loop.cljk` toolset rule | `turn-gate` (gate only, no turn is run) | S2 (execute the gated turn) |
| `bridge` | `bridge` | — | S6 |
| `chat` | `chat` | — | S6 |
| `vault` | `vault-cmd` | — | S6 |
| `mcp` | `mcp-cmd` | — | S6 |
| `dapps` | `dapps-cmd` | `dapps/status` surface (resolve + rows, shared `:dapps` cases) | partial (JUN-1036 S6b-2; the product `dapps` command wiring stays S6) |
| `cron list/create/run/tick/runs/incidents/notepad/status` | `cron-cmd` | `tick` (due/next-fire plan), `run-exec` (host side + finished-turn apply; stops at `:planned` with no executor), `notepad` (KV verbatim), `runs`/`incidents` (ledger reads: rows list, ack) | done run/tick-exec + notepad + ledger reads (JUN-1036 S2a/S2b/S4a/S4b; ledger/queue writes S5) |
| `profile list/show/check/refresh/itonamify/adopt/release/soul/did/organize/readiness/import/export/hy-*` | `profile-cmd` | `check` (job-level readiness: the legacy command reads active jobs only; model/state.db/pause findings stay doctor-only), `store-plan` (import/export copy plan), `profile list/show/check/readiness/refresh/itonamify/rail/bundle/refresh-bundle/release/import/export/adopt/soul/organize/hy-stage/hy-migrate/did` (import/export executes the plan: definition byte copy + Hermes-side park on export; adopt flips scheduler + parks + refreshes CID; soul renders registry/worlds blocks with backup+receipt; organize projects the registry entry; hy-stage checks and stages the Hy payload; hy-migrate rewrites `.py` refs under the tick lock; did derives the fleet did:key; bundle prints CID + manifest) | S4 verbs complete (S6-owned check/readiness finding kinds deferred, recorded below) |
| `doctor` | `doctor-cmd` | `doctor` (store verbs; S6-owned finding kinds deferred, recorded below) | done (JUN-1036 S3b) |
| `sessions [list]/show/resume` | `sessions-cmd` | `sessions` (reads state.db read-only; resume refuses verbatim) | done (JUN-1036 S3b) |
| `model [NAME] [--provider]` | `model-cmd` | `model` (positional dir, otehon convention) | done (JUN-1036 S3b) |
| `fallback` | `fallback-cmd` | `fallback` (positional dir, otehon convention) | done (JUN-1036 S3b) |
| `status` | `status-cmd` | `status` | done (JUN-1036 S3a) |
| `pause [reason]` | `pause-cmd` | `pause` | done (JUN-1036 S3a) |
| `resume` | `resume-cmd` | `resume` | done (JUN-1036 S3a) |
| `send` | `send-cmd` | `cli.cljc` run-send-step (platform/chat-id/thread-id/text validation, adapter send via chat/send-reply!, injected `:fetch`) | done (JUN-1036 S5d unit 1; live command wiring stays host-side) |
| `gateway run/serve` | `gateway-run/serve` | — | S5 |
| `peer serve/run/discover/rails/exec/authorities` | `peer-cmd` | `peer-push/pull` (sync plans) + `peer.cljc` messaging plane + client/routes (S5d units 1+2) | partial (S5d done: trust/envelope/bundle + discover/pick/infer/remote-run/routes/placement; socket/child-process/agent-loop/rails reads stay host-side, S6) |
| `help` | usage | usage | done |

Deliberate divergences (documented, not gaps): profile order is sorted
(the legacy order is filesystem order, which differs per host readdir —
byte-identity across runners wins); `status` takes the store from
`ITONAMI_DIR` exactly like the legacy root; the pause lift hint keeps the
`itonami-agent resume` text verbatim so the future rename is a no-op.
S4b divergences: written `itonami.edn` bytes use explicit key order
(sorted at every level — parsed-map iteration order differs per runner,
so the legacy single-`pr-str` bytes cannot be reproduced portably;
content is identical and reads back the same); `profile show` renders
its manifest line with fixed key order for the same reason; bundle job
entries render keys sorted (the legacy file order is unobservable after
a parse); `default` lives under `profiles/` like every otehon profile
(the legacy default is the store root); reads never create state (the
legacy ledger `open-db` creates `cron/executions.db` on read; otehon
answers from a missing ledger without writing); load-failure texts for
missing homes/malformed jobs differ in wording (same shape, same codes);
a `runs --limit` with no leading digits prints no rows; store-case
directories start empty per case (a previous runner's created files
never leak into the next runner's case).
S4c divergences: transfer homes put `default` under `profiles/` like
every otehon profile (the legacy `home-in` default is the store root);
scratch import/export has no host-root defaults (missing `--from` /
`--to` refuses with the usage text); release with no name refuses
(the legacy blank-name keep line is an accident of `load-profile(nil)`
reading the store root); the parked stamp text is never asserted;
import/export dry-run reports 0 copied, verbatim (the count lives in
the real-copy branch only — itonamify/refresh/rail/release count
their would-lines instead).
S4d divergences: `cron/jobs.json` and run/audit log bytes are
port-canonical (compact, keys sorted at every level — Basilisp's
`py->lisp` build does not preserve JSON document order, so the
legacy file order cannot be reproduced portably; parsed content is
identical, and the legacy indent-2 layout is not reproduced
either); the soul backup path in messages keeps the scratch root
(absolute, runner-independent — only the `state/soul-blocks/` tail
is asserted); the soul receipt and the parked stamp text are
host-clocked and never asserted, only presence.

## Module checklist

Every `src/itonami/agent/*.cljk` module and its `.cljc` twin status.
`planned-S#` names the slice that ports it; roles marked `*` are verified
from source, the rest follow the module name until their slice lands.

| module | twin | status |
|---|---|---|
| `browser.cljk` *browser tools* | — | planned S6 |
| `browser_cloud.cljk` | — | planned S6 |
| `browser_provider.cljk` | — | planned S6 |
| `chat_platform.cljk` | `chat.cljc` (normalize/guard/route/history/dispatch/poll/serve/chunk/prepare/send-reply, sync) | done (JUN-1036 S5c; dispatch inline, serve bounded, fan-in hooks ride the gateway fan-in) |
| `clarify.cljk` | — | planned S6 |
| `code_rpc.cljk` | `code_rpc.cljc` (tool-error, result-error, tool-errors-since, sandbox-tools-for, terminal-blocked-params, missing-import-hint, failure-hint-rules, failure-hint, hint-on-failure) | partial (shaping + hints done JUN-1036 S6b-8, shared cases; shim-header/tool-stubs/generate-hermes-tools, token check, dispatch, budget, log, serve loop stay S6) |
| `collect.cljk`, `collect_io.cljk` | — | planned S6 |
| `computer_use.cljk` | `computer_use.cljc` (canon-keys, blocked-input?, select-window, build-request, content-text, image-parts, result-error, structured, error/success-result, action-request) | partial (arg shapes done JUN-1036 S6b-7, shared cases; driver spawn, capture/list flows, approval, call dispatch stay S6) |
| `confine.cljk` *OS sandbox* | `confine.cljc` (policy, secret-paths, read-denial, confine-env, seatbelt-profile, bwrap-args, command) | partial (pure policy core done JUN-1036 S2c; `backend` probe and `audit*!` writers stay host-side) |
| `connections.cljk` | `connections.cljc` (connection-status + auth-status, read-only) | done (JUN-1036 S6b-2, shared cases; install/authorize is slice 2, hosted answers the named error) |
| `cron.cljk` *jobs/schedules* | `cron.cljc`, `tick.cljc`, `run.cljc`, `exec.cljc` | partial (plans + `run-job` host side and finished-turn apply done, JUN-1036 S2a/S2b; ledger/queue rows S5a; drain rail — deliver/drain/settle/abandoned/finish-run — S5b; the live run-job loop + watcher fan-in stay open) |
| `dapps.cljk` | `dapps.cljc` (registry pin + server table + resolve-checkout + status) | partial (status surface done JUN-1036 S6b-2, shared cases; `with-servers` / `select-servers` need the MCP allowlist, stay S6) |
| `delivery.cljk` | `delivery.cljc` (normalize/lanes/bot-chat token/adapter registry/classify-outcome, pure) | done (JUN-1036 S5a; shared cases assert the vocabulary incl. the outcome table) |
| `delivery_queue.cljk` | `queue.cljc` (`cron/deliveries.db`, `deliveries` + bounded `delivery_tombstones`, enqueue/get-status/claim-row/claim-next/finish/recover) | done (JUN-1036 S5a; fencing answers verified by follow-up SELECT — same outcome single-writer; `job_json` keeps the legacy indent-2 layout, key order is the map's iteration order) |
| `execution_ledger.cljk` *reads* | `cli.cljc` (runs-for, incidents-for: rows/incidents/ack over `cron/executions.db`) | done reads (JUN-1036 S4b; claim/finish/upsert/resolve writes stay S5) |
| `execution_ledger.cljk` *writes* | `ledger.cljc` (`cron/executions.db`, executions + incidents DDL verbatim; claim (throws on active)/mark-handoff-pending/adopt-claimed/mark-running/finish (completed\|failed + 1000-row overflow drop)/set-delivery-outcome/incident upsert/get/ack/alerted/list/resolve/withheld gate) | done (JUN-1036 S5a; fencing verified by follow-up SELECT; no BEGIN IMMEDIATE — single-writer oracle, see notes) |
| `gateway.cljk` | `gateway.cljc` (platform values, delivery transport, adapter/injection resolution, watcher send seam, send-transport!/route-key/identities/admission/coalesced/fan-in — pure core) | done (JUN-1036 S5b transport core + S5c fan-in with explicit flushes; background timers stay host-side) |
| `homeassistant.cljk` | `homeassistant.cljc` (entity/service validators, blocked-domain gate, data coercion, payload builder, state/service/affected projections) | partial (shapes done JUN-1036 S6b-6, shared cases; fetch, JSON envelopes, tool dispatch stay S6) |
| `host.cljk` *node/file/env/process* | `io.cljc` | partial (files/env/clock/listing/pids/spawn done JUN-1036 S2a; + S5a: `now-micros-iso` (host-zone 6-digit fraction, the `iso-local` micros shape), `json-str-2` (indent-2, iteration order), `parse-iso-ms`, `kill-probe-alive?` (legacy kill(0) probe semantics), `sqlite-literal` numbers inline bare so REAL columns read back numeric; bb sqlite3 goes over stdin (`spawn-sync!` `:input`) since Linux caps one argv at 128 KiB) |
| `identity.cljk` ( FleetSeed/KDF/derive-key/did-of/parse-seed/key-id ) | `ed25519.cljc` (KDF + RFC 8032 expansion + clamped base-point multiply + did:key:z; host SHA-512/UTF-8 in `io.cljc` ) | done (JUN-1036 S4e; node `createPrivateKey` expansion reproduced exactly, oracle-checked) |
| `loop.cljk` *agent turn loop* | `turn.cljc` (gate), `assembly.cljc` (history, wrap-up, turn limits) | partial (S2d assembly done; turn execution S2) |
| `main.cljk` *CLI dispatch* | `cli.cljc` | partial (this table) |
| `manifest.cljk` | `manifest.cljc` | done |
| `mcp.cljk` | `mcp.cljc` (protocol-version, enabled?, names-for, command-for, server-env, tool-name, tool-allowed?, handshake-lines, call-line, collect-call-results, collect-exchange-result, render-result + private rpc/expand/object-schema/glob) | partial (config selection + naming + allowlist + message shapes done JUN-1036 S6b-4/S6b-5, shared cases; stdio/HTTP exchange spawn, oauth-cache-ready, discover projection/call dispatch stay S6) |
| `notepad.cljk` | `notepad.cljc` | done (JUN-1036 S4a; clear is ported, unwired — no CLI surface) |
| `oauth.cljk` | `oauth.cljc` (store-name only) | partial (JUN-1036 S6b-2; discovery/registration/token flows stay S6) |
| `p2p.cljk` *node server/trust* | `peer.cljc` | partial (sync plans done; + S5d unit 1: did:key parse/valid/pub->did, signed envelopes + auth header, peers.edn read/write/add, ensure-rail, path rebase, bundle materialize/fetch, service-record projection/overlay, fetch mapping; + S5d unit 2: peer-info/discover/pick-peer/infer-peers/infer-via-peers/remote-run, seven serve routes + dispatch, local-gateway-complete, placement; socket/child-process/agent-loop/rails reads/health syscalls stay host-side) |
| `pause.cljk` * | `pause.cljc` | done (JUN-1036 S3a) |
| `platform_adapters.cljk` *lane adapters* | `adapters.cljc` (per-lane request builders + token-env/for-lane resolution + sync sends, error strings verbatim) | done (JUN-1036 S5c; env-only secrets, injected `:fetch`) |
| `process.cljk` | `process.cljc` (linger budget, pending-notification text, background mode) | partial (S5c; supervision runtime: watchers/heartbeats/checkpoints — host-side, not shared-case portable) |
| `profile.cljk` *store layout* | `profile.cljc`, `status.cljc` (names), `store.cljc` | partial (verbs S4) |
| `profile.cljk` *bundle/manifest lifecycle* | `bundle.cljc` (bundle-files/manifest/cid, default-manifest, with-inference-rail, write-manifest!, with-bundle, refresh-bundle-cid, copy-profile!) + `manifest.cljc` (with-org) + `cli.cljc` (set-parked!, release/transfer/adopt/organize/hy lines) | done itonamify/refresh/rail/bundle/refresh-bundle/release/import/export/adopt/organize/hy-stage/hy-migrate/did (JUN-1036 S4b/S4c/S4d/S4e) |
| `provider.cljk` *chain/resolution* | `rail.cljc` (job target), `model.cljc` (display chain) | partial (live calls S2) |
| `profile.cljk` *set-model-text* | `model.cljc` (verbatim port, throws as ex-info) | done for model/provider keys (JUN-1036 S3b) |
| `quiet.cljk` | `quiet.cljc` (warn/diag/wake predicates, linger budget, turn-failed?, sync linger loop) | partial (predicates + 35 S2c cases JUN-1036 S2c; sync linger loop with injected fns JUN-1036 S5c; live registry waits stay host-side) |
| `rails.cljk` | `rails.cljc` (small-model? / normalize-model-ids / detect-entries / all-models / agent-models / select-candidates) | partial (selection core done JUN-1036 S6b-3, shared cases; rails.edn read/write, the loopback probe, detect fan-out and complete's fetch loop stay S6) |
| `soul.cljk` | `soul.cljc` (world/unit/role render + splice, limit, body guard) | done (JUN-1036 S4d) |
| `tools.cljk` *tool registry* | `tools.cljc` (schemas table + toolsets + name/unsupported/approval/enabled/execution-disabled/runtime predicates) | partial (registry core done S6a; tool implementations + servers/processes + CLIs stay S6c/d) |
| `tts.cljk` | — | planned S6 |
| `vault.cljk` | — | planned S6 |
| `watcher.cljk` | — | planned S5 |
| `webguard.cljk` | — | planned S6 |
| `yaml.cljk` | `yaml.cljc` | done |

## Slices

- S1 profile ops — done (JUN-1008, JUN-1020/1021).
- S2a host-side run — done (JUN-1036: `run-exec` script spawn, monitor
  sources, output-doc save, jobs.json apply; 13 cases + 2 must-fail).
- S2b inference turn — done (JUN-1036: finished-turn apply —
  ok/silent/error/`delivery_failed` statuses, peer/placement lines,
  `## Error` appendix, usage/run audit rows, unresolved-lane message;
  7 cases. The live rail call stays open; executions-ledger and
  delivery-queue ROWS stay slice S5 — only their observable inputs,
  statuses and messages are produced here).
- S2d turn assembly core — done (JUN-1036: history-messages, wrap-up
  notice/threshold/latch-inject, turn-limit spellings/parse/resolve,
  max-turns precedence; 35 cases + 2 must-fail. The `run` loop itself
  stays host-side (MCP/browser/dapps/tool wiring, clarify-fn,
  subagents, live rail), as do `system-prompt`/`skills-index`/
  `context-file` (skills home + context file + live clock reads).
  Deliberate: the cap sentinel is the legacy `MAX_SAFE_INTEGER`
  on every runner (unobservable per the legacy's own comment); `∞`
  is built from its code point, never a `\u` escape. Keyword roles
  are dropped exactly like the legacy `contains?` check drops them.)
- S2c pure policy core — done (JUN-1036: confine policy/read-denial/env/
  seatbelt-profile/bwrap-args/command + quiet warn/diag/wake/linger/
  turn-failed predicates; 35 cases + 2 must-fail. The live rail call,
  process supervision runtime and readline chat stay host-side; the
  linger loop rides S5c's sync port (injected fns). Port divergences:
  `secret-paths` takes `itonami-dir` explicitly (legacy reads
  ITONAMI_DIR); `bwrap-args` takes `:scan-fn` (cases stub the `find`
  scan); `command` takes `:tmpdir` (cases pin it); `:confine` op redacts
  scratch roots to `<home>`/`<idir>` and drops `:env` from `command`
  results (machine env is covered by `confine-env` cases). `backend`
  probe and `audit*!` writers stay host-side. Note: S5c's merged
  `quiet.cljc` supersedes the S2c twin (same predicates, sync loop
  added); the `:quiet` op calls `quiet-notify-linger-seconds` via
  `process.cljc`. The S5c loop omits the legacy diagnostic-mute
  displacement — predicate ported, loop-level case absent.)
- S3a store verbs pause/status — done (JUN-1036: 5 cases + 2 must-fail).
- S3b read/show ops — done (JUN-1036: model/fallback first; doctor +
  sessions list/show/resume-refusal second, 18 cases. The S6-owned
  doctor finding kinds (unsupported toolsets/tools, browser backend,
  oauth cache) and the sqlite write path stay later slices; the resume
  re-entry landing was already a verbatim refusal in the legacy).
- S4a store verbs part 1 — done (JUN-1036: `cron notepad`
  set/get/delete/list with caps verbatim, job section wired into
  run prompts; `profile list`/`show` reads).
- S4b store verbs part 2 — done (JUN-1036: `profile check` (+`--summary`,
  `--all`), `readiness` (job-level findings + issue summary, corrected
  in S4d from a full-findings port), `refresh`,
  `itonamify` (+`--force`), `rail` (all verbatim incl. keep/skip/usage
  lines); `cron runs`/`history` rows with receipt join, `cron incidents`
  list/ack; 32 cases + 3 must-fail. Toolset/browser/oauth finding kinds
  stay S6; ledger/queue writes stay S5).
- S4c store verbs part 3 — done (JUN-1036: `profile refresh-bundle`,
  `release` (manifest flip + marker lift, +`--all`), `import`/`export`
  (definition byte copy + Hermes-side park on export, +`--force`);
  21 cases + 3 must-fail. `did` waits on identity crypto (S5+)).
- S4d store verbs part 4 — done (JUN-1036: `profile adopt`
  (scheduler flip + Hermes park + bundle CID, `--all` batch refusal),
  `profile soul` (registry/worlds render with backup + receipt, limit
  and body-guard refusals), `profile organize` (registry projection
  via `with-org`), `profile hy-stage` (hash-checked payload staging),
  `profile hy-migrate` (`.py`→`.hy` rewrite under the tick lock +
  bundle refresh); 31 cases + 4 must-fail. `readiness` corrected to
  job-level only (model/state.db/pause stay doctor-only).
- S4e store verbs part 5 — done (JUN-1036: `profile did`
  (fleet-seed did:key with registry epochs, `--registry`/`--out`,
  REFUSED without the seed; pure-cljc Ed25519 verified against
  node:crypto); `profile bundle` (read-only CID + manifest print,
  missed in the S4c denominator). 11 cases + 2 must-fail.
  S4 profile verbs complete; S5 messaging writes next.
- S5 messaging: gateway/send/deliver/ledger/peers/chat.
- S5a state plane — done (JUN-1036: delivery vocabulary + queue/ledger
  sqlite writes; 28 cases + 5 must-fail — see the evidence doc.
  Verified divergences, all covered by cases or recorded here:
  - fencing answers come from a follow-up SELECT (the hio writer returns
    no change count); identical outcomes in the single-writer oracle.
  - no BEGIN IMMEDIATE around claim/recover; concurrent multi-process
    fencing is best-effort (the oracle runs one writer per home).
  - queue `finish!`/`recover` return pre-update rows with the status
    overridden, verbatim from the legacy (`status` assoc on the read row).
  - `job_json` keeps the legacy indent-2 layout; key order follows the
    map's iteration order (Basilisp maps are hash-ordered, so shared
    cases assert parsed content, and byte equality with the legacy is
    recorded in the evidence doc via a one-off oracle).
  - `get-incident` selects the same 10 keys as the legacy (which names
    every column it reads); `incidents` selects the same 12.
  - timestamps take an explicit trailing arg in the port (production
    passes live time); the live `recover` grace math uses live now-ms
    with fixed seeds, so outcomes stay deterministic.
  - a nil ledger owner keeps its row (legacy kill(nil) reads alive);
    a nil queue owner reads dead (legacy parseInt gives NaN).
- S5b drain rail + gateway transport core — done (JUN-1036:
  `drain.cljc` (deliver-output/settle/drain-queued/tick-abandoned/
  output-doc/save-output/record-run-state/finish-run, sequential where
  the legacy fans out — terminal rows identical) + `gateway.cljc`
  (platform-value/delivery-transport/resolve-delivery-transport/
  resolve-injection-adapter/send-watcher-message!, adapters injected).
  20 cases + 4 must-fail; 361 cases + 32 must-fail green on
  basilisp 0.5.1 / bb 1.12.218 / nbb 1.4.208, canon byte-identical —
  see the evidence doc. Verified divergences:
  - the drain answers `{:drained n}` / `{:recovered n}` counts (the
    legacy answers the row count the same way; promise fan-out is a
    host-side shape, not an observable value).
  - `finish-run!` takes the loop result as data (injected in cases);
    the live agent loop stays host-side.
  - platform/config maps are looked up by string lane with keyword
    `:enabled` flags, verbatim from the legacy.
  - the harness `test` invocation is relative (`test/cases.edn`) so
    reported output-file paths stay `test/scratch/...` on every
    machine; CLI print paths were already cwd-relative.
  Watcher fan-in (batching/timers), `platform_adapters`, peer
  serve/discover and the live gateway serve loop stay S5c/S6.
- S5d unit 1 peer messaging plane + send — done (JUN-1036:
  `peer.cljc` did:key parse/valid/pub->did, sign/open envelopes +
  auth header, peers.edn read/write/add, ensure-rail, rebase-paths,
  bundle materialize/fetch, service-record projection/overlay,
  http-json mapping + `cli.cljc` run-send-step/ops-send.
  15 cases + 7 must-fail; 468 cases + 50 must-fail green on the
  rebased union (incl. S2c) on basilisp 0.5.1 / bb 1.12.218 /
  nbb 1.4.208, canon byte-identical — see the evidence doc.
  Verified divergences:
  - crypto/time/nonces/fetch ride injected seams (`:sign` `:verify`
    `:now-ms` `:random-hex` `:nonces` `:fetch`); the live send /
    gateway-serve / peer socket commands stay host-side (S6).
  - `service-records->peer-infos` / `overlay-service-records` answer
    keyword `:models` / `:load`, so record overlay overwrites instead
    of duplicating the key.
  - a nil rail passes through as nil (no `(str nil)` coercion), so
    `ensure-rail` leaves the config unchanged.
  - envelope maps keep string keys end to end; trust/peer-info maps
    keep keyword keys.
  Discover/remote-run/serve routes stay unit 2.
- S5d unit 2 discover/remote-run/serve routes — done (JUN-1036:
  `peer.cljc` peer-info/discover (one gossip hop)/pick-peer/
  infer-peers/infer-via-peers/remote-run (submit + poll loop +
  receipt + workspace-write application, bundle files+cid injected) +
  peer/models/chat/infer/bundle/run-accept/run-poll routes behind
  the serve-route dispatch + local-gateway-complete + placement
  order loop with `:run-local` host seam.
  19 cases + 7 must-fail; 487 cases + 57 must-fail green on the
  rebased union on basilisp 0.5.1 / bb 1.12.218 / nbb 1.4.208,
  canon byte-identical — see the evidence doc. Verified divergences:
  - discovery infos are keyword-normalized (`:load` `:max-runs`
    `:models` `:agent-models` `:profiles` `:peers`); the wire JSON
    and the verified signing fields are untouched.
  - `remote-run` `:writes` are sorted (parsed-JSON key order differs
    per runner) and the run `workspace/` dir is created before
    applying writes.
  - the scripted clock pops once per read; crypto/time/sleep/rails/
    local-run/fetch ride injected seams (`:sign` `:verify`
    `:now-ms` `:random-hex` `:nonces` `:fetch` `:sleep` `:complete`
    `:run-local` `:bundles`). The socket, child processes, agent
    loop, live rails, health syscalls and the live serve commands
    stay host-side (S6).
  - the run-poll done/failed dissoc branches have no case (no step
    can complete a run in the shared `:runs` atom); the 202 running
    read, 404, requester and poll-name checks are covered.
- S6a tool registry core — done (JUN-1036: `tools.cljc`
  schemas table (58 tool schemas) + toolsets + name/unsupported/
  approval/enabled/execution-disabled/runtime predicates.
  8 cases + 5 must-fail; 495 cases + 62 must-fail green on the
  union on basilisp 0.5.1 / bb 1.12.218 / nbb 1.4.208, canon
  byte-identical — see the evidence doc. Verified divergences:
  `runtime-tool-names` ports only the explicit 5-arity (the 4-arity
  reads the host PATH); the `computer_use` schema rides as inlined
  static data. Next: S6b clarify/collect/tts-shape/webguard-pure.
- S6b-2 connections/dapps status surface — done (JUN-1036:
  `dapps.cljc` registry pin + server table + resolve-checkout + status,
  `oauth.cljc` store-name, `connections.cljc` connection-status +
  auth-status; `:dapps` / `:connections` shared-case ops.
  11 cases + 2 must-fail; 640 cases + 75 must-fail green on
  basilisp 0.5.1 / bb 1.12.218 / nbb 1.4.208, canon byte-identical —
  see the evidence doc. Verified divergences:
  - `store-name` clamps the 128-char cut (`subs` to the min length):
    the legacy host (.cljk on Node) runs JS substring semantics, which
    keep a short string whole; the clamping port matches on every
    runner (bare `subs` throws on bb/JVM for short names).
  - `env` is one merged map (the CLI passes the case env or the whole
    process env, so production keeps the host fallbacks and cases stay
    hermetic); `~` expands against HOME from that same map; the west
    sibling joins with `/../..` (POSIX resolves it; only parsed
    content is asserted).
  - a configured server is answered as `mcp` even with `"mcp": false`
    (the flag only admits unconfigured names), verbatim from the
    legacy `or`.
  Product `dapps`/`connections` command wiring, `with-servers` /
  `select-servers` (MCP allowlist), install/authorize (config writes +
  human approval) and the OAuth flows stay S6.)
- S6b-3 local rail selection core — done (JUN-1036: `rails.cljc`
  small-model? / normalize-model-ids / detect-entries / all-models /
  agent-models / select-candidates with a `:rails` shared-case op.
  19 cases + 2 must-fail; 659 cases + 77 must-fail green on
  basilisp 0.5.1 / bb 1.12.218 / nbb 1.4.208, canon byte-identical —
  see the evidence doc. Verified divergences:
  - `small-model?` answers a boolean (the legacy answers the re-find
    match; every caller branches on truthiness, so values agree).
  - the trailing `\b` is an explicit `[^A-Za-z0-9_]`-or-end guard:
    regex `\b` never matches on bb (PORTABILITY.md); the legacy
    leading position is unchanged (re-find, no anchor).
  - `detect-entries` takes probed [port base ids] rows (injected, as
    elsewhere); the live fan-out, probe, rails.edn IO and complete's
    fetch loop stay S6.)
- S6b-4 mcp config + allowlist + dapps run-wiring — done (JUN-1036:
  `mcp.cljc` enabled? / names-for / command-for / server-env /
  sanitize / tool-name / object-schema / filter-patterns / glob-match? /
  tool-allowed? with a `:mcp` case op; `dapps.cljc` gains
  with-servers / select-servers with `:dapps` verbs. 28 cases +
  2 must-fail; 693 cases + 79 must-fail green on
  basilisp 0.5.1 / bb 1.12.218 / nbb 1.4.208, canon byte-identical —
  see the evidence doc. Verified divergences:
  - `with-servers` success path is FIXED, not verbatim: the legacy
    threads `acc` twice (`(update acc ...)` inside `->`), so its
    resolvable-server path always crashes with a 3-arg keyword call.
    Only the keep/error branches ever ran in the legacy.
  - `expand-string` is a hand scan, not str/replace-with-fn: what the
    replacement fn receives is not the groups vector on every runner
    (on Basilisp the key arrives nil and every variable renders
    `${}`). Single pass, no rescan — the legacy replaceAll semantics.
  - `names-for` sort/vec shape and the `mcp-` strip are verbatim; a
    configured server stays `mcp` under `"mcp": false` (dapps rule).
  Message shapes (rpc/handshake/call inputs, collect-results,
  render-result, discover projection), exchange, oauth-cache-ready
  and the dispatch stay S6 — shapes now done in S6b-5 below.)
- S6b-5 mcp message shapes — done (JUN-1036: `mcp.cljc`
  handshake-lines / call-line / collect-call-results /
  collect-exchange-result / render-result with five `:mcp` verbs.
  20 cases + 2 must-fail; 713 cases + 81 must-fail green on
  basilisp 0.5.1 / bb 1.12.218 / nbb 1.4.208, canon byte-identical —
  see the evidence doc. Verified divergences: none — pure shape
  port; error maps stay string-keyed like the legacy, collect fns
  take string-keyed req maps (EDN-passed args have string keys).
  Authoring notes (not divergences): unescaped `"` inside a
  docstring breaks loading on every runner (fixed by rewording);
  expected JSON strings are compact, Python-default spaced
  separators fail identically everywhere. Exchange spawn,
  oauth-cache-ready, discover projection and call dispatch stay S6.)
- S6b-6 homeassistant arg shapes — done (JUN-1036:
  `homeassistant.cljc` entity-id-error / service-error /
  blocked-error / coerce-data / build-payload / project-states /
  project-state / project-services / project-affected with a
  `:homeassistant` case op. 26 cases + 2 must-fail; 739 cases +
  83 must-fail green on basilisp 0.5.1 / bb 1.12.218 / nbb 1.4.208,
  canon byte-identical — see the evidence doc. Verified
  divergences: throwing guards answer reason strings (nil when
  fine) instead of throwing, so refusals pin as data with no
  host-dependent ex-message; validation order verbatim. Fetch,
  JSON envelopes and tool dispatch stay S6.)
- S6b-7 computer-use arg shapes — done (JUN-1036:
  `computer_use.cljc` canon-keys / blocked-input? / select-window /
  build-request / content-text / image-parts / result-error /
  structured / error+success-result / action-request with a
  `:computer-use` case op (ctx atom built from case data).
  31 cases + 2 must-fail; 770 cases + 85 must-fail green on
  basilisp 0.5.1 / bb 1.12.218 / nbb 1.4.208, canon byte-identical —
  see the evidence doc. Verified divergences: blocked-input?
  answers a boolean (legacy answers the match; callers branch on
  truthiness); throws pin as `{:crash static-message}`. Verbatim
  quirks pinned: alt reports as option (alias table), empty text
  wins over the result-error fallback. Driver spawn, capture/list
  flows, approval and call dispatch stay S6.)
- S6b-8 code-rpc shaping + hints — done (JUN-1036: `code_rpc.cljc`
  tool-error / result-error / tool-errors-since / sandbox-tools-for /
  terminal-blocked-params / missing-import-hint / failure-hint-rules /
  failure-hint / hint-on-failure with a `:code-rpc` case op.
  21 cases + 2 must-fail; 791 cases + 87 must-fail green on
  basilisp 0.5.1 / bb 1.12.218 / nbb 1.4.208, canon byte-identical —
  see the evidence doc. Verified divergences: none — pure shape
  port; the `…` marker is the literal U+2026. Shim-header /
  tool-stubs / generate-hermes-tools, token check, dispatch, budget,
  log and the serve loop stay S6.)
- S6b-9 hermes_tools stub generation — done (JUN-1036:
  `shim-header` / `tool-stubs` (byte-exact copy, BOM spliced as
  `(char 65279)` — no `\u` escape in source) /
  `generate-hermes-tools` with a `:code-rpc` verb. 2 full-output
  cases + 1 must-fail; 793 cases + 88 must-fail green on
  basilisp 0.5.1 / bb 1.12.218 / nbb 1.4.208, canon byte-identical —
  see the evidence doc. Byte-identity to the legacy literals proven
  by independent evaluation (`=` on both values). Token check,
  dispatch, budget, log and the serve loop stay S6.)
- S6b-10 finish-call dispatch tail — done (JUN-1036: `token-str` /
  `make-state` / `finish-call!` (+ `args-preview`, `round-half-up`)
  with `:code-rpc` verbs. 11 cases + 2 must-fail; 804 cases +
  90 must-fail green on basilisp 0.5.1 / bb 1.12.218 / nbb, canon
  byte-identical — see the evidence doc. Verified divergences:
  `round-half-up` is per-runner by construction (`Math/round` /
  `js/Math.round` / `math.floor(x + 0.5)`); non-negative durations
  only, where all three agree. Clock-derived durations are stripped
  from shared-case comparisons; pinned are result, count, tool name
  and the 80-char args preview (74-char truncation boundary pinned).
  Token check, dispatch, budget enforcement and the serve loop
  stay S6.)
- S6b-11 transport-name + token-check tail — done (JUN-1036:
  `token-ok?` / `duration-seconds` / `int-seq?` / `req-file?` /
  `res-name` with `:code-rpc` verbs; `finish-call!` refactored onto
  `duration-seconds`. 29 cases + 2 must-fail; 833 cases + 92
  must-fail green on basilisp 0.5.1 / bb 1.12.218 / nbb, canon
  byte-identical — see the evidence doc. Verified divergences:
  `int-seq?` uses numeric `==` (bb/SCI `=` is strict across
  Long/Double); `token-ok?` accumulates with no early exit, NUL pad
  via `(char 0)`; `res-name` width counts the sign, overflow never
  truncates; half-up duration boundary pinned. Dispatch, budget
  enforcement and the serve loop stay S6.)
- S6b-12 handle-request gates + message-text — done (JUN-1036:
  `refusal` / `terminal-args` / `message-text` with `:code-rpc`
  verbs. 10 cases + 2 must-fail; 843 cases + 94 must-fail green on
  basilisp 0.5.1 / bb 1.12.218 / nbb, canon byte-identical — see
  the evidence doc. Verified divergences: `refusal` returns the
  `tool-error` JSON envelope (bare-message expects rejected by all
  three); allow-list gate precedes budget gate; `""` stays `""`.
  `finish-call!`'s catch still uses `(str e)` — no EDN value makes
  `json-str` throw, so that path awaits a host-side probe. Async
  `dispatch-call!` branching and the serve loop stay S6.)
- S6b-13 doctor finding units — done (JUN-1036:
  `toolset-findings` / `tool-findings` / `browser-findings` /
  `oauth-findings` with a `:doctor-kinds` case op. 14 cases +
  2 must-fail; 857 cases + 96 must-fail green on
  basilisp 0.5.1 / bb 1.12.218 / nbb, canon byte-identical — see
  the evidence doc. Verified divergences: none — pure data reads
  over explicit witness inputs; `:agents?` threaded through
  `request-id` but unread by the CLI rendering; veto/headless/OOM
  / carrier / ssrf, the local-Chrome probe, host credential-cache
  reads, role/capability gating and the evidence-table rendering
  stay S6.)
- S6 tools (rest): backends + bridge/chat/vault/mcp/dapps CLIs.
