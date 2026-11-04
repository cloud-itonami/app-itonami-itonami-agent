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
