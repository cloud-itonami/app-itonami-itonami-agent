# awai-web-research profile — evidence of 2026-11-18

Tracking: [AWAI-3164](https://linear.app/kotobalabs/issue/AWAI-3164), under
[AWAI-3161](https://linear.app/kotobalabs/issue/AWAI-3161). Scope: network-awai-apex
ADR 0043 D5 (bot profile), D7 (on-device index) and D8 (persistence in Yataverse and
the Awai file format; owner instruction 2026-11-18, network-awai-apex#279). Placement:
root `adr-2610222100-hermes-itonami-separation` (`itonami/profiles/<p>/`), as
`awai-banking-ops` and `awai-securities-ops`.

## What was added

| Path | Role |
|---|---|
| `itonami/profiles/awai-web-research/` | `itonami.edn`, `yakuwari.edn`, `SOUL.md`, `profile.yaml`, `config.yaml`, `cron/jobs.json` (3 no-agent jobs, paused, run on demand) |
| `…/scripts/awai_web_research/index.cljk` | Pure: Kumade snapshot exports → `awai.web-index-draft/1` bundle and `awai.table/1` rows (index columns only); noindex excluded, private hosts and cross-origin redirects rejected by name, duplicates by identical normalized text |
| `…/scripts/web_index_inbox.cljk` | `workspace/inbox/*.json` → `workspace/drafts/<batch>.edn` and `workspace/awai/<batch>.awai` (CARv2, DAG-CBOR) |
| `…/scripts/web_index_persist.cljk` | `yataverse car-put` of a `.awai` only with `workspace/approvals/<batch>.edn` naming the operation, that root CID and the configured destination; independent `car-get` readback with a hash check of every block; receipt; the approval is used up. Optional AwaiFS volume copy through `AWAI_VOL_DIR`, fail-closed |
| `…/scripts/web_index_self_test.cljk` | Fixture test (reads only; exit 1 on any difference) |
| `…/scripts/deps.edn` | `network-awai/awai-format` `awai.fs.document` at `ba225f2567479db1146974dcdf20da79be9b05f1` (private; cloned over ssh by kbb) |
| `…/fixtures/` | 4 illustrative pages, their Kumade exports (Kumade 443bab0 `createSnapshot`), a tampered private-host export, expected bundle and root CID |

## Authority

`read_source`, `review_source`, `draft_artifact`, `record_local_receipt` are
`:autonomous` (supplied snapshots and own workspace only). `storage_upload` is
`:approval-required`, limited to `yataverse car-put` of its own index `.awai`, one
write per approval (D8). `publish`, `external_messaging`, `delete_data`, signing,
spending and every other effect are `:blocked`; `:default-decision :blocked`. The bot
never fetches from the network (O2 open). Only index columns are persisted: snapshot
id, requested and final URL, capture time, title, a summary of at most 280 code points,
tags and language. Page text, links and HTML are not.

## Runs

Environment: macOS, Node v26.0.0, `bin/itonami-agent-kbb` (the native
`bin/itonami-agent` blocks `cron` routes until the selfhost port), `ITONAMI_DIR=<repo>/itonami`,
host load 170–350 (one CLI run took about 10 minutes).

### 1. Search and bounded crawl (Kumade, approved; before the bot)

`capture-harness.mjs` (sha256 `887850d2…2c577a`) imports Kumade 443bab0
`src/commoncrawl.js` and `src/crawl.js` and gives them a broker that performs a
request only for the operations named on the command line, and logs each request.
Collection `CC-MAIN-2026-43`.

| Run | Approval | Result | Requests |
|---|---|---|---|
| Negative control | none | search: `commoncrawl-index/lookup-captures not approved`; crawl status `denied` | 0 ([requests-denied.json](requests-denied.json)) |
| Search | index, data | `https://example.com/`: 3 captures; the first fetched (WARC block digest verified) → 1 snapshot | index GET 200, data GET 206 ([requests-search.json](requests-search.json)) |
| Bounded crawl | index, data | seed `http://info.cern.ch/`, limits depth 1 / 3 pages / 2 MiB → status `complete`, 2 pages (depth 0 and 1), 0 skipped, 0 failed | 2 index GET 200; data GET 206, and 503, 503, 206 for the second record ([requests-crawl.json](requests-crawl.json)) |

Earlier the same morning the Common Crawl index answered 504 for every collection
(12 attempts in the harness, and 504 from `CC-MAIN-2026-43`, `-2025-51`, `-2024-51` by
curl; `collinfo.json` 200) until 09:54 JST. A first crawl with the seed
`https://info.cern.ch/` skipped its only page as `cross-origin-redirect`, because the
archived URL is `http://`; the recorded crawl uses the `http://` seed.

### 2. The bot, through the CLI

| Check | Command | Result |
|---|---|---|
| Manifest | `itonami-agent-kbb profile refresh-bundle awai-web-research`, then `profile check` | bundle `bafkreicaskm4zfsrfybuomcidxyr42sxnyamke3ac7y2yptldhvx7r5qbe`, 22 files; 0 errors, 0 warnings |
| Fixture test | `itonami-agent-kbb cron run 0663c27c007a -p awai-web-research` | 10 checks ok, `5 snapshot fixtures, 0 failed`; fixture root `bafyreibsavky255i6equqapebowotdiof5v45l4s62nfkgd2v7hvxtsfla` |
| Fixture test, negative control | `kbb web_index_self_test.cljk` on a copy whose expected bundle says `:lang "en"` for the Japanese page | `FAIL bundle equals fixtures/expected/bundle.edn`, `1 failed`, exit 1 |
| Inbox, fixtures | `itonami-agent-kbb cron run 7c2102875095 -p awai-web-research` with the 5 fixture exports | `excluded … noindex`, `excluded sha256:000… private-host`, `indexed 21487667ebbda321 3 row(s)` with the same root as `fixtures/expected/root.txt` |
| Inbox, real snapshots | same, with the 3 snapshots from step 1 | `indexed 396c90452d4396aa 3 row(s) root bafyreiheeuujthef7vidcdssi4iej7efl2fiqyiqlljntolligygs3ydl4` ([index-draft-396c90452d4396aa.edn](index-draft-396c90452d4396aa.edn)) |
| Persist, no approval | `itonami-agent-kbb cron run b2ac661ea5ea -p awai-web-research`, `YATAVERSE_CLI` = a stub that logs every call | `not approved … (no workspace/approvals/396c90452d4396aa.edn; nothing sent)`; stub log absent (0 calls) |
| Persist, approval names another root | same, approval for `bafyreie2yy…` | `not approved … approval does not name this operation and root …; nothing sent`; 0 stub calls |
| Persist, approved | same, `YATAVERSE_CLI` = `network-awai/app-itonami-yataverse` b0219ba `bin/yataverse.mjs` (`status`: `https://yataverse.com`, write configured) | `persisted 396c90452d4396aa root bafyreihee… to https://yataverse.com`; approval renamed to `.used` ([persist-receipt-396c90452d4396aa.edn](persist-receipt-396c90452d4396aa.edn)) |

The two negative persist runs used the first real root (`bafyreic6kx…`). The summary
rule was then changed (a page with only `dl`/`dt` had an empty summary, so it now
falls back to the whole text), the bundle refreshed, the fixture test rerun (still 0
failed: no fixture uses the fallback) and the inbox rerun, which gave the root that
was approved and written.

What the approved write shows:

- `car-put`: 8 blocks, `verified: true` (the CLI exported the root back and compared), and a Yataverse storage receipt signed with Ed25519, `receiptVerified: true` (receipt CID `bafyreidz3e5uaim4opjbjej7cbrioz26em34ksfqdnngcwr35vahdkbbva`).
- The job's own readback (`car-get --scope all`): same root, 8 of 8 local blocks present, every block's SHA-256 equals its CID.
- An unauthenticated `curl https://yataverse.com/ipfs/<root>?format=raw` returned 200 with 110 bytes whose SHA-256 equals the root CID's digest: anyone with the CID can read the index, as D8 assumes.
- The receipt says `retentionUntil: 0` and has no reservation or lease: it records acceptance, not durable custody.
- The approval's `:approval/at` reads `01:50Z`; the write happened at `01:48:23Z`. The operator wrote the time ahead by mistake. The approval was written by this session on the owner's chat instruction, not by the bot.

No model was called (no-agent scripts). The scripts made no network request of their
own; the persist job's only network effect was the yataverse CLI it ran after the
approval check. kbb cloned `network-awai/awai-format` at the pinned sha into `~/.gitlibs`.
kbb also printed that four unrelated `io.github.kotoba-lang/*` libraries could not be
cloned; the jobs do not use them.

The legacy `run-tests.cljk` suite was not run: this change touches no `src/` or `test/` file.

## Not done or not shown

- The native profile host, registration in the root agent registry and installation on a host (as for the banking and securities profiles).
- A model turn: all three jobs are no-agent scripts. Summaries are the page's own text, not a model's reading; `lang` is `ja` only when kana are present, otherwise `und`.
- The AwaiFS volume: `/Volumes/AwaiVol` (WebDAV on localhost:8586) did not answer `ls` (operation timed out), so `AWAI_VOL_DIR` was not set. The job refuses an unanswering directory instead of writing elsewhere.
- The Common Crawl search and crawl ran in Node through Kumade's own modules with an explicit approval list (`.agent-output` harness), not through the Kumade UI; the in-app consent flow on Android was shown in AWAI-3165. Crawl snapshots carry the time of capture into Kumade (`runCrawl` uses `now()`), not the archive time; search snapshots carry the archive time.
- Reading the index back in Kumade (D7: loading a bundle into the on-device index) is AWAI-3166.
- The Hermes crawl profiles in `network-awai/app-hyakka` are unchanged (ADR 0043 D6, O3).
