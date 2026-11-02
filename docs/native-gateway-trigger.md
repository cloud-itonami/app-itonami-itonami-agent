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

The exported `verified-cron-*` functions accept a canonical JSON definition
with exactly `cron`, `id`, `input`, `kind`, `offsetMinutes`, `policy`,
`profile`, and `rail`. The kind is `itonami.cron-schedule/1`; the nonempty
`id` must uniquely identify the logical schedule and is part of its CID. The
caller supplies an independently trusted definition CID and a durable,
authoritatively admitted previous-slot cursor. The module checks the bytes
against that CID, validates the definition and job fields, verifies the candidate
matches the cron expression, and requires it to be the first slot strictly after
the supplied cursor. It then requires the slot to be due. Reusing an identity
or supplying an untrusted/stale cursor defeats this guarantee. Supported UTC
offsets are -840 to +840 minutes; epoch milliseconds are limited to 1970 through
2100-01-01 inclusive. Fixed offsets do not implement daylight-saving zones.

The caller must persist and verify the resulting input bytes by CID and make
them available to a compatible agent-step consumer. `cron-consume-problem` and
`cron-source-bytes` implement its validation boundary: given the host-pinned
definition and cursor, expected job ID, canonical job and wrapper, and the
resolved source bytes, they recheck the slot, exact job and wrapper, and the
raw SHA-256 source CID before returning the source bytes. Source bytes must be
nonempty, at most 8 KiB, and named by a raw CIDv1/sha2-256 CID. This is a
callable component; no existing OCI guest or host has been wired to call it.

The module does not prove the host's pinned definition or previous cursor was
authorized. It does not claim a slot,
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
the build used its actual job text, ID and validation functions. Native
negative controls that removed the slot from the identity or skipped the
next-slot comparison failed. The tests also cover a Vixie day-of-month/day-of-week
case, wrong definition CID, impossible dates, an invalid zone and a skipped
slot. Consumer tests also reject altered source, wrapper and job, and a wrong
job ID; removing the source-CID check makes the native gate fail. The input
wrapper still requires integration in an agent-step consumer; this test does
not demonstrate a live queue, gateway or job dispatch.
