# awai-hiring-ops profile — evidence of 2026-11-18

Tracking: [AWAI-3173](https://linear.app/kotobalabs/issue/AWAI-3173), under
[AWAI-3168](https://linear.app/kotobalabs/issue/AWAI-3168). Scope:
network-awai-apex ADR 0043 S1 and D4 (the hiring-side profile). Profile
placement: root ADR `adr-2610222100-hermes-itonami-separation`
(`itonami/profiles/<p>/`). Same shape as `awai-job-seeker-ops` (AWAI-3172).

## Scope note

"Publish" here means recruit's `:mst/put-record` record written to a
host-local posting index, and "search" means a query over that index. Nothing
is posted to LinkedIn, Indeed or any job board (ADR 0043 D2, D3). Only the
user's own company's postings (S1); publishing for other employers is S3 and
waits on owner decision O1.

## What was added

| Path | Role |
|---|---|
| `itonami/profiles/awai-hiring-ops/` | The itonami agent definition: `itonami.edn`, `yakuwari.edn`, `SOUL.md`, `profile.yaml`, `config.yaml`, `cron/jobs.json` |
| `…/scripts/awai_hiring/posting.cljk` | `:publish` → S1 checks + isic-6399 content checks + recruit `cell-plan :jobposting`; `:search` over an index |
| `…/scripts/awai_hiring/recruit.cljk` | Finds recruit's pinned `src/recruit/murakumo.kotoba` on the classpath, refuses it unless its SHA-256 is `0450dbbfe6a57815d937bbefffd926245ccbf54971ec4f0e3b37de9e02b255bd`, and evaluates it unchanged |
| `…/scripts/hiring_self_test.cljk` | Fixture test (reads only; exit 1 on any difference) |
| `…/scripts/hiring_inbox.cljk` | Handles `workspace/inbox`: publishes ready records to `workspace/index`, writes plans, announcements, blocked records and search results to `workspace/drafts` (host-local) |
| `…/scripts/deps.edn` | cloud-itonami/recruit `cacff0c`, kotoba-lang/kotoba `lib/kotoba.text` `4bf68e1` (recruit's own pin) and `lib/kotoba.bytes` `2e3b9ef` |
| `…/fixtures/` | 13 fictional requests: 2 publications, 2 searches, 8 negative controls that each fail exactly one check or gate, 1 refusal by kind |

## Checks and gates

| Source | Check / gate | Blocks |
|---|---|---|
| S1 | `:hiring/own-company` | a posting whose employer DID is not the requester's organisation DID |
| S1 | `:hiring/fixture-or-own-data` | data whose origin is not `:fixture` or `:user-supplied` |
| S1 | `:hiring/local-index-only` | any target other than `:local-index` (LinkedIn, Indeed, a job board) |
| isic-6399 | `:isic-6399/stale-vacancy` | a closed vacancy (or one not stated open) |
| isic-6399 | `:isic-6399/ad-content-discriminatory` | a protected-characteristic requirement (age, gender, nationality, …), any requirement outside the reviewed set, or a posting marked discriminatory |
| isic-6399 | `:isic-6399/displayed-compensation-mismatch` | displayed monthly pay ≠ hourly wage × monthly hours (integer minor units) |
| isic-6399 | `:isic-6399/source-consent-unverified` | a posting that requires source consent without verified consent |
| recruit | the seven `common-gates` | any gate not attested: recruit's own `cell-plan` returns `:blocked`, no effect |

The isic-6399 checks are re-implemented from
`network-awai/app-itonami-cloud-itonami-isic-6399` `6f076c1`
`src/jobsearchops/governor.cljk`: that repository's `deps.edn` has
`:local/root` entries that resolve only inside the monorepo, so it cannot be
a git dependency here. recruit is used as is.

## Code and documentation differences found

- cloud-itonami/recruit README and `docs/operator-quickstart.md` say
  `kbb --classpath src` loads `recruit.murakumo`. With the current kbb engine
  (`kotoba-lang/org-babashka-nbb` `1675e6a`) it does not: the engine resolves
  `.cljk`/`.cljs`/`.cljc` only, and the source is `.kotoba`
  (`Could not find namespace: recruit.murakumo`). The loader above is the
  workaround; it evaluates the pinned bytes and fails closed on any other.
- kbb's deps door did not put recruit's transitive dependency
  (`kotoba.lang.text`) on the classpath, and a `require` inside
  `load-string` cannot load a namespace that is not yet loaded on nbb. The
  profile pins `lib/kotoba.text` at recruit's own sha and loads it first.

## Authority

`yakuwari.edn`, copied verbatim into `itonami.edn :capabilities`:
`read_source`, `review_source`, `draft_artifact`, `record_local_receipt` are
`:autonomous` (own workspace, local index and fixtures only).
`external_messaging`, `publish` (external), `spend`, `transfer_funds`,
`use_signing_identity`, `export_private_keys`, `storage_upload`,
`delete_data`, `delegate_task`, `manage_services`, `git_push`, `git_merge`,
`enable_cron` are `:blocked`. `:authority :default-decision :blocked`.
Placement `:local` only. Both cron jobs are `no_agent`, `enabled false`, and
run only on demand.

## Runs

Environment: macOS, Node v26.0.0, kbb engine `kotoba-lang/org-babashka-nbb`
`1675e6a`, itonami-agent base `d7ef6b2`, `ITONAMI_DIR=<repo>/itonami`.
Commands use `bin/itonami-agent-kbb`; the native `bin/itonami-agent` blocks
the `cron` routes (`blocked pending Kotoba/Amu selfhost port`, AWAI-131).

| Check | Command | Result |
|---|---|---|
| Profile manifest | `itonami-agent-kbb profile refresh-bundle awai-hiring-ops`, then `profile check awai-hiring-ops` | bundle `bafkreiemkgqt4p6fymecwzz42pwvaubbyl76kgtchl7mcqqafhxjsrfyci`; `1 profile(s): 0 with errors, 0 with warnings` |
| Fixture test through the CLI | `itonami-agent-kbb cron run f5fb753f08ff -p awai-hiring-ops` | `13 fixtures, 0 failed`: 2 published with `recruit=ready`; 2 searches (`hits=["posting-001"]`, `hits=[]`); each of the 8 negative controls blocked by exactly its own check or gate, including `missing-gates=[:did-primary-baseline]` from recruit's own `cell-plan`; 1 refused by kind |
| Publish and search through the CLI | `itonami-agent-kbb cron run c1a3ee8ea001 -p awai-hiring-ops` with two publications, one recruit-blocked publication and two searches in `workspace/inbox` | `published publish-backend-engineer index/posting-001.edn recruit=ready`, `published publish-warehouse-lead index/posting-002.edn`, `blocked blocked-recruit-did-primary missing-gates=[:did-primary-baseline]` (nothing written to the index), `searched search-clojure-tokyo hits=["posting-001"]`, `searched search-no-hit hits=[]`; the plan, announcement, blocked record and both search results are byte-identical (`cmp`) to the fixtures |
| Negative control of the test itself | the fixture test on a copy whose expected search result names `posting-999` | `13 fixtures, 1 failed`, exit 1 |
| recruit digest pin fails closed | the fixture test on a copy whose pinned recruit SHA-256 differs by one hex digit | exit 1, `recruit source digest does not match the pin; refusing to evaluate it` |

No model was called: the jobs are `no_agent` scripts. No network request was
made by the scripts; kbb fetched the pinned recruit and kotoba-lang/kotoba
revisions into `~/.gitlibs`. All data is fictional.

## Not done

- The app (AWAI-3175) and LinkedIn self-serve operations (AWAI-3174).
  External publication of any kind stays blocked.
- Publishing or collecting other employers' postings (S3) and candidate
  matching (S2): owner decision O1.
- Verification of recruit attestations: they are the operator's statements.
- Registration in `com-junkawasaki/root manifest/agent-registry` and
  installation into a host's `~/.itonami`. The profile is a reviewed
  definition, not a running bot.
- A live turn on the Murakumo rail (AWAI-131).
- Running the profile test in CI (no kbb job for profile scripts).
- Fixing recruit's README/quickstart or making kbb resolve `.kotoba`
  namespaces: reported, not changed here.
