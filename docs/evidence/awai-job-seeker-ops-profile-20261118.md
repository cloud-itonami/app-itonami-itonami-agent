# awai-job-seeker-ops profile — evidence of 2026-11-18

Tracking: [AWAI-3172](https://linear.app/kotobalabs/issue/AWAI-3172), under
[AWAI-3168](https://linear.app/kotobalabs/issue/AWAI-3168). Scope:
network-awai-apex ADR 0043 S1 and D4 (the job-seeker profile). Profile
placement: root ADR `adr-2610222100-hermes-itonami-separation`
(`itonami/profiles/<p>/`). Same shape as `awai-banking-ops` (AWAI-3155).

## Scope note

The AWAI-3172 issue text describes candidate intake, matching and
introduction with the isic-7810 actor. That is placement of other people's
candidates (ADR 0043 S2), which waits on owner decision O1. ADR 0043 D4
assigns AWAI-3172 to the S1 job-seeker profile instead: the user's own job
search only. This profile follows the ADR. ADR 0043 D4 still names
`hermes/profiles/`; the code moved itonami profiles to `itonami/profiles/`
(AWAI-3319), and this profile follows the code.

## What was added

| Path | Role |
|---|---|
| `itonami/profiles/awai-job-seeker-ops/` | The itonami agent definition: `itonami.edn`, `yakuwari.edn`, `SOUL.md`, `profile.yaml`, `config.yaml`, `cron/jobs.json` |
| `…/scripts/awai_job_seeker/draft.cljk` | Pure: request → intake → match → draft → status, held to the governor gates; returns an `awai.hiring-draft/1` bundle and the letter, a blocked record naming each failed gate, or a refusal |
| `…/scripts/job_draft_self_test.cljk` | Fixture test (reads only; exit 1 on any difference) |
| `…/scripts/job_draft_inbox.cljk` | Drafts the seeker's requests from `workspace/inbox` into `workspace/drafts` (host-local) |
| `…/scripts/deps.edn` | kotoba-lang/kotoba `lib/kotoba.bytes` (SHA-256), pinned at `2e3b9ef25f06ef98924daa450c36da1b7c712189` |
| `…/fixtures/` | 8 fictional requests: 1 that passes every gate, 6 negative controls that each fail exactly one gate, 1 refusal by kind |

## Governor gates

All six are evaluated on every request, and all are hard (no override).

| Step | Gate | Blocks |
|---|---|---|
| intake | `:intake/self-only` | acting for another job seeker (S2, O1) |
| intake | `:intake/fixture-or-own-data` | data whose origin is not `:fixture` or `:user-supplied` |
| intake | `:intake/posting-not-scraped` | a posting not copied or typed in by the seeker (ADR 0043 D2) |
| match | `:match/no-protected-criteria` | a protected characteristic (age, gender, nationality, religion, disability, …) or any criterion outside the reviewed set |
| draft | `:draft/claims-from-own-record` | a letter citing anything not in the seeker's own record |
| status | `:status/draft-only` | any action other than `:draft` (submit, send, post) |

The gates are the S1 boundary. They do not check the employer, the posting's
truth or any jurisdiction's law; every draft says so in `:open-points`.

## Authority

`yakuwari.edn`, copied verbatim into `itonami.edn :capabilities`:
`read_source`, `review_source`, `draft_artifact`, `record_local_receipt` are
`:autonomous` (own workspace and fixtures only). `external_messaging`,
`publish`, `spend`, `transfer_funds`, `use_signing_identity`,
`export_private_keys`, `storage_upload`, `delete_data`, `delegate_task`,
`manage_services`, `git_push`, `git_merge`, `enable_cron` are `:blocked`.
`:authority :default-decision :blocked`. Placement `:local` only. Both cron
jobs are `no_agent`, `enabled false`, and run only on demand.

## Runs

Environment: macOS, Node v26.0.0, kbb engine `kotoba-lang/org-babashka-nbb`
`1675e6a`, itonami-agent base `f791f68` (manifest re-checked after merging main `2fa92b6`: `0 with errors, 0 with warnings`), `ITONAMI_DIR=<repo>/itonami`.
Commands use `bin/itonami-agent-kbb`; the native `bin/itonami-agent` blocks
the `cron` routes (`blocked pending Kotoba/Amu selfhost port`, AWAI-131).

| Check | Command | Result |
|---|---|---|
| Profile manifest | `itonami-agent-kbb profile refresh-bundle awai-job-seeker-ops`, then `profile check awai-job-seeker-ops` | bundle `bafkreib7d6a5c5palpdhixbvtnlkre2c6behn2cujy4abbjpwr4aacwcwu`; `1 profile(s): 0 with errors, 0 with warnings` |
| Fixture test through the CLI | `itonami-agent-kbb cron run 461ec639d648 -p awai-job-seeker-ops` | `8 fixtures, 0 failed`: 1 drafted with all six gates `:pass`; each of the 6 negative controls blocked with exactly its own gate named (`failed-gates=[:intake/self-only]`, `[:intake/fixture-or-own-data]`, `[:intake/posting-not-scraped]`, `[:match/no-protected-criteria]`, `[:draft/claims-from-own-record]`, `[:status/draft-only]`); 1 refused by kind |
| Inbox drafting through the CLI | `itonami-agent-kbb cron run 4930e196f221 -p awai-job-seeker-ops` with `apply-backend-engineer` and `blocked-auto-apply` in `workspace/inbox` | `drafted apply-backend-engineer sha256=784218ab…c3a8`, `blocked blocked-auto-apply failed-gates=[:status/draft-only]`; the draft, the letter and the blocked record are byte-identical (`cmp`) to the fixtures |
| Negative control of the test itself | the fixture test on a copy whose expected letter was altered | `8 fixtures, 1 failed`, exit 1 |

Letter digest (sha256) of `apply-backend-engineer.letter.txt`:
`784218ab86fe3c79d7b3618e8f01d54cfd6330f2c0427359ab2642b8e3d4c3a8`.

The first CLI runs of both jobs failed with `spawnSync kbb ETIMEDOUT`: with
the host loaded by other kbb runs, kbb took about 7 minutes before the script
started (most of it failed clones of kbb's own `fs-*`/`process-*`/`json-*`
dependencies), past the 300 s `cron.script_timeout_seconds`. The profile sets
900 s; the runs above are with 900 s. The gate results did not change between
a direct `kbb` run and the CLI runs.

No model was called: the jobs are `no_agent` scripts. No network request was
made by the scripts. All data is fictional.

## Not done

- The hiring-side profile (`awai-hiring-ops`, AWAI-3173), the app
  (AWAI-3175) and LinkedIn self-serve operations (AWAI-3174).
- Placement of other people's candidates with the isic-7810 actor (S2) and
  anything else that waits on ADR 0043 O1.
- Registration in `com-junkawasaki/root manifest/agent-registry` and
  installation into a host's `~/.itonami`. The profile is a reviewed
  definition, not a running bot.
- A live turn on the Murakumo rail. The drafting needs no model; a live turn
  waits on the profile-aware native host (AWAI-131).
- Running the profile test in CI (no kbb job for profile scripts).
