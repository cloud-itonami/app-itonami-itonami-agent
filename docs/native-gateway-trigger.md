# Native gateway cron trigger input

Tracking: [AWAI-147](https://linear.app/kotobalabs/issue/AWAI-147) and
[JUN-307](https://linear.app/kotobalabs/issue/JUN-307). This is a native
preparation component, not a running gateway or an execution authority.

`kotoba/itonami/gateway_trigger.kotoba` combines the existing `due?` decision
from `itonami.schedule` with the actual Murakumo `job-problem`, `job-text` and
`job-id` functions. For a due slot it produces canonical UTF-8 JSON:

```json
{"input":"<source CID>","kind":"itonami.cron-trigger/1","schedule":"<schedule CID>","scheduledAt":1792638000000}
```

The input's raw SHA-256 CID becomes the `input` of the existing
`itonami.agent-step/1` job. Thus a retry for the same schedule, input and due
time produces the same job ID even if observed later, while the next due time
produces a different ID. An inactive or future slot, negative time, malformed
CID, invalid profile or policy, and a non-Itonami rail refuse preparation.

The schedule CID must identify an independently validated immutable definition
including its unique logical schedule identity, profile and version; reusing
the same definition CID for different schedules can collapse their job IDs.
The caller must independently compute and verify that `scheduledAt` really is a
slot of that definition, persist and verify the input bytes by CID, and make
them available to a compatible agent-step consumer. This module cannot prove
any of those facts from a CID or `due?` alone. It also does not claim a slot,
create a reservation, perform I/O, submit a job, reconcile an unknown outcome,
enforce a lease/fence, or grant tools and network access. Unknown outcomes must
be reconciled under the same ID, not turned into a new slot or a blind retry.

The native regression in `test/native/gateway_trigger_cases.kotoba` composes
the actual schedule and Murakumo agent-step decision functions with pinned
SHA-256, JSON and CID dependencies. It checks independent JSON/CID/hash vectors,
same-slot stability, distinct slots, invalid inputs and due gating. The checked
Murakumo source was `fleet_agent_step.kotoba` at main
`1a6cd277b65441d647cdc6a54cb4862cae96cd82` (SHA-256
`8e61e145c6c6dd1269a0cc33a44c88d5e34782cee7ca831c0872632863c6258d`);
the build used its actual job text, ID and validation functions. A native
negative control that removed the slot from the identity failed. The input
wrapper is new and requires an agent-step consumer that understands it; this
test does not demonstrate a live queue, consumer, gateway or job dispatch.
