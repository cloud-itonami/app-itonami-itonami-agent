# Durable local cron cursor

AWAI-147 adds a native cursor planner for the existing verified cron job
adapter. A trusted host provisions a private canonical JSON cursor:

```json
{"kind":"itonami.cron-cursor/1","schedule":"<pinned schedule CID>","slot":1793645760000}
```

`cron-cursor-main plan DEFINITION PIN CURSOR clock` validates the cursor,
definition, schedule pin, job fields and wall clock. It returns `cursor/idle`
or a two-line plan with the previous and next slot, deterministic job ID,
input CID, profile, policy, and exact canonical trigger wrapper. Malformed,
future, misbound or unverified input is refused. `advance DEFINITION PIN
CURSOR PREVIOUS SLOT` checks that the current cursor and next due slot still
match, then writes and fsyncs a temporary cursor, renames it and fsyncs the
directory. A leftover pending file or uncertain write refuses further work.

Advance itself does not verify GitHub. Its trusted caller must serialize
writers and only invoke it after independently verified archive readback.
The write is atomic on a qualified local filesystem, not a multi-host CAS.
The clock and cursor pins come from host configuration; restoring an old
cursor or using different local state on another host needs reconciliation.
Neither the plan nor the cursor grants resource use, tools or effects.

An ARM Linux native source-unity regression passed, while removing the
schedule-binding check failed. In a separate isolated ARM Podman VM, the
companion host runner in murakumo drove the actual cron adapter, job node,
three authenticated colocated voters, real read-only native test cell,
output verifier and private GitHub archive. After independent readback it
advanced the cursor and then reported idle. Replaying the pre-advance cursor
revalidated the saved log and archive without a second cell attempt; an
incomplete log left the cursor unchanged. A pre-existing local lock refused.
The VM and ephemeral token were removed.

The cell was a rule-based fixture and the signing key was public. These tests
do not prove production identity, MTP inference, multi-host fencing, durable
distributed cursor consensus, crash/power-loss survival, or sustained service.
