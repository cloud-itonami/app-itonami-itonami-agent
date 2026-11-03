# Completed cursor replacement recovery candidate

`cc-recover` recognizes only an intent whose proposed CID matches the current
cursor and whose original CID is different. It requires an explicit absent
pending file and a present 125-byte intent, uses a bounded range read, checks
the exact intent frame and current CID again, syncs cursor and directory,
renames the intent to `.last`, and syncs the directory. All other outcomes
remain unknown. The caller must serialize writers and independently reconcile
the archive; this does not establish GitHub state or a distributed fence.
FS2 metadata and range reads are not atomic and require exclusive host-owned
files and a qualified provider. The `recover` CLI checks the cursor's schema,
schedule binding and time before calling this function.

On one local Darwin ARM64 Mac, a test-only native fixture used the actual
`cc-recover`, checkpoint metadata/size/range functions and a diagnostic FS2
provider on isolated files. Its CID decoder was a limited stub, so it does not
qualify the real decoder or full CLI. A prior diagnostic generated an actual
post-rename intent and cursor; recovery succeeded, preserved the cursor and
moved the intent to `.last`. Pending, original cursor, unrelated cursor,
malformed intent, absent intent and oversized intent all returned unknown and
left their cursor and intent unchanged. No archive, job, process or model was
run. This is not a power-loss, concurrent-writer, or production-provider test.

A separate source-unity composition including this function passed the native
compiler's `check`; other historical input remains unqualified. Full source
provenance, native host execution, GitHub readback, fault recovery and fleet
integration remain open.

| Artifact | SHA-256 |
| --- | --- |
| cursor source | `feb547b4d720e7d3d345a75753ee5d96009d1996f3aa5219041fa9094e1cf062` |
| test fixture source | `7e31a2efafc50d70ffd25f4127a698e35d1bb0ae76cccbc9c77121acc83ca607` |
| signed fixture | `66f1e8c3cc5cfe0b3e0dd08a9c08411bc0c88127886e09c11cea04b95e8a3e40` |
| checked source-unity | `58da3c4f58e9777a3a8f14f4212cc915a2a6c620212c2f65733ad01d4f08f5c2` |
