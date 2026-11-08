# Otehon time-based retention slice — measured runs (2026-11-09)

`cas/retention-plan` (pure) + `cas-gc --retain-days N` with a
`tombstones.edn` audit trail per store. Pure date math (`ymd->days`,
Hinnant days_from_civil) verified against python; portable
`parse-int` for the CLI count. Runners: bb 1.12.218, nbb 1.4.208
(node v26), basilisp 0.5.1 / python 3.14.7.

## Date oracle

`ymd->days` agrees with python exactly: `2026-11-09` → 20766,
`1970-01-01` → 0, `2026-01-01` → 20454; malformed input → nil
(fail-closed, never dropped on bad math).

## Shared cases (in repo, all runners)

`bin/run-shared-cases`: 130/130 pass on bb, nbb, basilisp;
must-fail 5/5; bb/nbb outputs byte-identical to basilisp.
6 new: `retention-basic` (drop/hold/mark/revived together),
`retention-zero-days` (no tomb + 0 grace → immediate drop),
`retention-boundary` (exactly N days → drop),
`retention-bad-date` (malformed tomb → re-mark, never drop),
`cas-parse-int-valid/invalid`.
A first version of the zero-days case failed (no-tomb read as
mark, not drop) and forced the semantics below; the suite re-ran
green after the fix.

## Live disk demos (/tmp/retain-demo, not committed)

- Store S (`a.txt`, `b.txt`), `b.txt` unpublished:
  `cas-gc S --apply --retain-days 30` → `:mark [sha]`, `:deleted 0`,
  object still on disk, tomb written with host today.
- Tomb backdated to `2020-01-01`: same command → `:drop [sha]`,
  `:deleted 1`, `:bytes 7`, tomb file empty, object gone.
- Tomb present, then `b.txt` re-published: next apply →
  `:revived [sha]`, tomb cleared, `cas-verify` clean.
- `--retain-days xyz` → refused `cas/bad-retain-days`, exit 1.

## Contract

- Default (no `--retain-days`) is the legacy immediate gc, unchanged.
  With retention, drops wait out the grace in `tombstones.edn`;
  re-referenced objects are revived, stale tomb entries cleared, and
  the manifest is never rewritten.
- `days ≤ 0` drops untombstoned objects at once (no grace to wait
  out); malformed tomb dates always re-stamp first, in either mode.
- What stays open: fleet metadata, asymmetric signatures (the awai fs
  gap list).
