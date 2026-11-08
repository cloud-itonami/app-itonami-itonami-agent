# Otehon model/fallback slice (JUN-1036 S3b) — 2026-11-08

Unit: `model` / `fallback` display plus the textual config.yaml edit,
ported verbatim from the legacy CLI (same lines, same refusal texts,
same exit codes). Internal implementation differs freely (pure
namespaces + an atomic host write); the surface matches.

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-s3b/otehon
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-08)

```
PASS  basilisp cases: {:cases 165, :fail [], :negative-controls 27, :pass 165, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 9/9 reported FAIL
PASS  bb cases: {:cases 165, :fail [], :negative-controls 27, :pass 165, :runner "babashka 1.12.218"}
PASS  bb must-fail: 9/9 reported FAIL
PASS  nbb cases: {:cases 165, :fail [], :negative-controls 27, :pass 165, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 9/9 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

165 cases (was 146): 19 new model/fallback cases — display sources,
pinned runs, comment-preserving sets, quoting/coercion writes, folded
and sequence refusals, all six arg-validation errors, flag-parser
forms. must-fail 9/9 (was 7/7): the two new controls prove the model
line and the rewritten text bite. Canon outputs byte-identical across
runners, including a model show → fallback → set → show CLI flow.

## Host-write probe (one-off oracle, dated)

`io/replace-file!` is new host surface (tmp + rename, mode preserve,
symlink follow), so it gets a dated manual probe besides the shared
cases — `/tmp/s3b-probe/probe.cljc` (not in the repo), one store per
runner (seed `config.yaml` 640 + `link.yaml` symlink), same assertions:

- Basilisp: link write lands in the target (returns the resolved path),
  fresh file created, control-char write refused with the exact message.
- bb / nbb: identical outputs.
- After state on all three: target still 640 with the new content, the
  link still a link, the fresh file 600, no `.tmp-*` residue.

```
640 config.yaml / 600 fresh.yaml / 0 tmp files (all three runners)
control-char refused: this value contains a control character config.yaml cannot round-trip
link.yaml -> config.yaml (intact, target updated)
```

The control-character refusal is not expressible in shared EDN
(Basilisp rejects `\u` escapes, so no case can spell the input);
the probe covers it with `(char 1)` instead.

## Notes

- Profile selection is positional (`model <dir>`) — the otehon
  convention, already used by every other command. Everything else
  (texts, sources, exit codes) is legacy-verbatim; recorded in
  `otehon/COMPAT.md`.
- Error lines print bare to stdout with the legacy exit code (the
  legacy `die` wrote stderr; otehon has no stderr channel).
- New files: `otehon/src/itonami/otehon/model.cljc`. Extended: `io.cljc`
  (`process-id`, `symlink-target`, `replace-file!`), `cli.cljc`
  (commands + `:model`/`:fallback` ops), cases, must-fail,
  `run-shared-cases`, README, COMPAT.
- What stays open: the live inference calls (`complete-hop`,
  breakers) — slice S2.
