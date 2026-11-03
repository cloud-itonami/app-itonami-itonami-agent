# Cursor intent diagnostic

The previous pending-file check did not cover a crash or lost reply after
renaming the pending cursor over the live cursor but before syncing its
directory: the pending name was already absent. The candidate now writes and
syncs a separate intent before the replacement and refuses planning and
advance while it remains. Host serialization and reconciliation remain
required. The intent is evidence, not authority to execute a job.

On one local Darwin ARM64 host, a test-only source-unity fixture composed the
actual `cc-advance` function with the diagnostic FS2 provider and its hashing
helpers. In an isolated temporary directory, a normal update advanced the
cursor and moved the intent to `.last`; pre-existing pending and intent files
both refused the update. In the same fixture, an injected failure discarded
the response **after** the provider performed the pending-to-cursor rename.
The call returned `cursor/rename-unknown`, the pending file was absent, the
intent remained, and a second advance returned `cursor/intent-unknown`.

A second native fixture composed the actual cursor entry points with stubbed
planning, clock, writes, rename and sync, and used the candidate provider for
existence checks. With an intent present, both plan and advance returned
`cursor/intent-unknown` and exited nonzero. This is not a power-loss test,
production FS2 qualification, GitHub integration, recovery implementation,
or evidence of a running autonomous bot. The diagnostic provider and host
directory must enforce the documented absence, fsync and rename semantics.

SHA-256: cursor source `d3426abb7279a649b1cbd33728396e5a26c2a1ae9ad4933b7243ff7bdca7eae6`;
FS fixture `d6ed6d672372b4cd8bb7fc64df9f465a069170b2fe42b79428bd3005606b8847`;
entry fixture `a7b666d3773e00b2f4613ad281ff41526f3f3dfcb7705ac653cec85b80bb0231`.
