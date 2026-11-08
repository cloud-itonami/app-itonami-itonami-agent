# Otehon GC slice — measured runs (2026-11-09)

`cas/gc-plan` (pure) + `cas-gc [--apply]` command, with two new host
helpers in `io.cljc` (`file-size`, `delete-file!`, one branch per
runner). Runners: bb 1.12.218, nbb 1.4.208 (node v26),
basilisp 0.5.1 / python 3.14.7.

## Shared cases (in repo, all runners)

`bin/run-shared-cases`: 116/116 pass on bb, nbb, basilisp;
must-fail 5/5; bb/nbb outputs byte-identical to basilisp.
3 new cases: `cas-gc-basic` (one dead object → drop + bytes),
`cas-gc-clean`, `cas-gc-empty`.

## Live disk demos (/tmp/gc-demo, not committed)

Store G with `a.txt` (6 B) + `b.txt` (18 B: `-n droppable-data\n`
— the demo shell's `echo -n` wrote `-n` literally plus a newline,
confirmed by `od`; source file, stored object, and gc report all
agree on 18 B).

- `cas-gc G` (dry-run) on the full store → `:drop []`, nothing deleted.
- `b.txt` unpublished (manifest rewritten without it, the way a real
  unpublish drops the rel): dry-run → `:drop ["9531…"]`, `:bytes 18`,
  `:keep 1`, store untouched.
- `cas-gc G --apply` → `:applied true`, `:deleted 1`; the dead object
  file is gone, the live one stays, `cas-verify` clean, and `cas-get`
  of the dropped sha is refused `cas/object-missing` (exit 1).
- Bug found live and fixed: `--apply` was read from the wrong arg
  slice (same shape as the earlier `market-sign` bug — flags after a
  single required arg live in `b`, not `more`), so the first apply ran
  as a dry-run. Fixed, re-verified (suite re-run green after the fix).

## Contract

- Default is dry-run (plan only, deletes nothing). `--apply` deletes
  exactly the unreferenced objects and reports `:deleted`.
- The manifest is never rewritten by gc; liveness always comes from
  the current manifest. A failed delete refuses the whole gc
  (`cas/gc-failed` with `:failed`), nothing half-removed is reported ok.
- Time-based retention (keep deleted objects N days, audit log of
  drops) stays open: it needs a policy owner and a trustworthy clock,
  so this slice reclaims only what nothing references right now.
