# awai-securities-ops profile — evidence of 2026-11-18

Tracking: [AWAI-3156](https://linear.app/kotobalabs/issue/AWAI-3156), under
[AWAI-3149](https://linear.app/kotobalabs/issue/AWAI-3149). Scope: network-awai-apex
ADR 0042 (D1–D4). Placement: root `adr-2610222100-hermes-itonami-separation`
(`itonami/profiles/<p>/`). Same layout as `awai-banking-ops`
([evidence](awai-banking-ops-profile-20261118.md)).

## What was added

| Path | Role |
|---|---|
| `itonami/profiles/awai-securities-ops/` | `itonami.edn`, `yakuwari.edn`, `SOUL.md`, `profile.yaml`, `config.yaml`, `cron/jobs.json` |
| `…/scripts/awai_securities/draft.cljk` | Pure: `:sese023` instruction drafts (with an optional MT54x comparison text), `:reconcile-settlement` (instruction vs sese.025/MT544-547 confirmation and sese.024/MT548 status), `:reconcile-holdings` (semt.002 vs the holder's positions); anything else is refused by name |
| `…/scripts/fin_securities_self_test.cljk` | Fixture test (reads only; exit 1 on any difference) |
| `…/scripts/fin_securities_inbox.cljk` | Handles the holder's requests in `workspace/inbox` (received messages in `workspace/inbox/messages`) into `workspace/drafts` |
| `…/scripts/deps.edn` | kotoba-lang/kotoba `lib/kotoba.swift` (which brings `lib/kotoba.securities`) and `lib/kotoba.bytes`, pinned at `4e7fc2b8e2d46944ea6bb0047c5897a312b64ac0` (kotoba#1354, AWAI-3154) |
| `…/fixtures/` | 6 requests, 5 received messages (custodian side, built with the same library), expected results |

## Authority

As `awai-banking-ops`: `read_source`, `review_source`, `draft_artifact` and
`record_local_receipt` are `:autonomous` (own workspace and fixtures only);
`transfer_funds` (no order, delivery, receipt instruction to a third party,
payment or trade), `spend`, `external_messaging` (no custodian, CSD, broker or
SWIFTNet), `use_signing_identity` and every other effect are `:blocked`;
`:default-decision :blocked`; placement `:local`; both cron jobs paused, run on
demand. SOUL forbids investment advice and market data.

## Runs

Environment: macOS, Node v26.0.0, `bin/itonami-agent-kbb` (the native
`bin/itonami-agent` blocks `cron` routes until the selfhost port),
`ITONAMI_DIR=<repo>/itonami`, host load 33–52.

| Check | Command | Result |
|---|---|---|
| Fixture test through the CLI | `itonami-agent-kbb cron run 95bb2c086c25 -p awai-securities-ops` | `6 fixtures, 0 failed`: 1 drafted, 3 reconciled, 2 refused (a bad ISIN check digit; a `:place-order` request) |
| Inbox through the CLI | `itonami-agent-kbb cron run ec98190dfbac -p awai-securities-ops` with 3 requests and 2 received MT messages | `refused place-order-refused unsupported-kind`, `reconciled reconcile-settlement-mt-diff`, `drafted sese023-receive-apmt`; the drafted XML and MT text and the reconciliation are byte-identical to the fixtures |
| Negative control | fixture test on a copy whose expected MT reconciliation was altered | `6 fixtures, 1 failed`, exit 1 |
| XSD (L1) | `xmllint --schema` with the pinned XSDs | the drafted sese.023 and the received sese.025, sese.024 and semt.002 fixtures `validate` |
| Manifests | `itonami-agent-kbb profile check` | `awai-securities-ops` and `awai-banking-ops`: 0 errors, 0 warnings, no drift |

What the reconciliation fixtures show:

- ISO 20022: the sese.025 confirmation matches the instruction field by field (`:settled? true`); the sese.024 status is acknowledged and matched.
- MT: the MT545 confirmation is for 90 units against an instruction for 100 (`{:field :quantity :instructed "100" :confirmed "90"}`, `:settled? false`); the MT548 status is matched and pending.
- Holdings: one match (the holder wrote `100.00`, the statement `100`), one quantity difference (short 2.5 against short 3), one ISIN only in the statement, one only in the positions.

No model was called (no-agent scripts). No network request was made by the
scripts; kbb fetched the pinned kotoba-lang/kotoba revision into `~/.gitlibs`.

## Not done

- Registration in the root agent registry and installation on a host (same as awai-banking-ops; JUN-302).
- Reason codes, partial settlement, corporate actions, prices and anything outside the modeled subset of AWAI-3154.
- MT is checked only against the modeled skeleton, not the SWIFT Standards MT (ADR 0042 O3); L2 and L3 wait on O3 and O5; other people's accounts on O1.
