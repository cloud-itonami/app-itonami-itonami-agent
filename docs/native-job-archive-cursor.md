# Job archive and cron cursor candidate

The selected-source composition uses Murakumo job output and archive sources
from candidate commit [`f73deaf`](https://github.com/kotoba-lang/murakumo/commit/f73deaf2187619afe46da96fa294a0549896a134)
in [draft PR #586](https://github.com/kotoba-lang/murakumo/pull/586), which
depends on merged checkpoint sources including
[`d6fbfab`](https://github.com/kotoba-lang/murakumo/commit/d6fbfab289dcfe272121a06b43149251e7651330).
The Murakumo candidate adds checked checkpoint reads, closure verification,
block synchronization and Git configuration refusals. It is not merged;
this document records the source used for the check, not an enforced build pin
or a qualified dependency. Reconcile the exact merged dependency before build.
It derives the job ID and slot from the pinned schedule and current cursor,
checks the proof's node and the schedule's profile/policy, and learns a live
job lease before archiving. The signed receipt's node and epoch must match the
adopted lease, its timestamp must precede that lease's deadline, and its v2
lease digest must match the full canonical adopted lease. After the private GitHub archive independently
reads back and revalidates the signed checkpoint and output proof, it learns
the same live lease again and checks that the schedule and cursor are unchanged
before attempting the existing durable cursor update. An unknown result is
not permission to retry or advance; a surviving cursor intent requires the
existing explicit reconciliation path.

The separate opt-in `cron-job-archive-head-cursor-main` additionally takes an
independently trusted expected Git commit at argument 24. After the output
archive has been verified and the lease rechecked, it invokes the candidate
GitHub expected-head CAS, checks the fetched head receipt, then learns the
same live lease again and rechecks the schedule and cursor before advancing.
The CAS implementation re-verifies the archive in its own private child work
directory. The earlier `cron-job-archive-cursor-main` retains its existing
non-head path. The host must provision the initial head and pin the exact
expected commit; neither a GitHub ref nor the expected OID is a lease.

Before the CAS the candidate writes and syncs `cursor.head-intent`, recording
the expected head, checkpoint, job, prior cursor, task digest and slot. After
the cursor advances, it moves the marker to `cursor.head-last` and syncs the
directory. A surviving or unreadable intent blocks the cursor planning,
standalone cursor recovery/advance and archive coordinator entries. The head
may advance even if the final lease check, cursor write, marker finalization
or process fails: the host must quarantine the work and independently
reconcile GitHub head/archive and the local cursor, without automatic retry.

The job-node entry takes a caller-supplied previous slot and does not read this
marker itself. The trusted host must serialize all paths and refuse all job
dispatch and alternate cursor writers while the marker or any operation is
unknown. A marker's absence after an uncertain write is not proof of outcome.
CAS and lease checks are not atomic: this adds head concurrency control, not a
receiver-side fence or external-effect protection.

The host supplies and protects all paths, the schedule pin, checkpoint pins,
signer, authenticated quorum token, node identity, and a fresh archive work
path. It must serialize all writers across hosts and hold exclusive ownership
of the local files throughout. The two lease checks are not atomic with the
cursor write and do not supply receiver-side fencing; do not deploy this
candidate until an actual distributed writer fence, workload lifetime and
resource governor are qualified. This does not establish that a failed or
unknown cell had no external effects.
The signature binds the digest to the receipt; producing that receipt under a
live lease and enforcing effects against it still require runtime qualification.

Selected current gateway, checkpoint, archive and head-CAS functions and
decision test cases passed Amu source check with historical dependencies. The decision tests,
full dependency closure, native runtime, GitHub call, crash recovery and
cross-host serialization have not been executed for this composition.
