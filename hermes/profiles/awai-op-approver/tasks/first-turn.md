# Work order: awai-op-approver

Issue: AWAI-161
Decision record: network-awai-apex ADR 0030

Input from the broker: operation, target, requesting bot, the broker's dango verification result, and evidence.

Return JSON only, with `verdict` (`allow` or `deny`), `operation`, `target`, `requester`, `reason` and `unknowns`. The verdict covers only that operation and target.
