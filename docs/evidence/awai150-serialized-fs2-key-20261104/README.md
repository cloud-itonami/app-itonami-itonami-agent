# Serialized cron bounded key diagnostic

The diagnostic Mac runtime trapped on the legacy unframed `cr-read` key path.
The candidate adds a separate FS2-capable `key-seed-fs2` path for serialized
cron and its signer-bound job entry; the legacy key path is retained for other
callers. It checks framed regular-file metadata, requests the first 64 bytes,
checks the exact FS2 response and rechecks metadata. Host-owned exclusive paths
are still required: metadata and range calls are not an atomic snapshot and
a same-size replacement can evade this check.

On jacob, macOS 26.3.1 (a), build 25D771280a, native diagnostic tests with a
public synthetic zero key derived the expected DID. Short, missing and invalid
hex keys were refused; a longer file with a valid 64-byte prefix retained the
legacy prefix behavior. The current serialized guard executed actual schedule,
next-slot, job-ID and consume validation; tampered source was refused, and a
valid synthetic source produced the expected turn-intent and reached a test
job stub. Duplicate turn was refused. A separately built fixture of the actual
job entry (with clock stubbed) rejected a changed signer and short key. The
legacy unframed read continued to trap on this diagnostic runtime. No real key,
lease, bot, GitHub or workload was used.

The selected unity SHA-256 was
`245380c293b1f4d70858b4eb60afc90731c0765d1f29cf1a0d08d12e62872fe8`;
Murakumo cell source SHA-256
`ef49691ccca16406a5e5e0cc1ce41f213ad3b91ef926d338b1e952da3e09be5a`;
job-node source SHA-256
`77f35a9fd974b4e93e1b7e4dfbf1e0f3017bafdf9e7cee12a5621dc933283146`;
Itonami guard source SHA-256
`b0b677c742eacd7d523ffa1bdb1adc8bf43a5c05a24d6ebf55f850eda7cdec3c`;
harness SHA-256
`8ac2a78db727d83c543e1c1d9c285ed16288a6bb46ec530168b384969492066b`.
Current definitions were compared against the unity. The pinned diagnostic
compiler and packager were `920954769b8fdc51cf50dfcab5c44a4e56cf01f261806a153b21488ba81ac4e6`
and `f726f9dd8a4f169187275797a74d55ad3c62cc5f681cd31f19e1a28d67834f8d`.

This is a candidate for a host with FS2 O/E support; older runtimes can trap.
Provider failure, concurrent replacement, real credential custody, full job
execution, crash recovery, host containment and governor remain unqualified.
