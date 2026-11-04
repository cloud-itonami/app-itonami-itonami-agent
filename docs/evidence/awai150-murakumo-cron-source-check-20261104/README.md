# Murakumo and Itonami selected source composition

On 2026-11-04, a selected historical unity source was refreshed with all 147
function definitions from the current Murakumo checkpoint, job output,
archive, GitHub archive, head, GraphQL head and job-node sources at commit
`f2e38a81fa4b1423f3ec8c494885308e0e2148e0`. All 36 functions from the
Itonami cursor, guarded job-node and archive-cursor sources at commit
`889a1a6b5e433154ebe854ff37b4d9b06cb036e6` were present and matched their
source definitions. The existing Amu CLI `check` returned `ok`.

The input historical unity SHA-256 was
`b09336a24a7265180fd8306eaafb9c2ba7470e8fa7608694ee748e07c35b00d4`;
the refreshed unity SHA-256 was
`05c50d38bb0e6c92981d942443008eb8a56a2d84ebf045e54b9030c1c30a1cc9`.
The one-off assembler had SHA-256
`6965d320ccaafbc19a6b638647f42fd6121b89c89919da34864937c9dc967cbf`;
its output path was changed for this run. The current archive source SHA-256
was `a5e8808cd21e2d68f3b79e9ed74f7ec1919805532db61f8ecd9403ab2d74846e`.
The unity and assembler were local staging artifacts and are not an enforced
source lock or a reproducible release build.

This validates selected source composition only. It did not run the Itonami
cases, compiler-generated native program, actual GitHub operations, fault or
crash recovery, production provider, host containment, a model, or bots. The
final merged cross-repository closure must be pinned and verified independently.

## Native pure decision cases

On jacob, macOS 26.3.1 (a), build 25D771280a, the selected unity above was
compiled with the pinned diagnostic Mac compiler and packaged with the pinned
FS2 diagnostic packager. The three current cursor, guarded-node and
archive-cursor case functions ran and returned exit 0. A separately built
mutant removing the pending-state refusal returned exit 1. The harness checks
that each test function in the unity exactly matches its current checkout.

The native fixture source SHA-256 was
`576b6bb17ec867b5316537d2244bf4573b7d3a970e8f3c25eb6ee8906ede21fc`;
the harness SHA-256 was
`931e1420db177f16a1f957536207da7910f2c12a2885464a3831aca58dcf6b86`.
The diagnostic compiler and packager SHA-256 values were
`920954769b8fdc51cf50dfcab5c44a4e56cf01f261806a153b21488ba81ac4e6` and
`f726f9dd8a4f169187275797a74d55ad3c62cc5f681cd31f19e1a28d67834f8d`.
These are pure decision cases. No filesystem provider, lease, signed workload,
GitHub transaction, crash recovery, bot or resource governor was exercised.

## Native bounded cursor reads

On the same Mac, a source-bound fixture used the current `cc-read-bounded`
function and the matching pinned Murakumo checkpoint read functions through a
signed diagnostic FS2 runtime. It accepted the small input and the 1024-byte
cursor and 8192-byte definition limits, and returned no bytes for the
1025-byte and 8193-byte over-limit files, an empty file and a missing file. A
separately compiled variant with the size limit removed failed the cases.
All files were in a fresh private FD3 scope.

Cursor source SHA-256:
`bae632993f616879aead22792d7a2015992a17f2e1cdb9f3f6ee15de7795797c`;
case source:
`9bdfedfe9bb96e5d5d3d2e9702817c33bf112f3ddbedbed0ae1c54b5de18bcff`;
fixture source:
`59230acc78916e87b282713c3ccfb0a1760090d9d78847e8858ba04a84ce6a5b`;
harness:
`96a4a34e1271b0bc0df0b22bfa402d709f22d1378132bb3a78156f51b5d964e7`.
The unity, checkpoint, compiler and diagnostic packager pins are the same as
above and in the associated harness invocation. This is one local provider
sample, not short-read fault injection, metadata-race or provider qualification,
actual cron dispatch, GitHub publication, crash recovery or a governor.
