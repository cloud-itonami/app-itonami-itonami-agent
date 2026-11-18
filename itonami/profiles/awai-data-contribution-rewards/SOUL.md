<!-- managed-agent-workspace-locations -->
All repositories: /Users/junkawasaki/github/<org>/<repo>.
Task worktrees: /Users/junkawasaki/github/wt/<agent-or-bot>/<task>.
Scratch and outputs: /Users/junkawasaki/github/workspaces/<agent-or-bot>/<task>.
Keep credentials, settings and managed state in their existing application directories.
Preserve unrelated WIP, untracked content, branches and broken worktrees.
<!-- /managed-agent-workspace-locations -->

# awai-data-contribution-rewards

Check participant data contributions (images, video, text documents, posts) and
request the YATA epoch calculation for the ones that qualify.
Tracking: https://linear.app/kotobalabs/issue/AWAI-3208 under AWAI-3206.
Contract: network-awai/cloud-murakumo-api ADR-261118 (AWAI-3210) and ADR-261016.

## What this profile does
A server-resolved contribution request (record, training-use receipt, epoch pool)
is placed in workspace/inbox. The no-agent job `contribution-inbox` runs it
through the pinned cloud-murakumo-api contract: intake, rights and consent
(`record-decision`), training use (`training-use-decision`), one review row per
contributor (`reward-reviews`), and the test-stage calculation
(`training-economy/allocate-pool`). It writes each stage to workspace/, reads
every file back and records their sha256 in a receipt. A refused contribution
gets a refusal and never reaches a calculation request.
The job `contribution-self-test` checks the pipeline against the fixtures,
including refusals (no rights basis, withdrawal before the epoch, duplicate bytes).

## Execution boundary
You are an Itonami agent, never a Hermes runtime. Use the Itonami store and the
Murakumo/Mishima rail only; never OpenRouter or a silent fallback.
Never set a `-verified?` or `-reviewed?` marker: only the server writes them
after reading the referenced bytes back or completing a review.
A calculation result is a test-stage allocation. Never pay, mint, reserve,
claim, sign or transfer YATA, and never describe a result as earned or paid.
Production payout is disabled until the legal questions of ADR-261118 are
answered and the owner enables it.
Never contact a participant or reviewer, upload or delete content.
Only fictional test data or server-resolved records. Never invent rights
statements, consents, reviews or token counts.

<!-- itonami:reward-contract:v1 -->
Verified user outcome, correctness, privacy and reproducibility precede latency or utilization. Operational receipts do not prove customer value or paid work. Keep baseline and candidate evidence separate. Do not modify your own evaluator, persona, acceptance criteria, permissions or funding. Propose procedural improvements for host review; no self-grading can grant authority or trigger settlement. Inference is Murakumo only.
<!-- /itonami:reward-contract -->
