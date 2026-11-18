# awai-public-funds profile — evidence of 2026-11-18

Tracking: [AWAI-3294](https://linear.app/kotobalabs/issue/AWAI-3294), under
[AWAI-3291](https://linear.app/kotobalabs/issue/AWAI-3291). Scope: network-awai-apex
ADR 0046 (D1–D6). Placement: root `adr-2610222100-hermes-itonami-separation`
(`itonami/profiles/<p>/`). Same layout as `awai-securities-ops`
([evidence](awai-securities-ops-profile-20261118.md)).

## What was added

| Path | Role |
|---|---|
| `itonami/profiles/awai-public-funds/` | `itonami.edn`, `yakuwari.edn`, `SOUL.md`, `profile.yaml`, `config.yaml`, `cron/jobs.json` |
| `…/scripts/awai_public_funds/analyze.cljk` | Pure: `:budget-vs-outturn` (digest, layout and fiscal-year checks; aggregation by 所管 × COFOG division; the outturn's two arithmetic identities per row; initial budget vs outturn per ministry; ministry and purpose-code coverage). Anything else is refused by name |
| `…/scripts/public_funds_io.cljk` | Reads the data files and a request's source files with their sha256 (node:crypto). No network |
| `…/scripts/public_funds_self_test.cljk` | Fixture test (reads only; exit 1 on any difference) |
| `…/scripts/public_funds_inbox.cljk` | Handles requests in `workspace/inbox` (sources in `workspace/inbox/sources`) into `workspace/drafts` |
| `…/data/cofog-map.edn` | 目的別分類 code → COFOG division, every entry `:assumed` with confidence and reason; reserves, disaster and unspecified codes unmapped |
| `…/data/ministries.edn` | 所管 → open-jpn-gov roster DID, exact name matches only |
| `…/fixtures/` | 4 requests (1 analysis, 3 refusals), the two source CSVs unmodified, expected results |

## Sources

Both files are the unmodified 歳出 members of Ministry of Finance CSV archives,
published under 公共データ利用規約（第1.0版）(PDL1.0; the site's terms at
<https://www.bb.mof.go.jp/terms/> require the source to be credited and any
processing to be stated; checked 2026-11-18). Source: 予算書・決算書データベース.
The aggregated results are processed from these files by this profile.

| Role | Archive (fetched 2026-11-18T00:08Z) | Archive sha256 | Member | Member sha256 |
|---|---|---|---|---|
| Initial budget | `https://www.bb.mof.go.jp/server/2024/csv/DL202411001.zip` (令和6年度 一般会計 歳入予算明細書及び歳出予定経費要求書(科目別内訳)) | `234bd440…5510e06f` | `DL202411001b.csv`, 6,718 rows, thousand yen | `abc34265…4774e931` |
| Outturn | `https://www.bb.mof.go.jp/server/2024/csv/DL202477001.zip` (令和6年度 一般会計 歳入決算明細書及び歳出決算報告書(科目別内訳)) | `5536fd4f…d1e187e4` | `DL202477001b.csv`, 6,962 rows, yen | `3e193253…a444e959` |
| Code names | `https://www.bb.mof.go.jp/server/2024/dlpdf/DL202411001.pdf` pp. 1136–1137 (目的別分類) | `d5031c98…725a1883` | — | — |

Full digests are in the fixture requests and `data/cofog-map.edn`. The
thousand-yen unit of the initial budget is stated in the same PDF (設例,
「要求額(千円)」) and agrees with the outturn: 内廷費 is 324,000 in one and
324,000,000 in the other.

## Authority

`read_source`, `review_source`, `draft_artifact` and `record_local_receipt` are
`:autonomous` (own workspace, fixtures and data only); `publish`,
`external_messaging` and every other effect are `:blocked`;
`:default-decision :blocked`; placement `:local`; both cron jobs paused, run on
demand. The capability vocabulary used across this repo has no fetch
capability, so none was invented (ADR 0046 D6): fetching is excluded by the
`read_source` scope and by the disabled `web` toolset, and the user downloads
sources (O1).

## Runs

Environment: macOS, Node v26.0.0, host load average 170–212 during the runs.
`kbb` hung at start-up on this host (even `(println :hello)` did not return in
60 s; the launcher's stdlib and dependency resolution reads the kotoba-lang
checkout, which sits on a JuiceFS mount that returned I/O errors the same
morning). The kbb engine itself was healthy, so every command below ran on it
directly: `node kotoba-lang/org-babashka-nbb/cli.js` (= `$E`), with the
profile's `scripts/` as classpath where needed. `ITONAMI_DIR=<repo>/itonami`.

| Check | Command | Result |
|---|---|---|
| Fixture test | `cd scripts && node $E --classpath . public_funds_self_test.cljk` | `4 fixtures, 0 failed`: 1 analyzed, 3 refused (fiscal-year mismatch, digest mismatch, `:rank-ministries`) |
| Negative control | the same on a copy whose expected identity result was changed to `:differs` | `4 fixtures, 1 failed`, exit 1 |
| Manifest | `node $E bin/itonami-agent-kbb profile refresh-bundle awai-public-funds`, then `profile check --all` | bundle `bafkreiaon6d2zcs4rkqvaawtautnonn7wgj576fbqaosiyatocitp2y67e` (22 files); 3 profiles, 0 errors, 0 warnings |
| Fixture test through the CLI | `node $E bin/itonami-agent-kbb cron run a7c1e0f3b294 -p awai-public-funds` | succeeded; `4 fixtures, 0 failed` |
| Inbox through the CLI | `node $E bin/itonami-agent-kbb cron run d4e8b2c61f05 -p awai-public-funds` with two requests and the two CSVs in `workspace/inbox` | `analyzed jp-fy2024-general 66 rows`, `refused rank-ministries-refused unsupported-kind`; the result is byte-identical to the fixture |
| Independent recomputation | a Python 3 script (developer oracle, not a gate) summing the same CSV columns | every total, both identity counts and the per-ministry sums equal the analysis |

What the FY2024 result shows (all `:derived` from the two files):

| | yen |
|---|---|
| Initial budget (当初) | 112,571,688,422,000 |
| Outturn budget (歳出予算額, after the supplementary budget) | 126,514,973,726,000 |
| Current budget (歳出予算現額) | 137,578,249,935,601 |
| Spent (支出済歳出額) | 123,023,998,629,580 |
| Carried to next year (翌年度繰越額) | 10,243,266,020,197 |
| Unused (不用額) | 4,310,985,285,824 |
| Spent under unmapped purpose codes | 7,997,262,328,178 |

- Both outturn identities hold on all 6,962 rows: 歳出予算現額 = 歳出予算額 + 前年度繰越額 + 予備費使用額 + 流用等増△減額 + 予算決定後移替増△減額, and 歳出予算現額 = 支出済歳出額 + 翌年度繰越額 + 不用額.
- 18 所管 appear in both files and none in only one. 内閣, 国会, 皇室費 and 裁判所 have no open-jpn-gov DID.
- Unmapped codes: 051, 053, 059, 074, 088, 089, 106, 107, 110, 191, 199. No unknown code.
- Initial budget and outturn differ for all 18 所管; the record gives the amounts and no cause.

No model was called (no-agent scripts). The scripts made no network request.
The two archives and the PDF were downloaded once by the developer, as a user
would.

## Corrections to ADR 0046

- D6 asked AWAI-3294 to check whether a fetch capability exists in the
  vocabulary; it does not, so the profile relies on the `read_source` scope.
- The ADR's 結果 says AWAI-3294 starts by completing open-cofog (10/96
  classes). D5 aggregates by COFOG division only, which the profile defines in
  `data/cofog-map.edn`, so the class gap does not block this step; it blocks
  group- and class-level work later.

## Not done

- Registration in the root agent registry and installation on a host (same as awai-banking-ops; JUN-302).
- A run through the `kbb` launcher on a healthy host, and through the native `bin/itonami-agent` (cron routes are not ported).
- Special accounts, local government, other countries (ADR 0046 O3); fetching (O1); publication (O2).
- COFOG group and class level; a review of `data/cofog-map.edn` by someone versed in GFS/COFOG.
