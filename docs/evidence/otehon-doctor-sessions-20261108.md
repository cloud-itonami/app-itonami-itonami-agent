# Otehon doctor/sessions slice (JUN-1036 S3b) — 2026-11-08

Unit: `doctor` findings and `sessions list`/`show` reads, ported
verbatim from the legacy CLI (same finding codes and lines, same exit
codes, same SQL text and filters, same list/show line shapes).
Internal implementation differs freely (pure `doctor.cljc` /
`sessions.cljc` verdicts + host sqlite reads in `io.cljc`); the surface
matches. `sessions resume` refuses verbatim (it was already
unimplemented in the legacy).

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-s3b2/otehon
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-08)

```
PASS  basilisp cases: {:cases 203, :fail [], :negative-controls 27, :pass 203, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 11/11 reported FAIL
PASS  bb cases: {:cases 203, :fail [], :negative-controls 27, :pass 203, :runner "babashka 1.12.218"}
PASS  bb must-fail: 11/11 reported FAIL
PASS  nbb cases: {:cases 203, :fail [], :negative-controls 27, :pass 203, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 11/11 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

203 cases (was 185): 9 doctor cases (ok, no-model, missing-token,
token-set, paused, script refs, delivery lane, load-failed,
no-state-db) and 9 sessions cases (list with hidden/tool filtering,
empty, missing db, show, unknown, missing id, bad limit, resume
refusal, corrupt db). must-fail stays 11/11. Canon outputs
byte-identical across runners.

## Compat notes (legacy `main.cljk`/`tools.cljk` vs otehon)

- One sqlite read layer, three engines: python sqlite3 (read-only URI
  + `query_only`), node:sqlite (readOnly + `query_only`), and the
  `sqlite3` CLI on bb (`-json -readonly`, literals escaped inline).
  Same rows on all runners; engine error text stays behind the
  `state.db unreadable:` prefix, which cases pin instead.
- Seeded state.db files are built by setup SQL in the test op (no
  binary fixtures); the setup drops tables first so reruns are clean.
- Doctor finding kinds that need the S6 registries (unsupported
  toolsets/tools, browser backend, oauth cache) are deferred to S6
  and never reported here; with no S6 conditions present the output
  matches the legacy exactly.
- `profile-load-failed` fires on unparsable jobs.json like the legacy;
  the message tail differs per JSON engine, so cases pin the prefix.

## Open (per `otehon/COMPAT.md`)

- S6-owned doctor kinds, the sqlite write path, the live rail call.
- S3 done except rails; S4 store writes, S5 messaging (ledger/queue),
  S6 tool backends.
