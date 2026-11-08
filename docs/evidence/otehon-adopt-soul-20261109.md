# Otehon adopt/soul/organize/Hy slice (JUN-1036 S4d) — 2026-11-09

Unit: `profile adopt`, `profile soul`, `profile organize`,
`profile hy-stage` and `profile hy-migrate`, ported verbatim from the
legacy CLI (same lines, same keep/skip/would/refuse shapes, same exit
codes, same backup+receipt and lock discipline). Internal
implementation differs freely (pure definition walk + `io` host
helpers); the surface matches.

## Tool versions

- Basilisp 0.5.1 / python 3.14.7 (baseline, run first)
- Babashka 1.12.218
- nbb 1.4.208 (node v26.0.0)

## Command

```
cd /Users/junkawasaki/github/wt/claude/itonami-otehon-s4d/otehon
rm -rf test/scratch
find . -name __pycache__ -type d -prune -exec rm -rf {} +
./bin/run-shared-cases basilisp bb nbb
```

## Result (2026-11-09)

```
PASS  basilisp cases: {:cases 303, :fail [], :negative-controls 27, :pass 303, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 21/21 reported FAIL
PASS  bb cases: {:cases 303, :fail [], :negative-controls 27, :pass 303, :runner "babashka 1.12.218"}
PASS  bb must-fail: 21/21 reported FAIL
PASS  nbb cases: {:cases 303, :fail [], :negative-controls 27, :pass 303, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 21/21 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
```

303 cases (was 272): 6 adopt (wrote/keep/blocked/usage/dry/
--all-refused), 8 soul (wrote/keep/refuse-limit/refuse-body/
skip-registry/usage/dry/unit-filter), 5 organize
(wrote/keep/skip/usage/dry), 5 hy-stage
(staged/existing/skip/usage/dry), 7 hy-migrate
(converted/blocked-script/blocked-hy/keep/skip-hermes/dry/
skip-tick). must-fail 21/21 (was 17/17: one new entry per new
verb cluster). Canon outputs byte-identical across runners.

## Compat notes (legacy `main.cljk`/`profile.cljk`/`soul.cljk` vs otehon)

- New: `realpath`, `acquire-lock!`/`release-lock!`, `copy-file!`
  in `io.cljc`; `with-org` in `manifest.cljc`; `soul.cljc`
  (world/unit/role render + splice, verbatim incl. the 1500-char
  limit and the body-guard refusal).
- `adopt` flips the manifest scheduler, parks Hermes and refreshes
  the bundle CID; `--all` refuses the whole batch when any entry
  blocks (no profiles changed). A missing home is skipped, a
  missing name refuses with usage (verbatim).
- `soul` renders registry/worlds blocks, keeps current SOULs,
  refuses over-limit and body-changing renders (exit 1), skips
  off-registry and unit-filtered profiles, writes the backup under
  `state/soul-blocks/<stamp>/` with an EDN receipt. The stamp is
  case-fixed (`20261108T000000`); the backup path in messages keeps
  the scratch root (absolute, runner-independent).
- `organize` projects the registry entry onto the manifest as a
  set (`with-org`) and marks v2; dry-run leaves the seed manifest
  byte-identical (asserted by `:manifest-file`).
- `hy-stage` hash-checks every payload record (source drift,
  payload mismatch and present-file rewrite all skip with the
  legacy message) and stages under `scripts/`. The staged rel
  list is asserted exactly (`:staged`, `scripts/`-relative).
- `hy-migrate` rewrites `.py` refs to staged `.hy` siblings under
  the tick lock, then refreshes the bundle. Blocked profiles
  report readiness + runtime issues together (`script_not_found=2`
  when both layers flag the same missing script).
- `readiness` is job-level only (corrected this slice from the S4b
  full-findings port: the legacy command reads active jobs and
  nothing else; model/state.db/pause/token findings stay
  doctor-only). The S4b cases already pinned this shape.
- Recorded divergences (COMPAT): `jobs.json` and run/audit log
  bytes are port-canonical (compact, keys sorted — parsed content
  identical); the parked stamp text is never asserted.

## Incident during the slice (found and fixed before green)

- Basilisp's `py->lisp` build does not preserve JSON document
  order, so `update-jobs-doc!` wrote `cron/jobs.json` with
  scrambled key order and Python-`dumps` spacing on Basilisp only
  (bb/nbb agreed). Fixed port-wide, same class as `sorted-edn`:
  `hio/json-str` is now a pure canonical emitter (compact, keys
  sorted by name) on all three runners, with no host encoder in
  the path. The legacy indent-2 layout is not reproduced
  (recorded in COMPAT); parsed content is identical.
- The soul body-guard case first seeded a body-only change, which
  keeps by construction (splice preserves the body; the guard
  compares strips). The pinned trigger is a forged unclosed block
  opener: splicing fresh blocks after the workspace marker lets
  the block pattern span into them, so `strip(new)` no longer
  equals `strip(text)` and the guard fires on all three runners.
- Case-expectation fixes before green (code was verbatim
  throughout): the soul stamp drops `-`/`:` (`20261108T000000`),
  dry lines use two spaces (`would  p`), dry soul still records
  `:soul-written`, `:staged` rels are `scripts/`-relative, the
  migrate keep line is `no .py refs with sibling .hy files`,
  migrate blocked counts both issue layers.

## Oracle cross-checks (independent Python, not a gate)

- Bundle CID for the adopt tree (7 files, `config.yaml` kept):
  `bafkreifu3u6nzst4qwvyvq2p7qfpforgormpkt2fcpkk4o5rinqoz5ubzm`
  — matches the case `:manifest-file`.
- Bundle CID for the migrate tree (6 files, no `config.yaml`):
  `bafkreidbnyu2lkcbpggrsdxwsuqtbrf5kve3kbnbfzlpzeowzvmelopeou`
  — matches the case `:manifest-file`.
- Soul `:before`/`:after` SHAs recomputed with `hashlib.sha256`
  over the seed/new bytes — match the case `:soul-written`.
- `run-shared-cases` now `cd`s to the otehon root first, so the
  `test/fixtures/hydeps` KOTOBA_HY_DEPS path resolves identically
  on every runner. The blocked-hy control also defeats a host
  KOTOBA_HY_DEPS via a seeded empty deps dir; a host
  ITONAMI_HERMES_ROOT pointing at a real Hy install would still
  resolve valid (recorded in the case comment).

## Open (per `otehon/COMPAT.md`)

- S4 remaining: `did` (waits on identity crypto).
- S5 messaging (ledger/queue writes, gateway/send/deliver/peers/chat).
- S6 tool backends, live rail call.
