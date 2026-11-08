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
| `cron list/create/run/tick/runs/incidents/notepad/status` | `cron-cmd` | `tick` (due/next-fire plan), `run-exec` (one job's host side, stops at `:planned`) | done run/tick-exec (JUN-1036 S2a; rail call S2b), S4 notepad/status |
| `profile list/show/check/refresh/itonamify/adopt/release/soul/did/organize/readiness/import/export/hy-*` | `profile-cmd` | `check`, `store-plan` (import/export copy plan) | S4 remaining verbs |
| `doctor` | `doctor-cmd` | — | S3b (needs readiness ports) |
| `sessions [list]/show/resume` | `sessions-cmd` | — | S3b (sqlite host reads) |
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
| `cron.cljk` *jobs/schedules* | `cron.cljc`, `tick.cljc`, `run.cljc`, `exec.cljc` | partial (plans + `run-job` host side done, JUN-1036 S2a; executor/rail S2b) |
| `dapps.cljk` | — | planned S6 |
| `delivery.cljk`, `delivery_queue.cljk` | — | planned S5 |
| `execution_ledger.cljk` | — | planned S5 |
| `gateway.cljk` | `gateway` (watcher egress surface) | partial (delivery loop S5) |
| `homeassistant.cljk` | — | planned S6 |
| `host.cljk` *node/file/env/process* | `io.cljc` | partial (files/env/clock/listing/pids/spawn done, JUN-1036 S2a) |
| `identity.cljk` | `market.cljc` (attestations) | partial (node identity S5) |
| `loop.cljk` *agent turn loop* | `turn.cljc` (gate) | partial (turn execution S2) |
| `main.cljk` *CLI dispatch* | `cli.cljc` | partial (this table) |
| `manifest.cljk` | `manifest.cljc` | done |
| `mcp.cljk`, `mcp_http_client.mjs` | — | planned S6 |
| `notepad.cljk` | — | planned S4 (sqlite host) |
| `oauth.cljk` | — | planned S6 |
| `p2p.cljk` *node server/trust* | `peer.cljc` | partial (sync plans done; serve/run S5) |
| `pause.cljk` * | `pause.cljc` | done (JUN-1036 S3a) |
| `platform_adapters.cljk` *lane adapters* | — | planned S5 |
| `process.cljk` | — | planned S2 |
| `profile.cljk` *store layout* | `profile.cljc`, `status.cljc` (names), `store.cljc` | partial (verbs S4) |
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
- S2b inference turn (rail call, delivery lanes, executions ledger).
- S3a store verbs pause/status — done (this unit: 5 cases + 2 must-fail).
- S3b read/show ops: model/fallback/doctor/sessions.
- S4 store writes: adopt/release/refresh/notepad/profile verbs, soul.
- S5 messaging: gateway/send/deliver/ledger/peers/chat.
- S6 tools: backends + bridge/chat/vault/mcp/dapps CLIs.
