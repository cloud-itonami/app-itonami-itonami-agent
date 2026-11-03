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
directory. Before replacement, advance also writes and syncs an intent with
the original and proposed cursor CIDs, then syncs its directory. After the
replacement and its directory sync, it renames the intent to `.last` and syncs
the directory again. A leftover intent or pending file refuses further work;
neither is automatically cleared or assumed to mean the update failed.
Planning and advance require an explicit FS2 D absence response for both
paths. The CLI exits nonzero on a refused plan or unsuccessful advance;
only an idle/valid plan or a confirmed advance exits zero. The FS2 provider
must distinguish absence from lookup errors. The earlier Darwin diagnostic
provider does not; a later diagnostic candidate does, but is not qualified for
production. See [the isolated cursor fixture](evidence/awai147-cursor-unknown-20261103/README.md).
The rename-response loss window is exercised separately in the
[intent fixture](evidence/awai147-cursor-intent-20261103/README.md).

For a completed replacement only, `recover DEFINITION PIN CURSOR clock`
validates the current cursor binding, requires the pending path absent and a
well-formed intent whose proposed CID matches the current cursor, rechecks
and syncs the cursor and directory, then retires the intent and syncs again.
The caller must hold the same writer lock, protect the private directory,
and independently reconcile the GitHub archive before recovery. An old or
different cursor, pending path, malformed intent or uncertain filesystem
response stays unknown. This operation never dispatches or replays a job.
The shared `cc-recovery-ready?` preflight lets the controller refuse an
invalid local intent before invoking an archive operation; `cc-recover`
rechecks it immediately before syncing and retiring the intent.
See the [recovery diagnostic](evidence/awai147-cursor-recovery-20261103/README.md).

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
