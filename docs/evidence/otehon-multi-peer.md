# Otehon multi-peer fallback slice — measured runs (2026-11-09)

`peer/repair-plan` takes `[{:peer :manifest}]` in try-order (first
listing peer wins, recorded as `:from`); `peer-repair` accepts
`<peerdir>...`, gates each peer separately, and falls through to the
next listing peer when a copy fails. Runners: bb 1.12.218, nbb 1.4.208
(node v26), basilisp 0.5.1 / python 3.14.7.

## Shared cases (in repo, all runners)

`bin/run-shared-cases`: 119/119 pass on bb, nbb, basilisp;
must-fail 5/5; bb/nbb outputs byte-identical to basilisp.
The 4 single-peer repair cases were rewritten to the generalized
contract (`:remotes` + `:from`); 3 new: `peer-repair-multi-split`
(x from p1, y from p2), `peer-repair-multi-first-wins` (same rel on
both peers — first wins, pinned), `peer-repair-multi-unrepairable`.

## Live disk demos (/tmp/multi-demo, not committed)

- S tampered + P1 (primary) tampered, P2 healthy:
  `peer-repair S P1 P2` → `:ok`, `:from {"P2" 1}`; verify clean,
  `cas-get` `cmp`-identical. The corrupt primary never poisons the
  store (source re-hash refuses it, next peer heals).
- Registry `{"n1" {:prefix "Q1"}}`, `peer-repair S2 Q1 Q2 --as n1`:
  Q2 gate-fails (`peer/prefix-mismatch`) → listed in `:skipped`,
  never touched; Q1 heals → `:ok`, `:from {"Q1" 1}`.
- `--as zz` (unknown): refused `peer/repair-no-peer` with both
  `:skipped` reasons, exit 1.
- Single peer, clean store → `:ok`, `:repaired 0` (no-op shape).
  Single peer, both copies corrupt → refused
  `peer/repair-copy-failed`, exit 1 (legacy path preserved).

## Contract

- Peers are tried in listed order; `:from` records the winner per
  object, aggregated as a peer→count map on `:ok`.
- Gate-failed peers are skipped and reported, never read. No admitted
  peer at all → whole command refused (`peer/repair-no-peer`).
- Copy failure falls through to the next listing peer; only when every
  candidate fails (or nobody lists the rel) does the command refuse.
  The manifest is never rewritten.
- What stays open: manifest repair, asymmetric signatures,
  time-based retention, fleet metadata (the awai fs gap list).
