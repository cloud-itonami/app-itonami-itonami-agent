# Verification record — S6c-9 CLI arg parsing — 2026-11-12

JUN-1036 S6c-9: the ninth S6c slice — the pure half of the CLI
argument parser in `cli.cljc`. `opt-value` shapes one generic
`--flag value` / `--flag=value` pair (toolsets comma-split, the
`:force` / `:verbose` / `:trust` flags coerce literal true/false,
everything else verbatim); `parse-args` reads Hermes-style argv to
`[opts positionals` (`-p` / `-z` / `-m` / `--provider` / `-t`
anywhere, `--flag=value` carries the whole value, a following token
is a value unless dash-prefixed non-numeric — Telegram ids like
`-100123` stay values). The new `:parse-args` op (`parse` /
`opt-value` verbs) mirrors the `:doctor` / `:doctor-kinds` split.
12 shared cases + 2 must-fail controls; 1090 cases + 139
must-fail green on basilisp 0.5.1 / bb 1.12.218 / nbb, canon
byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/cli.cljc`: + `cli-boolean-flags` /
  `opt-value` / `parse-args` + declare + `:parse-args` dispatch +
  `ops-parse-args`.
- `otehon/COMPAT.md`: S6c-9 entry.
- `test/cases.edn` +12, `test/must-fail.edn` +2.

## Portability notes

- No interop: sets-as-functions, `str/split`, `str/starts-with?`,
  `str/index-of`, `subs`, `keyword` and `re-matches` only — each
  with existing gate precedent on all three runners.
- Verified divergence: none new. The short `-t` keeps the legacy
  split-on-missing (a bare trailing `-t` throws, like the legacy —
  unpinned, no passing case); the long `--toolsets` stays nil-safe
  through `opt-value`, exactly as the two legacy spellings differ.
  `die`, `-main` argv wiring and executor/profile host reads stay
  host-side.

## Runs

- bb 1.12.218: 1090 cases, 0 fail; must-fail 139/139 FAIL (as designed).
- nbb (node v26.3.0): 1090 cases, 0 fail; must-fail 139/139 FAIL.
- basilisp 0.5.1: 1090 cases, 0 fail; must-fail 139/139 FAIL.
- Canon byte-identical across the three runners.
