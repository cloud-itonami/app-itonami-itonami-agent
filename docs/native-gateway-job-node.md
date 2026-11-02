# Native cron job-node adapter

AWAI-147 connects the verified cron consumer to the existing Murakumo
agent-step node. `cron-job-node-main` takes the node's existing `run`
arguments in positions 1–18, with `run-cron` at position 0. It additionally
takes the durably admitted previous slot at 19, pinned schedule CID at 20,
definition path at 21, and source path at 22. Position 9 must be `clock`.
The host, not a model or a checkpoint, supplies these trusted values and
file paths. The wrapper is the existing job input at position 16.

Before competing for a lease, the adapter validates the definition, next due
slot, job, wrapper and source CID through `cron-consume-problem`. It passes the
validated source to the existing node execution path; the job identity,
lease, durable attempt marker and receipt still bind to the wrapper. The
node re-reads the wrapper and verifies its CID before acquiring the lease.
Normal `job-node-main run` continues to pass the original input unchanged.

Build by composing the pinned Murakumo `fleet_job_node` source at commit
`1bf2127` ([PR #565](https://github.com/kotoba-lang/murakumo/pull/565)), the pinned `itonami.schedule`,
`gateway_trigger`, and `gateway_job_node` with the normal job-node dependency
closure. The current selfhost source-unity build also renames schedule's
private `digit?` to avoid a flattened-name collision. This is not a standalone
prebuilt service, and it does not run a scheduler or persist its cursor.

An ARM Linux static source-unity gate compiled the actual node, schedule,
gateway and adapter sources and returned 0 for valid and altered input cases.
An x86 Linux fixture then exercised the actual adapter, proposer, three
authenticated loopback voters and durable attempt directory. Cell admission
and execution were substituted with stubs: the first job reached the cell
stub with the source hash, the repeated job was refused at the attempt marker,
and changed source was refused before the cell. Voters were on one host; no
topology membership, real cell, effect sink or restart was tested. The same
live fixture on a separate ARM host reached its lease but could not launch
the `stat`/`mkdir` subprocess because process isolation was unavailable there.

A subsequent isolated ARM Linux Podman VM ran the actual node admission and
actual network-disabled, read-only test cell, without either cell stub. The
cell read the source on stdin, returned exit 0, and its output CID matched the
independently calculated value. OpenSSL independently verified the signed
receipt using the public RFC 8032 fixture key. A repeat was refused at the
attempt marker and a changed source was refused before cell execution. The
three authenticated voters remained colocated in the same test VM. The cell
was rule-based and did not perform model inference. The temporary token and
dedicated VM were removed after the test. [Evidence](evidence/awai147-cron-real-cell-20261103.json).

The host still needs trusted schedule enrollment and durable cursor advancement,
reconciliation for unknown outcomes, resource admission, cross-host fencing,
and real cell/production verification. A prior slot supplied again under a
different cursor is not made safe by this adapter alone. A source digest is
an integrity check, not authorization to use its contents or perform effects.
