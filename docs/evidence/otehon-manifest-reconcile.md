# Otehon manifest repair slice — measured runs (2026-11-09)

`cas/manifest-rebuild` (pure) + `cas-reconcile` command, with
`admitted-peers` factored out of `peer-repair` (identical behavior,
re-proven by the suite) and `read-edn-safe`/`cas-rehash-inventory`
helpers. Runners: bb 1.12.218, nbb 1.4.208 (node v26),
basilisp 0.5.1 / python 3.14.7.

## Shared cases (in repo, all runners)

`bin/run-shared-cases`: 124/124 pass on bb, nbb, basilisp;
must-fail 5/5; bb/nbb outputs byte-identical to basilisp.
5 new: `manifest-rebuild-basic` (adopt from two peers),
`-consistent` (agreement is silent), `-conflict` (local entry vs
peer entry over a held object), `-peer-disagree` (two peers claim one
rel — first wins, second becomes a conflict), `-unmatched`.

## Live disk demos (/tmp/rec-demo, not committed)

- Store S (`a.txt`, `b.txt`), peer P. Manifest overwritten with
  `garbage-not-edn{{{`: `cas-reconcile S P` → `:adopted 2`,
  `:local-damaged true`, `:unmatched []`; follow-up `cas-verify`
  clean — full recovery from content + peer.
- `a.txt` deliberately mis-pointed at `b.txt`'s digest (a held
  object, so a genuine ambiguity): refused
  `cas/reconcile-conflict` with `:conflicts`, exit 1, and the
  manifest byte-identical afterwards (nothing written on conflict).
- Manifest file deleted outright: reconcile re-adopts both entries,
  verify clean.

## Contract

- Content is re-hashed from bytes (`cas-rehash-inventory`); the
  path-derived digest is never trusted, so a tampered object cannot
  smuggle a wrong entry back into the manifest.
- Local entries are never overwritten: adoption only fills rels the
  manifest lacks. Any disagreement over a held object refuses the
  whole reconcile.
- Peers are read-only sources here, gated like repair (`:skipped`
  reported, never read). No admitted peer → refused
  `cas/reconcile-no-peer`.
- What stays open: time-based retention, fleet metadata, asymmetric
  signatures (the awai fs gap list).
