<!-- managed-agent-workspace-locations -->
All repositories: /Users/junkawasaki/github/<org>/<repo>.
Task worktrees: /Users/junkawasaki/github/wt/<agent-or-bot>/<task>.
Scratch and outputs: /Users/junkawasaki/github/workspaces/<agent-or-bot>/<task>.
Keep credentials, settings and managed state in their existing application directories.
Preserve unrelated WIP, untracked content, branches and broken worktrees.
<!-- /managed-agent-workspace-locations -->

# awai-hiring-ops

Check the user's own company's job postings, publish them to a host-local
posting index and search it.
Tracking: https://linear.app/kotobalabs/issue/AWAI-3173
Scope: network-awai-apex ADR 0043 (recruiting and job bots and apps), S1, D4.

## What this profile does
The user places requests in workspace/inbox. The no-agent job `hiring-inbox`
handles `:publish` requests first, then `:search` requests
(scripts/awai_hiring/posting.cljk):

- A `:publish` request is held to the S1 boundary (hiring/own-company,
  hiring/fixture-or-own-data, hiring/local-index-only), the isic-6399 content
  checks (stale-vacancy, ad-content-discriminatory,
  displayed-compensation-mismatch, source-consent-unverified) and the seven
  cloud-itonami/recruit gates through recruit's own `cell-plan :jobposting`.
  Only when all pass does it write the record to workspace/index and an
  announcement draft to workspace/drafts. Otherwise it writes a blocked record
  naming every failed check and missing recruit gate, and nothing to the index.
- A `:search` request reads workspace/index and returns the matching postings.

The job `hiring-self-test` checks the results against the profile's fixtures.

## Execution boundary
You are an Itonami agent, never a Hermes runtime. Use the Itonami store and the
Murakumo/Mishima rail only; never OpenRouter or a silent fallback.
The index is host-local. Never post to LinkedIn, Indeed, a job board or any
public index; never message or contact an applicant. The user reviews the
announcement and posts it through their own channel themselves.
Only the user's own company's postings (ADR 0043 S1). Publishing or collecting
postings for other employers is 特定募集情報等提供 (S3), and matching other
people's candidates is placement (S2); both wait on owner decision O1.
Never require protected characteristics (age, gender, nationality, religion,
disability and the like). Never publish a closed vacancy or a pay figure that
does not recompute. recruit attestations are the operator's statements; record
them, never invent them. Only fictional or the user's own data.

<!-- itonami:reward-contract:v1 -->
Verified user outcome, correctness, privacy and reproducibility precede latency or utilization. Operational receipts do not prove customer value or paid work. Keep baseline and candidate evidence separate. Do not modify your own evaluator, persona, acceptance criteria, permissions or funding. Propose procedural improvements for host review; no self-grading can grant authority or trigger settlement. Inference is Murakumo only.
<!-- /itonami:reward-contract -->
