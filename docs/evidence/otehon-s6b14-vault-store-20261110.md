# Verification record — S6b-14 vault pure store ops — 2026-11-10

JUN-1036 S6b-14: the pure store core of the itonami credential vault.
`vault.cljc` ports the data projections of `itonomi.agent.vault` over an
explicit store map (string-keyed login records keyed by handle):
`login-meta` (agent-visible projection, nil when unknown), `add-login`
(keyword-keyed input, as the legacy `add-login!` receives it; handle
generated host-side), `remove-login` (`{:store :removed}`, pinning the
CLI's "removed" / "no such handle" contract), `secret` (password/seed
plumbing, never for the model), `list-rows` (sorted `[handle record]`
vectors, the `vault list` order) and `format-row` (the padded list
line: handle/14 + username/20 + origin + two spaces + label).
`:vault` verbs (`meta` / `add` / `remove` / `secret` / `list` /
`format`) wired through `ops-vault` in `cli.cljc`. 10 shared cases +
2 must-fail controls; 867 cases + 98 must-fail green on basilisp
0.5.1 / bb 1.12.218 / nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/vault.cljc`: new, 6 pure fns.
- `otehon/src/itonami/otehon/cli.cljc`: vault require, declare,
  `:vault` dispatch, `ops-vault`.
- `otehon/COMPAT.md`: S6b-14 entry + vault table rows updated.
- `test/cases.edn` +10, `test/must-fail.edn` +2.

## Portability notes

- `format-row` pads (never truncates), mirroring `padEnd(14/20)`.
- `list-rows` normalizes map entries to `[handle record]` vectors:
  seq-of-map shape differs per runner, vectors compare identically.
- `str/blank?`, keyword destructuring and `sort-by` are portable.
- Secrets never leave the store map: `login-meta` drops password and
  seed by construction (pinned by `meta-known`).
- The encrypted file (AES-256-GCM), the 0600 key file, `vh-` +
  random-hex handle generation, the stdin password read, TOTP minting
  (RFC 6238) and the `vault` command wiring stay S6.

## Runs

- bb 1.12.218: 867 cases, 0 fail; must-fail 98/98 FAIL (as designed).
- nbb (node v26.3.0): 867 cases, 0 fail; must-fail 98/98 FAIL.
- basilisp 0.5.1: 867 cases, 0 fail; must-fail 98/98 FAIL.
- Canon byte-identical across the three runners.
- Note: the first `:vault/add` draft used string-keyed input records;
  code-first inspection of the legacy `add-login!` (keyword
  destructuring) caught the mismatch before any run; the pinned case
  uses keyword keys for input, string keys for the stored record.
