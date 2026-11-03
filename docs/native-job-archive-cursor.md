# Job archive and cron cursor candidate

This candidate composes the current cron cursor with Murakumo job output and
archive sources at merged commit [`d6fbfab`](https://github.com/kotoba-lang/murakumo/commit/d6fbfab289dcfe272121a06b43149251e7651330).
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

The selected composed source and its decision test cases passed Amu source
check. The decision tests, native runtime, GitHub call, crash recovery and
cross-host serialization have not been executed for this composition.
