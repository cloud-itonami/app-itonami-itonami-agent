# AWAI-167 PR #53 conflict resolution — 2026-11-09

PR: `network-awai/awai-os#53` (head `b2d75ed`), "Connect Hermes to the
shell with local inference". No commit/push made here.

## CI (via gh, 2026-11-09)

- `evaluate`: pass (38s), `otehon`: pass (3m40s), run 42424434206.
- `mergeable: CONFLICTING` — main moved on (AWAI-168 #68–#77,
  AWAI-231 #72, AWAI-233).

## Conflicts (4 files, all union-resolvable)

- `nixos/otehon/shell/index.html` — CSP: PR ports (8642) + main ports
  (8774/8775/8776) unioned.
- `nixos/otehon/shell/package.json` — test: both sides' test files
  unioned (8 files, keeps `agent.test.js`).
- `nixos/otehon/shell/src/main.jsx` — imports: agent/* + net/sound/
  display/docks panels all kept (no name clashes).
- `nixos/otehon/shell/src/main.css` — PR `.messages li.answer` block +
  main net-view/dock blocks kept (selectors disjoint).

## Verification (scratch merge, discarded afterwards)

- `node --test` (8 files): 34/34 pass. `node build.mjs`: exit 0.
- Patch of the 4 resolutions:
  `~/github/workspaces/codex/awai-53-resolution.patch`
  (applies on the conflicted merge with `git apply`).

## Linear

- Comment posted to AWAI-167 with the above (comment id
  `ba3c1377-080f-43d0-86ea-9a7d87744ef0`).
- Remaining: rebase → push → CI re-run → VM check (24 GB root
  untested). VM check needs the owner's worktree + disk.
