# Itonami bot gateway and GitHub state: integration contract

Status: **proposed, not implemented or production-qualified**. Tracking:
[AWAI-147](https://linear.app/kotobalabs/issue/AWAI-147),
[AWAI-131](https://linear.app/kotobalabs/issue/AWAI-131),
[JUN-307](https://linear.app/kotobalabs/issue/JUN-307),
[JUN-289](https://linear.app/kotobalabs/issue/JUN-289).

The owner requested GitHub as bot storage on 2026-11-02. This contract describes
how to reconcile that request with the existing Cell, placement and lease
authorities; it does not silently rewrite earlier Yataverse assumptions or
claim that either integration already exists. The private `itonami-state` repo
contains a frozen application snapshot, not a current bot database. Do not
overwrite, reset, import, or treat that snapshot as the bot store.

## Boundaries

* The gateway admits bounded triggers and submits agent-step jobs to the
  existing Murakumo/Cell execution path. It does not itself grant capabilities,
  run a second placement authority, or turn storage access into permission for
  network delivery, spending, publication or tool use.
* A private GitHub repository can hold explicitly approved bot state and
  immutable evidence. It is a durable transport and versioned storage, not a
  distributed lease, a proof of a verified Cell head, or a replacement for
  `sahai`/`inga` fencing. Existing Cell verification remains authoritative.
* Only the Itonami profile and inference rail are in scope. Do not adopt Hermes
  state, jobs, credentials or provider settings. Use the approved Kotoba/Amu
  native route; unported routes must refuse rather than fall back to Node/kbb.
* GitHub is external storage even when private. No keys, bearer tokens,
  credentials, unapproved private inputs, raw sessions, or ambient host data
  belong in commits, paths, metadata or logs. A private repository is not an
  encryption boundary. The data classification, retention and authorized
  repository/installation must be explicit before persisting actual bot data.

## Proposed state and checkpoint

For one authorized profile, version a manifest with: profile identity and
profile artifact digest; state schema and migration version; committed
sequence and predecessor; verified Cell/head reference where applicable;
gateway trigger id and job id; lease/fence epoch as an auditable reference;
model artifact digest and runtime profile; output/receipt digest; and state
object digests. Objects must be bounded, classified and integrity checked.
The manifest must not contain secrets or a reusable approval, live lease, socket,
process handle or credential. A hash does not make sensitive or low-entropy
data safe to publish. The exact schema and encryption/key ownership are open
implementation decisions, not an implemented wire format.

Use a distinct, access-controlled namespace for bot state, with an explicit
repository and ref allowlist. Restore by immutable commit and verified object
digests; verify the profile, schema and Cell authority before exposing state to
a fresh host. Missing objects, unknown schema, revoked access, tampering,
stale or forked heads and failed migrations must refuse. Recovery never
revives an old approval or lease: the destination obtains fresh admission.

## Commit and failure protocol

1. Acquire the existing profile/job lease and fencing token; check the trigger
   id and ensure the job has not already been committed. A repository lock or
   GitHub branch alone cannot establish distributed execution authority.
2. Execute one bounded turn with the pinned profile/model and allowed tools.
   Record a classified result and a signed execution receipt, if provided by
   the existing runtime. Treat a timeout after an external effect as UNKNOWN,
   not as a safe retry.
3. Validate the proposed checkpoint and current authority. Commit to the
   explicitly allowed state ref using expected-parent compare-and-swap. A
   non-fast-forward, stale fence, changed parent or uncertain write must stop;
   never force push, automatically merge state, or claim success from a local
   commit. GitHub ref comparison prevents overwriting a changed ref; it does
   not atomically validate a separate lease. Before integration, establish a
   host/provider enforcement protocol that rejects a stale writer at the
   actual commit boundary. Until that is proven, distributed writes are
   blocked.
4. Read back the remote ref, commit and object digests. A lost acknowledgement
   is UNKNOWN until readback resolves it. Only then record the checkpoint as
   persisted. Restart reconciles remote commit, verified head, lease and job
   receipt before another turn. GitHub outage leaves the job pending/unknown;
   it never silently selects an unrelated store.

## Inference qualification

The requested model is Ternary Bonsai 2 27B with MTP. Identify and pin the
actual weights, tokenizer, any MTP/draft artifact, runtime build and digest;
observe the MTP setting and accepted-draft metrics on the running service.
The presence of a Bonsai 27B process or an MTP-like filename is insufficient.
Measure a non-sensitive bounded prompt and record output, timing, resource
pressure and errors. Check available capacity and protect existing workloads;
do not replace a running service to infer model readiness. A node without
verified identity, model, MTP mode or admission is ineligible, not a fallback.

## Acceptance gates

* A single explicitly authorized test profile and node complete a native,
  tool-restricted turn against the qualified model; retain model/runtime and
  signed receipt evidence without including the prompt or credentials.
* A classified checkpoint is written to the approved private GitHub ref,
  independently read back, and restored on an empty runtime with fresh
  admission. Verify repository access and encryption where required.
* Negative tests reject wrong profile, model or schema; tampered/missing state;
  stale Cell head/fence; competing writes; replay; revoked credentials;
  unavailable GitHub; and uncertain effect or commit acknowledgement. Prove
  unchanged remote state for rejected writes.
* Show graceful stop, restart/reconciliation and no duplicate turn. Expand to
  another node only after the single-node evidence and independent failure
  domain checks pass. Publication, external messaging and payments remain
  separate operations with their existing authorization boundaries.
