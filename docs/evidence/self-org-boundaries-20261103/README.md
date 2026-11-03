# Self-organisation boundary test

The source is not yet connected to a production gateway. This change refuses
negative queue, worker and WIP observations, escalates invalid attempt counts,
and bounds backoff and staffing comparisons without signed i64 multiplication
overflow. Stop, expired lease and exhausted budget still prevent wake-up. Zero
WIP limit retains the existing fallback of one; zero retirement interval still
disables retirement. `wake/invalid` and `staff/invalid` are new named outcomes;
a future caller must treat both as refusals.

On a local Apple Silicon Mac, a unity fixture built from the complete decision
source ran all four built-in tests using the Amu bootstrap root and diagnostic
FS2 packager. It exited 0. Three independently built mutants, removing the
negative in-flight check, the backoff overflow check, and the negative queue
check in staffing, each exited 1. The fixture exercised normal behavior and
near-i64-limit inputs. The compiler SHA-256 was
`920954769b8fdc51cf50dfcab5c44a4e56cf01f261806a153b21488ba81ac4e6`;
the packager SHA-256 was
`f726f9dd8a4f169187275797a74d55ad3c62cc5f681cd31f19e1a28d67834f8d`.

Source SHA-256 `79f79909e49be79b76db55d3ea35ebc2e4a0b94f7ec8e65213dda14dcbe5df9a`;
signed fixture `9be81478e176e1782a13a4a1a12031ea280932ef94682c52aa61e336bc764910`;
mutants: wake `c0c91048ef7f665c3a2de90541d39f2ecd3622e1a8d04d846cf568f927628354`,
backoff `e99366284367560a007a30551d91be17663e76a55915cfa3cb59fb292d9bf139`,
staff `447be6c2a7041c2ed74711314cb7a0cbdda1658245d15bd19befbe162c9b151a`.
This is a local diagnostic gate, not the Linux static CI gate, production
runtime qualification, or proof of autonomous bot operation.
