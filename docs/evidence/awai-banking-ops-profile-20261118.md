# awai-banking-ops profile — evidence of 2026-11-18

Tracking: [AWAI-3155](https://linear.app/kotobalabs/issue/AWAI-3155), under
[AWAI-3149](https://linear.app/kotobalabs/issue/AWAI-3149). Scope:
network-awai-apex ADR 0042 (D1–D4). Profile placement: root ADR
`adr-2610222100-hermes-itonami-separation` (itonami agents live under
`itonami/profiles/<p>/`).

## What was added

| Path | Role |
|---|---|
| `itonami/profiles/awai-banking-ops/` | The itonami agent definition: `itonami.edn`, `yakuwari.edn`, `SOUL.md`, `profile.yaml`, `config.yaml`, `cron/jobs.json` |
| `…/scripts/awai_banking/draft.cljk` | Pure draft builder: request → `awai.fin-draft/1` + wire text, or a refusal |
| `…/scripts/fin_draft_self_test.cljk` | Fixture test (reads only; exit 1 on any difference) |
| `…/scripts/fin_draft_inbox.cljk` | Drafts the holder's requests from `workspace/inbox` into `workspace/drafts` (host-local) |
| `…/scripts/deps.edn` | kotoba-lang/kotoba `lib/kotoba.swift` and `lib/kotoba.bytes`, pinned at `2e3b9ef25f06ef98924daa450c36da1b7c712189` |
| `…/fixtures/` | 5 requests (3 drafts, 2 refusals) and their expected outputs |
| `src/itonami/agent/cron.cljk` | A `.cljk` job script next to a `deps.edn` runs through kbb's deps door (`kbb <script>`); otherwise unchanged |

Supported kinds: pain.001.001.09, pacs.008.001.08, MT103 (MT103 for reading
and comparison with pacs.008 only). Not supported yet, refused by name:
camt.053/054 reconciliation, pacs.002 status reports, bookkeeping proposals.

## Authority

`yakuwari.edn`, copied verbatim into `itonami.edn :capabilities`:
`read_source`, `review_source`, `draft_artifact`, `record_local_receipt` are
`:autonomous` (own workspace and fixtures only). `transfer_funds`, `spend`,
`external_messaging`, `use_signing_identity`, `export_private_keys`,
`publish`, `storage_upload`, `delete_data`, `delegate_task`,
`manage_services`, `git_push`, `git_merge`, `enable_cron` are `:blocked`.
`:authority :default-decision :blocked`. Placement `:local` only. Both cron
jobs are `paused` (`enabled false`) and run only on demand.

Every draft carries `:sent? false` and the open points: L2 not checked (ADR
0042 O3 pending), L3 not checked (O5 pending), not sent. MT103 drafts add the
ADR 0042 D1 note that new cross-border MT103 are not to be sent.

## Runs

Environment: macOS, Node v26.0.0, kbb engine `kotoba-lang/org-babashka-nbb`
`1675e6a`, itonami-agent base `53cd269`, `ITONAMI_DIR=<repo>/itonami`.
Commands use `bin/itonami-agent-kbb`; the native `bin/itonami-agent` blocks
the `cron` routes (`blocked pending Kotoba/Amu selfhost port`).

| Check | Command | Result |
|---|---|---|
| Fixture test through the CLI | `itonami-agent-kbb cron run efece4c1ecc5 -p awai-banking-ops` | `5 fixtures, 0 failed` (3 drafted, 2 refused as expected) |
| Inbox drafting through the CLI | `itonami-agent-kbb cron run 881b5d26983b -p awai-banking-ops` with two requests in `workspace/inbox` | `drafted pacs008-usd-two pacs.008.001.08 sha256=7acf1e9d…`, `refused pain001-refused-uppercase-uetr builder-refused`; the drafted XML is byte-identical to the fixture |
| Negative control | the fixture test on a copy whose `pacs008-usd-two.xml` was altered | `5 fixtures, 1 failed`, exit 1; restored copy exit 0 |
| XSD (L1) of the expected XML | `xmllint --schema` with the digest-pinned XSDs of `kotoba-lang/kotoba lib/kotoba.swift/conformance/xsd-pins.edn` | `pain001-eur-single.xml validates`, `pacs008-usd-two.xml validates` |
| Profile manifest | `itonami-agent-kbb profile check awai-banking-ops` | `1 profile(s): 0 with errors, 0 with warnings`, no drift |
| itonami-agent suite | `kbb --backend sci --classpath src:test run-tests.cljk` | 316 tests, 2025 assertions, 0 failures (includes the new deps-door case) |

Expected wire digests (sha256): `pain001-eur-single.xml`
`2b40693ca1449db217ffe2e377693817a82f08a2675bf3c17d417822be7b5a8b`,
`pacs008-usd-two.xml`
`7acf1e9d92c1f9115b9cc40b4f7c68c1ba2c84f7bceef90bc6f2d0e271a7bf48`,
`mt103-usd.fin`
`c742c5fa638944a1b01d7e6470d0e07cac80f4112e5464558eda96a0a221ea10`.

No model was called: the jobs are `no_agent` scripts. No network request was
made by the scripts; kbb fetched the pinned kotoba-lang/kotoba revision into
`~/.gitlibs` once.

## Not done

- Statement reconciliation (camt.053/054), pacs.002 status handling and
  bookkeeping proposals. `kotoba.swift` has no camt or pacs.002 builders; the
  Python codec that has them is not on this profile's path.
- Registration in `com-junkawasaki/root manifest/agent-registry` (no entry
  has `:agent/runtime :itonami` yet; JUN-302) and installation into a host's
  `~/.itonami`. The profile is a reviewed definition, not a running bot.
- L2 and L3 (ADR 0042 O3, O5) and anything involving another person's
  transactions (O1).
- Running the profile test in CI: this repository's CI runs native gates
  and an oracle job; a kbb job for profile scripts is not added.
