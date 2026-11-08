# Otehon peer repair slice — measured runs (2026-11-09)

`peer/repair-plan` (pure) + `peer-repair` CLI command (`itonami-otehon`).
Runners: bb 1.12.218, nbb 1.4.208 (node v26), basilisp 0.5.1 / python 3.14.7.

## Shared cases (in repo, all runners)

`bin/run-shared-cases`: 106/106 pass on bb, nbb, basilisp;
must-fail 5/5; bb/nbb outputs byte-identical to basilisp.
4 new cases: `peer-repair-basic` (mismatch heals, missing-without-peer
is unrepairable), `peer-repair-none` (clean → empty plan),
`peer-repair-unrepairable`, `peer-repair-sorted` (deterministic order).

## Live disk demos (/tmp/repair-demo, not committed)

- S = store with `a.txt`/`b.txt`, P = peer via `peer-push`.
  Tamper S's `a.txt` object (first byte → `X`): `cas-verify` →
  `{:mismatch ["a.txt"], :status :refused}`. `peer-repair S P` →
  `{:repaired 1, :status :ok}`; follow-up `cas-verify` clean and
  `cas-get` output `cmp`-identical to the original.
- `c.txt` added to S after the push, then tampered: peer P lacks it →
  `peer-repair S P` refused `peer/repair-incomplete` with
  `:remaining ["c.txt"]` and `:unrepairable ["c.txt"]`.
- Both S and P objects for `a.txt` tampered: refused
  `peer/repair-copy-failed` with `:failed [sha]`; local manifest
  untouched, the healthy `b.txt` object intact (verify still flags
  exactly the two bad rels, nothing silently dropped).
- Registry gate: unknown node → refused `peer/not-member`
  (same gate as push/pull).

## Contract

- :ok only when the post-copy verify is clean. Copy failures and
  peer-missing rels both refuse; the manifest is never rewritten by
  repair (bad rels already point at the right digests).
- Source objects are re-hashed before copy and re-verified after
  write (reuses `peer-apply-one`); a corrupt peer cannot poison
  the local store.
- What stays open: multi-peer fallback (try next peer when one copy
  fails or lacks the rel), repair of a damaged manifest itself,
  signature crypto, retention/GC.
