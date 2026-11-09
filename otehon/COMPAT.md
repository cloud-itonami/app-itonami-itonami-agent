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
| `dapps` | `dapps-cmd` | — | S6 |
| `cron list/create/run/tick/runs/incidents/notepad/status` | `cron-cmd` | `tick` (due/next-fire plan), `run-exec` (host side + finished-turn apply; stops at `:planned` with no executor), `notepad` (KV verbatim), `runs`/`incidents` (ledger reads: rows list, ack) | done run/tick-exec + notepad + ledger reads (JUN-1036 S2a/S2b/S4a/S4b; ledger/queue writes S5) |
| `profile list/show/check/refresh/itonamify/adopt/release/soul/did/organize/readiness/import/export/hy-*` | `profile-cmd` | `check` (job-level readiness: the legacy command reads active jobs only; model/state.db/pause findings stay doctor-only), `store-plan` (import/export copy plan), `profile list/show/check/readiness/refresh/itonamify/rail/bundle/refresh-bundle/release/import/export/adopt/soul/organize/hy-stage/hy-migrate/did` (import/export executes the plan: definition byte copy + Hermes-side park on export; adopt flips scheduler + parks + refreshes CID; soul renders registry/worlds blocks with backup+receipt; organize projects the registry entry; hy-stage checks and stages the Hy payload; hy-migrate rewrites `.py` refs under the tick lock; did derives the fleet did:key; bundle prints CID + manifest) | S4 verbs complete (S6-owned check/readiness finding kinds deferred, recorded below) |
| `doctor` | `doctor-cmd` | `doctor` (store verbs; S6-owned finding kinds deferred, recorded below) | done (JUN-1036 S3b) |
| `sessions [list]/show/resume` | `sessions-cmd` | `sessions` (reads state.db read-only; resume refuses verbatim) | done (JUN-1036 S3b) |
| `model [NAME] [--provider]` | `model-cmd` | `model` (positional dir, otehon convention) | done (JUN-1036 S3b) |
| `fallback` | `fallback-cmd` | `fallback` (positional dir, otehon convention) | done (JUN-1036 S3b) |
| `status` | `status-cmd` | `status` | done (JUN-1036 S3a) |
| `pause [reason]` | `pause-cmd` | `pause` | done (JUN-1036 S3a) |
| `resume` | `resume-cmd` | `resume` | done (JUN-1036 S3a) |
| `send` | `send-cmd` | — | S5 |
| `gateway run/serve` | `gateway-run/serve` | — | S5 |
| `peer serve/run/discover/rails/exec/authorities` | `peer-cmd` | `peer-push/pull` (sync plans) | S5 remaining verbs |
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
| `code_rpc.cljk` | — | planned S6 |
| `collect.cljk`, `collect_io.cljk` | — | planned S6 |
| `computer_use.cljk` | — | planned S6 |
| `confine.cljk` *OS sandbox* | `confine.cljc` (policy, secret-paths, read-denial, confine-env, seatbelt-profile, bwrap-args, command) | partial (pure policy core done JUN-1036 S2c; `backend` probe and `audit*!` writers stay host-side) |
| `connections.cljk` | — | planned S6 |
| `cron.cljk` *jobs/schedules* | `cron.cljc`, `tick.cljc`, `run.cljc`, `exec.cljc` | partial (plans + `run-job` host side and finished-turn apply done, JUN-1036 S2a/S2b; ledger/queue rows S5a; drain rail — deliver/drain/settle/abandoned/finish-run — S5b; the live run-job loop + watcher fan-in stay open) |
| `dapps.cljk` | — | planned S6 |
| `delivery.cljk` | `delivery.cljc` (normalize/lanes/bot-chat token/adapter registry/classify-outcome, pure) | done (JUN-1036 S5a; shared cases assert the vocabulary incl. the outcome table) |
| `delivery_queue.cljk` | `queue.cljc` (`cron/deliveries.db`, `deliveries` + bounded `delivery_tombstones`, enqueue/get-status/claim-row/claim-next/finish/recover) | done (JUN-1036 S5a; fencing answers verified by follow-up SELECT — same outcome single-writer; `job_json` keeps the legacy indent-2 layout, key order is the map's iteration order) |
| `execution_ledger.cljk` *reads* | `cli.cljc` (runs-for, incidents-for: rows/incidents/ack over `cron/executions.db`) | done reads (JUN-1036 S4b; claim/finish/upsert/resolve writes stay S5) |
| `execution_ledger.cljk` *writes* | `ledger.cljc` (`cron/executions.db`, executions + incidents DDL verbatim; claim (throws on active)/mark-handoff-pending/adopt-claimed/mark-running/finish (completed\|failed + 1000-row overflow drop)/set-delivery-outcome/incident upsert/get/ack/alerted/list/resolve/withheld gate) | done (JUN-1036 S5a; fencing verified by follow-up SELECT; no BEGIN IMMEDIATE — single-writer oracle, see notes) |
| `gateway.cljk` | `gateway.cljc` (platform values, delivery transport, adapter/injection resolution, watcher send seam, send-transport!/route-key/identities/admission/coalesced/fan-in — pure core) | done (JUN-1036 S5b transport core + S5c fan-in with explicit flushes; background timers stay host-side) |
| `homeassistant.cljk` | — | planned S6 |
| `host.cljk` *node/file/env/process* | `io.cljc` | partial (files/env/clock/listing/pids/spawn done JUN-1036 S2a; + S5a: `now-micros-iso` (host-zone 6-digit fraction, the `iso-local` micros shape), `json-str-2` (indent-2, iteration order), `parse-iso-ms`, `kill-probe-alive?` (legacy kill(0) probe semantics), `sqlite-literal` numbers inline bare so REAL columns read back numeric) |
| `identity.cljk` ( FleetSeed/KDF/derive-key/did-of/parse-seed/key-id ) | `ed25519.cljc` (KDF + RFC 8032 expansion + clamped base-point multiply + did:key:z; host SHA-512/UTF-8 in `io.cljc` ) | done (JUN-1036 S4e; node `createPrivateKey` expansion reproduced exactly, oracle-checked) |
| `loop.cljk` *agent turn loop* | `turn.cljc` (gate) | partial (turn execution S2) |
| `main.cljk` *CLI dispatch* | `cli.cljc` | partial (this table) |
| `manifest.cljk` | `manifest.cljc` | done |
| `mcp.cljk`, `mcp_http_client.mjs` | — | planned S6 |
| `notepad.cljk` | `notepad.cljc` | done (JUN-1036 S4a; clear is ported, unwired — no CLI surface) |
| `oauth.cljk` | — | planned S6 |
| `p2p.cljk` *node server/trust* | `peer.cljc` | partial (sync plans done; serve/run S5) |
| `pause.cljk` * | `pause.cljc` | done (JUN-1036 S3a) |
| `platform_adapters.cljk` *lane adapters* | `adapters.cljc` (per-lane request builders + token-env/for-lane resolution + sync sends, error strings verbatim) | done (JUN-1036 S5c; env-only secrets, injected `:fetch`) |
| `process.cljk` | `process.cljc` (linger budget, pending-notification text, background mode) | partial (S5c; supervision runtime: watchers/heartbeats/checkpoints — host-side, not shared-case portable) |
| `profile.cljk` *store layout* | `profile.cljc`, `status.cljc` (names), `store.cljc` | partial (verbs S4) |
| `profile.cljk` *bundle/manifest lifecycle* | `bundle.cljc` (bundle-files/manifest/cid, default-manifest, with-inference-rail, write-manifest!, with-bundle, refresh-bundle-cid, copy-profile!) + `manifest.cljc` (with-org) + `cli.cljc` (set-parked!, release/transfer/adopt/organize/hy lines) | done itonamify/refresh/rail/bundle/refresh-bundle/release/import/export/adopt/organize/hy-stage/hy-migrate/did (JUN-1036 S4b/S4c/S4d/S4e) |
| `provider.cljk` *chain/resolution* | `rail.cljc` (job target), `model.cljc` (display chain) | partial (live calls S2) |
| `profile.cljk` *set-model-text* | `model.cljc` (verbatim port, throws as ex-info) | done for model/provider keys (JUN-1036 S3b) |
| `quiet.cljk` | `quiet.cljc` (warn/diag/wake predicates, linger budget, turn-failed?, sync linger loop) | partial (predicates + 35 S2c cases JUN-1036 S2c; sync linger loop with injected fns JUN-1036 S5c; live registry waits stay host-side) |
| `rails.cljk` | — | planned S3b |
| `soul.cljk` | `soul.cljc` (world/unit/role render + splice, limit, body guard) | done (JUN-1036 S4d) |
| `tools.cljk` *tool registry* | — | planned S6 |
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
- S6 tools: backends + bridge/chat/vault/mcp/dapps CLIs.
