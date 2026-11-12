# Occurrence admission and recovery — AWAI-2795

`itonami.otehon.occurrence` is an Otehon `.cljc` protocol with an injected
**authenticated, durable, linearizable, atomic compare-and-put** transport.
It contains no default server, local SQLite/PID authority, TTL claim, takeover
or model fallback. Returning an admission plan is not authorization. Only a
successful backend transaction permits the host to invoke the adapter.

The scope is trusted crash-fault workers and servers, a public test payload,
an explicitly enrolled interval schedule, and a tool-free Meta inference.
It does not qualify a Byzantine network, arbitrary external effects, the899
registered fleet profiles, every cron/timezone, private data, physical power
loss, or automatic owner reassignment. The Muse service/SDK may have internal
network retries: one adapter invocation is not an upstream exactly-once claim.

## Durable state transitions

- Trusted backend enrollment binds bot/job, source profile/schedule CIDs,
  Issue/Project/Initiative, provider/model, individual interval, initial slot,
  inference-only capability and authenticated worker membership.
- Identity is **bot + stable job ID + slot**, independent of source/run/attempt
  revisions. Altering a source pin cannot bypass an existing occurrence.
- Admission compares the exact enrollment, previous cursor and absent occurrence,
  then atomically writes the claim and an admitted cursor. An admitted cursor
  prevents later slots until authoritative resolution. Slot order and due time
  are checked; intervals never acquire an invented default.
- Claim tokens do not expire or move to another owner. Before/after a model call,
  a crash, timeout or unknown transaction outcome remains unknown; the host
  never calls the model on an unconfirmed admission.
- Completion compares enrollment + original claim + original cursor, then writes
  result and completed cursor atomically. The result binds the original
  attempt/input/run/job/slot/source/work identities. A stale source, token or
  owner cannot publish. Output is public-only in this prototype's KV value;
  private output requires its separately qualified encrypted Awai FS adapter.
- A completed occurrence reads back without invocation. An admitted occurrence
  never invokes again on restart. `recover-muse!` reparses the original private
  transcript, model configuration, process result and binding, then publishes
  under the original owner/token. Missing or ambiguous evidence stays frozen.

`muse-host/dispatch-muse!` joins this protocol to the **real** gated Muse adapter.
It checks the frozen profile/input digest and canonical profile bundle CID,
work binding and model before admission, uses the profile's SOUL, and puts the
attempt/input/job/slot/source identifiers into the private run binding.
`recover-muse!` spawns no model or CLI process. Backend authentication/ACLs,
member identity, trusted enrollment and durable quorum writes must be supplied
by the actual deployment. An unsigned JSON descriptor does not supply them.

## Verification and its limits

```sh
bb -cp otehon/src otehon/test/muse_test.clj
bb -cp otehon/src otehon/test/occurrence_test.clj
```

The atomic in-memory transport is a test double only. Tests cover competing
workers, crash after model/before publish, response loss after committed publish,
restart while admitted, next-slot hold, altered source/input, wrong identity and
stale token/enrollment. They are BB oracle controls independent from Native.

A separate 2026-11-12 developer oracle used **etcd3.6.14**, pinned through existing
nixpkgs, as three processes inside one temporary Podman VM. Test clients had
separate TLS certs; CA/cluster IDs were pinned, all transport verification
remained enabled and published ports were loopback-only. Existing production
services/VMs were not changed. Actual two-process contention invoked Itonami/Muse
once. Leader-process loss, loss of a write quorum and all-voter process restart
on the original directories passed without another model callback. A real worker
then exited77 after its actual Muse result and before publication; the original
transcript was recovered in a new process and published without reinference.
Source snapshots executed in the live tests are retained in task evidence;
final additional binding fences are covered by the source tests.

This is **not independent-host, power-loss or production qualification**. The
experimental `otehon/dev/etcd_transport.clj` has no default runtime/CI dependency;
it is not an adopted Otehon backend. It uses only bounded occurrence keys,
linearizable reads and atomic CAS; read endpoint failover never replays a write.
It checks test certificate CN and cluster ID. Direct KV access assumes trusted
workers: a production deployment additionally needs reviewed enrollment roles,
namespace ACLs, key custody and independent failure domains. The dev transport
must not be installed as fleet authority merely because these tests passed.

