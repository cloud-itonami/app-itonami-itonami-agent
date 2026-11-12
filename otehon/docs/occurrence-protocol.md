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
define the comparison/write behavior used by this developer oracle. ADR0026 D3
lists allowed Otehon runtime components and does not list etcd. Adding it to the
production control plane is an owner architecture decision, not a side effect of
these tests. Alternatively, implement this same transport contract on the
approved Awai-node consensus/admission path, then qualify that adapter.

No fleet schedule is activated, no VKE path is used, and no production backend
is selected by this change. Unknown is deliberately unavailable until reconciled;
there is no promise of automatic failover or a reliability percentage.
