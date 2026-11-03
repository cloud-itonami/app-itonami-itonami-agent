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
The same unity source and mutants were then compiled by the pinned ARM64
Linux static root compiler in a task-owned, 1 CPU / 1 GiB VM. Structured
results were 0 for the fixture and 1 for each mutant. The compiler SHA-256
was `914b828a24ced086e09929894bb64806dea103bf79766ef144001a076717b1c4`.
The static ELF hashes were fixture
`47d03219c03dab9c95141bf457b56e2406b635c8e102e2cbb4e2c31be86dc152`,
wake `229766f891aaa602aa693090656a587a67e278896e785c1d72f16decb174c4d2`,
backoff `525abb4df604eb17d6fdadb2b8750f43f5c7eec0d6e5441f8f98964a840f9929`,
staff `5362c30c2666e421c012077ffa4ee4866c1502227a5d4562217d24e4a0ebdcee`.
The test VM was stopped and deleted. `generate.py` reconstructs the unity
sources and mutants for this test; it is not production tooling.

This isolated test does not run CI, establish a production runtime boundary,
or demonstrate autonomous bot operation.