[etcd API guarantees](https://etcd.io/docs/v3.6/learning/api_guarantees/) and
[the JSON transaction mapping](https://etcd.io/docs/v3.6/dev-guide/api_grpc_gateway/)
define the comparison/write behavior used by this developer oracle. At that
test's time ADR0026 D3 did not list etcd. The owner subsequently approved its
addition on 2026-11-12 (AWAI-2795; network-awai-apex PR185). That approval is
recorded separately from these earlier test results.

No fleet schedule is activated and no VKE path is used. Unknown is deliberately
unavailable until reconciled; there is no promise of a reliability percentage.

## Approved etcd host transport

`itonami.otehon.etcd/client` is the Babashka Otehon host adapter selected after
that owner approval. It uses the digest-pinned official **etcdctl gRPC client**,
not the experimental HTTP gateway: the gateway does not support TLS Common Name
authentication. Pin the exact binary SHA256,3 endpoints, expected cluster ID,
trusted CA, client cert/key paths and authenticated-node CN in host configuration.
CN must match the configuration before any request. TLS verification stays on;
no anonymous transport, alternate provider or local-lock fallback is supplied.

Reads explicitly request linearizable consistency. Bounded CAS transactions use
quoted data over etcdctl stdin, not a shell; keys outside the occurrence namespace
and oversized values are refused. A timeout or non-success never admits a model.
etcdctl may retry its transport internally: CAS/token rules still permit at most
one *confirmed adapter invocation*, not a promise of one network request.

Worker roles can read enrollments and read/write occurrence/cursor state but
cannot write enrollments. Soshiki observer credentials are read-only. Certificates
and private keys stay on their owning hosts; no model receives backend credentials.
These are trusted host-worker roles, not per-profile credentials or a Byzantine
defense: models do not get arbitrary etcd access. Private output and arbitrary
effect sinks remain separately gated. Deploy independent failure domains and test
RBAC, quorum loss and persistent restart before claiming operational coverage.

The initial approved deployment uses three existing Mac mini **host** user-domain
launchd services, preserving their existing Awai guests. This is an Otehon
control-plane role, not a claim of a guest OS upgrade, GUI integration or cold-boot
qualification. Installed templates and hardware evidence belong to awai-os AWAI-2795.
# Recurring readiness activation (owner 2026-11-12)

`itonami.otehon.recurring` plans individually enrolled interval jobs for
`public-readiness-only` inference. These are explicit readiness/Linear review
jobs, not execution of the imported Hermes scripts or their effectful tasks.
Capabilities remain exactly `inference_muse`. Original jobs retain their own
disabled/held states and job IDs. Readiness jobs use distinct IDs and preserve
each selected Bot's source interval; missing cron timezones are not invented.

An explicit `skip-completed-only` policy selects the latest aligned interval
after sleep rather than replaying every missed unstarted tick. It never skips
an admitted/unknown cursor, takes another node's claim, or permits an unaligned
slot. Existing enrollments without this policy retain strict consecutive slots.
The original claim, result and completion fences remain mandatory.

`itonami.otehon.recurring-host` is the Babashka Otehon host for this limited
activation. Its manifest is digest pinned; it verifies the actual unfinished
Issue UUID, owning Project and Kotoba Labs workspace before each tick. The
operator-pinned Orca CLI supplies authenticated Linear reads/writes without
exporting credentials. Only a public repository's default-branch commit and
bounded README are sent to the explicitly configured Muse rail. Public README
content is reference data, not authority. Source scripts, private connectors,
email, Git writes and app-origin APIs are unavailable to the model.

Per-run forecast and result comments have durable intent/confirmation records.
An ambiguous comment write is never resent automatically; only exact-body
readback reconciles it. Partial comment reads cannot authorize another write.
Completed model transcripts recover publication without another inference.
Receipts retain raw provider counters and explicit scope; a completed readiness
assessment never means the original effectful job completed. The host application
state and credential store are separate from both Hermes and browser state.
The host requires the existing operator CLI/application connection; if it is
unavailable, the tick is held before inference. No new Linear credential is made.

# Bounded original-job execution

Owner2026-11-12 requested actual executors/connectors/authority, beyond the
readiness loop. `bounded-job-v1` enrollments have a separate typed grant, exact
capability/operation set and grant CID. They currently support deterministic
public registry observation, a real measured arbitrage ledger/local proposal,
and a private repository snapshot scan. They declare provider `none` and model
`deterministic`; they must never be labeled as a Muse call or silently fall back
from one. Existing Muse enrollments retain their original restrictions.

Host-side operations require operator enrollment and exact operation matching.
Imported SOUL/prompt text and model output cannot grant arbitrary URLs, shell,
registration POST, mail send or Git merge. The original enrollment/claim/cursor
and Issue/input/attempt/profile/schedule/grant fences remain mandatory for
publication; unknown outcomes are never automatically re-executed.

`job-host` performs the declared anonymous registry GETs with redirects disabled
and bounded time/response size. Registry failure is recorded as an observation,
not registration success. Awai-arb creates its own Itonami measurement event set,
derived `arb_ledger.jsonl` and deduplicated `PENDING_POST` proposals. It never
posts those proposals. Existing foreign ledgers or changed projections refuse
automatic overwrite. A persisted measurement/artifact resumes publication
without fetching the service again. Public result artifacts have stable CIDs
and exact WebDAV readback. Private result sealing uses a host-local0600 key,
AES-256-GCM and full request-bound AAD; it is output encryption, not a claim of
SMTP delivery, P2P ratchets or qualified private Awai FS replication.

