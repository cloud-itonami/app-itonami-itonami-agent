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
