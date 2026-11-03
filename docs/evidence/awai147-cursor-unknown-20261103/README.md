# Cursor refusal and exit status

The native cursor planner previously ignored a pending replacement, and its
advance check treated every FS2 D response other than `1` as absent. The CLI
also returned success after printing a refused plan or unsuccessful advance.
It now requires explicit `0` for the pending lookup and returns nonzero for
refusal. This is not a distributed lock or a resolution of the pending write.

The attached test-only fixture extracts the current cursor entry points and
advance function. On one local ARM64 Mac it uses the candidate diagnostic FS2
D provider for existence checks. Planning, cursor serialization, writes,
rename, fsync, IDs and clock are stubbed; files and permissions are confined
to a temporary fixture directory. A present pending file and a pending file
denied read permission both yielded `cursor/pending-unknown` and nonzero from
the real advance entry point. A synthetic unavailable lookup was also refused.
An absent pending file permitted the stubbed advance; an invalid-plan stub
returned a nonzero status. The old-control function reported idle with a
pending file and reached its stubbed advance when the pending file could not
be read. Cursor bytes remained unchanged in the final test run.

This checks neither actual cursor persistence nor crash recovery, concurrent
writers, GitHub, leases or an autonomous bot. The FS2 provider must reliably
distinguish absence from errors; a prior diagnostic Darwin version did not.
Even the newer candidate is not production-qualified, and the host must
serialize access and protect the directory against concurrent mutation.

Cursor source SHA-256:
`365edbdc592c640e0469668f581a67d41031e5020b4b6dd7b906cd447409adc2`.
Signed fixture and old-control image SHA-256:
`bc8defbd67f870a2dbfc318a7c5ad34a88da9fd23d84cc98d74c10d0d5c2da1d`,
`d04ccfd5ac31a4f9e21d617548b0151433d57007647c72d3594e3bafa90dbf87`.
The compiler and candidate provider are the diagnostic artifacts described in
kotoba-lang/amu's `docs/evidence/awai154-darwin-fs2-existence-20261103/`.
