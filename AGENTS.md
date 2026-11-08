# itonami-agent

- **Selfhost only**: the required itonami-agent execution path is Kotoba/Amu
  selfhost native kexe. Do not use Node, kbb, nbb or JVM for runtime, CI,
  build, packaging, gates or fallback. An unported route remains blocked.
- `bin/itonami-agent` still sends unported routes to `bin/itonami-agent-kbb`.
  Treat that branch and the legacy `.cljk` launcher as migration inventory,
  not as a qualified selfhost path. AWAI-60 tracks its removal; AWAI-131
  tracks the profile-aware native host and first live turns.
- A one-off developer reference oracle is allowed only under the workspace
  rule: record the command, versions, inputs and outputs; never make it a
  required build, CI, runtime or fallback path.
- Hermes compatibility is the contract: change a file format only together with
  the Hermes code that reads it. Qualify native behavior with Amu selfhost
  checks and negative controls; legacy `run-tests.cljk` is not a native gate.
- **Otehon (`otehon/`)**: the `.cljc` version of the itonami agent core
  (owner 2026-11-07; ADR 0026 D18; JUN-1008). It runs on Basilisp, Babashka
  and nbb, which the workspace Awai Otehon rule allows on the Otehon path.
  It is separate from the Native route above: no native gate, build,
  packaging or runtime path reads it, and neither side falls back to the
  other. Its CI job is oracle-only and not a required check. Use `#?(:lpy …)`
  for Basilisp and never `#?@`. See `otehon/README.md`.

## Two CLIs — 分離の正本（hermes agent CLI / itonami agent CLI）

`hermes` と `itonami-agent` は **同じ profile フォーマットを共有する別物**。
store・状態を混ぜない。推論 rail も、下の Awai Otehon の例外を除いて混ぜない:

| | hermes agent CLI | itonami-agent CLI |
|---|---|---|
| 実体 | Hermes Agent（Python, upstream） | 本 repo（Kotoba/Amu selfhost native kexe が必須。旧 `.cljk`/kbb 経路は移行中） |
| profile store | `~/.hermes/profiles/<name>/` | `~/.itonami/profiles/<name>/`（`ITONAMI_DIR`） |
| 状態 / DB | `~/.hermes/` 配下（sessions・executions.db 等） | `~/.itonami/` 配下（runs・receipts 等） |
| 推論 | config.yaml `model:` — openrouter 等。Awai Otehon の profile は、明示した Awai node の `/v1` も使える | itonami.edn `:inference` — murakumo（mishima 等） |
| murakumo / mishima | Awai Otehon の profile に限り、Awai node の `/v1` 経由で使える。それ以外は**使わない** | **使う** |
| 移動 | `profile export`（itonami store → Hermes） | `profile import`（Hermes → itonami store） |

この表は profile 形式と store の契約を示す。`profile import/export` の現行
CLI route は selfhost 資格未取得であり、移行完了まで起動手順に使わない。

- **Awai Otehon 以外の Hermes agent profile に murakumo / mishima を使わない** — config.yaml の
  `model:` でも、cron job の `model` / `provider` pin でも（job の pin が
  config より優先で拾われるため、両方を禁じる）。
- **Awai Otehon の例外は Hermes 側だけに適用する**（`network-awai/network-awai-apex` ADR 0026 D4）。
  本 repo の selfhost 必須と、下の `:deny-hosts` は変えない。
- **itonami.edn の `:inference` は itonami-agent CLI 専用。** Hermes は
  itonami.edn を読まない（読み飛ばすだけ）。逆に itonami-agent の推論は
  `:inference` の rails のみ（`:deny-hosts` に openrouter）。
- フォーマット互換（両 CLI が読むファイル）を変えるときは README の互換表と
  この表を同時に更新する。

## awai fs storage — local agent 共通退避先（2026-11-08〜）

ローカル開発の容量枯渇に使う共通 storage。local agent（itonami-agent 含む）は
退避先としてここを記録し、個別の B2 prefix や restic repo を増やさない:

- warm 層: JuiceFS volume `awai-warm`（B2 S3 互換 `261023-github`、
  SQLite meta `~/.juicefs/awai-warm/`、writeback OFF）。検証済み
 （PUT→SHA→fresh-cache cold read）。macFUSE は未解決のため native mount なし
- cold / retire 層: restic repo
  `rclone:b2-261023-github:261023-github/awai-restic-20261108`、
  password は `~/.juicefs/awai-warm/restic-password`（mode 600、同 host 内。
  off-host escrow は未解決）。restic 0.19.1 は
  `workspaces/codex/jun-964-juicefs/restic-bin/restic` を使う。
  backend は rclone（S3 init は制限キーで不可だった）
- 退避フロー: quiescence 確認（mtime＋稼働 proc＋git/stash）→ backup
 （tag `awai-migrate-YYYYMMDD`）→ spot SHA（dump vs source、GB 級は timeout 500）
  → `restic check` → 削除。検証失敗時は削除しない
- 禁止: live service 配下（LaunchAgent 管理の sshd 等）の削除、他 agent の WIP の
  無断削除、稼働中 restore 先への接触。欠けた mount を空 dir で代替しない
- 設計: `docs/awai-fs-design.md`。追跡: JUN-964（Linear）
