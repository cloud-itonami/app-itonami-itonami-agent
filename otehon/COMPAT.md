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
| `profile list/show/check/refresh/itonamify/adopt/release/soul/did/organize/readiness/import/export/hy-*` | `profile-cmd` | `check`, `store-plan` (import/export copy plan), `profile list/show/check/readiness/refresh/itonamify/rail` | S4 remaining verbs (adopt/release/soul/did/organize/import/export/hy-*; S6-owned check/readiness finding kinds deferred, recorded below) |
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

## Module checklist

Every `src/itonami/agent/*.cljk` module and its `.cljc` twin status.
`planned-S#` names the slice that ports it; roles marked `*` are verified
from source, the rest follow the module name until their slice lands.

| module | twin | status |
|---|---|---|
| `browser.cljk` *browser tools* | — | planned S6 |
| `browser_cloud.cljk` | — | planned S6 |
| `browser_provider.cljk` | — | planned S6 |
| `chat_platform.cljk` | — | planned S5 |
| `clarify.cljk` | — | planned S6 |
| `code_rpc.cljk` | — | planned S6 |
| `collect.cljk`, `collect_io.cljk` | — | planned S6 |
| `computer_use.cljk` | — | planned S6 |
| `confine.cljk` *OS sandbox* | — | planned S2 |
| `connections.cljk` | — | planned S6 |
| `cron.cljk` *jobs/schedules* | `cron.cljc`, `tick.cljc`, `run.cljc`, `exec.cljc` | partial (plans + `run-job` host side and finished-turn apply done, JUN-1036 S2a/S2b; live rail + ledger/queue rows S5) |
| `dapps.cljk` | — | planned S6 |
| `delivery.cljk`, `delivery_queue.cljk` | — | planned S5 |
| `execution_ledger.cljk` *reads* | `cli.cljc` (runs-for, incidents-for: rows/incidents/ack over `cron/executions.db`) | done reads (JUN-1036 S4b; claim/finish/upsert/resolve writes stay S5) |
| `execution_ledger.cljk` *writes* | — | planned S5 |
| `gateway.cljk` | `gateway` (watcher egress surface) | partial (delivery loop S5) |
| `homeassistant.cljk` | — | planned S6 |
| `host.cljk` *node/file/env/process* | `io.cljc` | partial (files/env/clock/listing/pids/spawn done, JUN-1036 S2a) |
| `identity.cljk` | `market.cljc` (attestations) | partial (node identity S5) |
| `loop.cljk` *agent turn loop* | `turn.cljc` (gate) | partial (turn execution S2) |
| `main.cljk` *CLI dispatch* | `cli.cljc` | partial (this table) |
| `manifest.cljk` | `manifest.cljc` | done |
| `mcp.cljk`, `mcp_http_client.mjs` | — | planned S6 |
| `notepad.cljk` | `notepad.cljc` | done (JUN-1036 S4a; clear is ported, unwired — no CLI surface) |
| `oauth.cljk` | — | planned S6 |
| `p2p.cljk` *node server/trust* | `peer.cljc` | partial (sync plans done; serve/run S5) |
| `pause.cljk` * | `pause.cljc` | done (JUN-1036 S3a) |
| `platform_adapters.cljk` *lane adapters* | — | planned S5 |
| `process.cljk` | — | planned S2 |
| `profile.cljk` *store layout* | `profile.cljc`, `status.cljc` (names), `store.cljc` | partial (verbs S4) |
| `profile.cljk` *bundle/manifest lifecycle* | `bundle.cljc` (bundle-files/manifest/cid, default-manifest, with-inference-rail, write-manifest!, with-bundle, refresh-bundle-cid) | done itonamify/refresh/rail (JUN-1036 S4b; did/soul/organize/adopt/release/import/export/hy-* stay later) |
| `provider.cljk` *chain/resolution* | `rail.cljc` (job target), `model.cljc` (display chain) | partial (live calls S2) |
| `profile.cljk` *set-model-text* | `model.cljc` (verbatim port, throws as ex-info) | done for model/provider keys (JUN-1036 S3b) |
| `quiet.cljk` | — | planned S2 |
| `rails.cljk` | — | planned S3b |
| `soul.cljk` | — | planned S4 |
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
- S3a store verbs pause/status — done (this unit: 5 cases + 2 must-fail).
- S3b read/show ops — done (JUN-1036: model/fallback first; doctor +
  sessions list/show/resume-refusal second, 18 cases. The S6-owned
  doctor finding kinds (unsupported toolsets/tools, browser backend,
  oauth cache) and the sqlite write path stay later slices; the resume
  re-entry landing was already a verbatim refusal in the legacy).
- S4a store verbs part 1 — done (JUN-1036: `cron notepad`
  set/get/delete/list with caps verbatim, job section wired into
  run prompts; `profile list`/`show` reads).
- S4b store verbs part 2 — done (JUN-1036: `profile check` (+`--summary`,
  `--all`), `readiness` (doctor findings + issue summary), `refresh`,
  `itonamify` (+`--force`), `rail` (all verbatim incl. keep/skip/usage
  lines); `cron runs`/`history` rows with receipt join, `cron incidents`
  list/ack; 32 cases + 3 must-fail. Toolset/browser/oauth finding kinds
  stay S6; ledger/queue writes stay S5).
- S4 remaining: adopt/release/soul/did/organize/import/export/hy-*.
- S5 messaging: gateway/send/deliver/ledger/peers/chat.
- S6 tools: backends + bridge/chat/vault/mcp/dapps CLIs.
