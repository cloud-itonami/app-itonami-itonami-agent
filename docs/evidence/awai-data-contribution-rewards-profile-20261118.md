# awai-data-contribution-rewards profile — evidence of 2026-11-18

Tracking: [AWAI-3208](https://linear.app/kotobalabs/issue/AWAI-3208), under
[AWAI-3206](https://linear.app/kotobalabs/issue/AWAI-3206). Contract:
network-awai/cloud-murakumo-api ADR-261118 (AWAI-3210, PR #375) and ADR-261016.
Profile placement: root ADR `adr-2610222100-hermes-itonami-separation`
(itonami agents live under `itonami/profiles/<p>/`).

## What was added

| Path | Role |
|---|---|
| `itonami/profiles/awai-data-contribution-rewards/` | The itonami agent definition: `itonami.edn`, `yakuwari.edn`, `SOUL.md`, `profile.yaml`, `config.yaml`, `cron/jobs.json` |
| `…/scripts/awai_contrib/contract.cljk` | Loads the reward contract from a local cloud-murakumo-api clone with `git show <sha>:<path>` at the pinned merge commit, checks each file's sha256, then evaluates exactly the checked text |
| `…/scripts/awai_contrib/flow.cljk` | Pure pipeline: intake → `record-decision` → `training-use-decision` → `reward-reviews` → `awai.training-reward-request/1` → `allocate-pool` → `awai.training-reward-result/1`, and the read-back check |
| `…/scripts/contribution_inbox.cljk` | Runs `workspace/inbox` requests; writes every stage to the host-local workspace, reads each file back, records sha256 in a receipt |
| `…/scripts/contribution_self_test.cljk` | Fixture test (reads only; exit 1 on any difference) |
| `…/fixtures/` | 4 requests (1 accepted, 3 refused) and their expected outcomes |

The pinned contract:

| File at `62715161e9b2a42a311df0551c0ca6807519240b` | sha256 |
|---|---|
| `src/local_murakumo/training_economy.cljk` | `b1a3dd5077ab5eec85ce0c60256d25bb76efa8325ae9ea084803e189db02d1f2` |
| `src/local_murakumo/training_contribution_rights.cljk` | `e9141f7b70ffc2f443620449652d923ce4a50cb3c5db87b4768a732c694164ba` |

The clone is `AWAI_MURAKUMO_API_DIR` (profile `.env`, host-local) or
`~/github/network-awai/cloud-murakumo-api`. Reading a git object means a dirty
working tree or another checked-out branch does not change what runs. A missing
clone, missing commit or digest difference stops the job; nothing falls back.

## Why the calculation is local

cloud-murakumo-api has no training-reward route in production
(`/training/policy` returns 404, AWAI-307), and payout is disabled under
ADR-261016. "Request the test-stage calculation" therefore means: the bot writes
an `awai.training-reward-request/1` naming the calculator (repo, sha,
`local-murakumo.training-economy/allocate-pool`, `:stage :test`) and runs that
pinned function on this host. Every request and result carries `:payout? false`
and the open points. When the API gains a route, the request file is the shape
to send; sending it is an external operation that needs its own approval.

## Authority

`yakuwari.edn`, copied verbatim into `itonami.edn :capabilities`:
`read_source`, `review_source`, `draft_artifact`, `record_local_receipt` are
`:autonomous` (own workspace, fixtures and the pinned contract commit only).
`transfer_funds`, `spend`, `external_messaging`, `use_signing_identity`,
`export_private_keys`, `publish`, `storage_upload`, `delete_data`,
`delegate_task`, `manage_services`, `git_push`, `git_merge`, `enable_cron` are
`:blocked`. `:authority :default-decision :blocked`. Placement `:local` only,
inference rail Murakumo with `openrouter.ai` denied. Both cron jobs are paused
(`enabled false`) and run only on demand. No model is called: both jobs are
`no_agent` scripts.

The bot never sets a `-verified?` or `-reviewed?` marker; in the fixtures they
are fictional test values standing in for server-resolved records.

## Runs

Environment: macOS (load average 70–310 during the runs), Node v26.0.0, kbb
engine `kotoba-lang/org-babashka-nbb` `1675e6a`, itonami-agent base `dbe0223`,
`ITONAMI_DIR=<repo>/itonami`, `AWAI_MURAKUMO_API_DIR` = a clone of
cloud-murakumo-api at `6271516`. Commands use `bin/itonami-agent-kbb`; the
native `bin/itonami-agent` blocks the `cron` routes (pending the Kotoba/Amu
selfhost port, AWAI-60 / AWAI-131).

| Check | Command | Result |
|---|---|---|
| Fixture test through the CLI | `itonami-agent-kbb cron run c3a1f0e2b7d4 -p awai-data-contribution-rewards` | `4 fixtures, 0 failed; contract network-awai/cloud-murakumo-api@6271516` (1 requested, 3 refused as expected) |
| One test contribution through the CLI | `itonami-agent-kbb cron run 9e6b2d84f1a7 -p awai-data-contribution-rewards` with `contrib-image-own-work` and `contrib-text-no-rights-basis` in `workspace/inbox` | `requested contrib-image-own-work  epoch test-epoch-1  allocations [["test-human-1" "750000"] ["test-human-2" "250000"]]  unallocated 0  result sha256=96d1ac30…`; `refused contrib-text-no-rights-basis  rights unsupported-rights-basis` |
| Read back | the inbox job's receipt `receipts/contrib-image-own-work.receipt.edn` | 4 stage files written and read back with matching bytes and values; `:read-back {:ok? true}`, `:payout? false` |
| Negative control: no rights basis | the same inbox run | a refusal file only; no request, result or receipt for that id |
| Negative control: altered expectation | the fixture test on a copy whose expected allocation was `"750001"` | `4 fixtures, 1 failed`, exit 1 |
| Negative control: altered contract pin | the fixture test on a copy whose pinned `training_economy.cljk` digest ends in `…1f3` | `contract digest mismatch: src/local_murakumo/training_economy.cljk …`, exit 1, nothing evaluated |
| Profile manifest | `itonami-agent-kbb profile check awai-data-contribution-rewards` after `profile refresh-bundle` | `1 profile(s): 0 with errors, 0 with warnings`; bundle `bafkreieyjavmbtdj6l6mrtupxczem4z45o4ocxuxlyrmmp2n4dyz3g6dwe`, 17 files |

Stage file digests of the run (sha256): `records/…rights.edn`
`92f48231313aa97792c6684c5cc52b5c162c7a308536cd77d050a2cc33859ab6`,
`records/…use.edn`
`4ebac7fdded0a8e079308d7310b3f0a00679fe1376a501e8b577e7e0849c2460`,
`requests/…request.edn`
`8a1f941e095bcbf18ae16474e9f6a05d70d31174988ecd2135d7dea39916bfc5`,
`results/…result.edn`
`96d1ac303a3f8341210876328f9f7ab1afcd2d4cf1245fec23a4e7e8ae73717f`.

The first inbox run failed on an unbalanced parenthesis in
`contribution_inbox.cljk` (`EOF while reading`); the table shows the run after
the fix. No model was called and the scripts made no network request.
The itonami-agent suite (`run-tests.cljk`) was not run: no file under `src/` or
`test/` changed, and the host was under load average 70–310.

## Open points

- `kotoba.lang.text`, the contract's one library, comes from kbb's implicit
  stdlib (sibling `kotoba-lang/text` at `6c6cd30`, `text.cljk` sha256
  `a388bba5…`), not from a pin. A `scripts/deps.edn` pinning it at
  `febd87e4` made the deps door unable to resolve it in this environment, and
  `load-string` on this engine cannot load a namespace from the classpath
  itself, so the stdlib copy is loaded first. The contract tests in
  cloud-murakumo-api use `febd87e4`.
- Not registered in `com-junkawasaki/root manifest/agent-registry` and not
  installed into a host's `~/.itonami`. The profile is a reviewed definition,
  not a running bot.
- No HTTP route, no persistence of the duplicate index outside the host-local
  workspace, no payout. The legal questions in ADR-261118 are open.
- The participant app (AWAI-3209) and the store / device install (AWAI-3211)
  are separate issues.
- This repository's CI runs native gates and an oracle job; a kbb job for
  profile scripts is not added.
