# KNP1 converter disposable-fixture qualification

Date: 2026-11-03

This is a one-time check of `bin/convert-hermes-notepad-to-kotoba.py` against disposable SQLite files. No Hermes profile or user database was read or changed. The Python converter remains an offline migration tool; it is not part of the Itonami runtime, build, package, CI, or fallback path.

## Environment and command

- Python: 3.15.0 (`main`, 2026-10-09, Clang 21.0.0)
- SQLite: 3.54.1
- Command shape: `python3 bin/convert-hermes-notepad-to-kotoba.py <disposable-profile> --offline-confirmed`
- Input schema: `cron_notepad(job_id TEXT, key TEXT, value TEXT, updated_at TEXT, PRIMARY KEY(job_id,key))`

## Positive input and result

Input rows:

| job_id | key | value | updated_at |
|---|---|---|---|
| `job_01` | `alpha` | empty string | `2026-11-03T00:00:00Z` |
| `job_01` | `βeta` | `line one\nline two` | `2026-11-03T00:00:01+00:00` |
| `job-02` | `cursor` | `42` | `2026-11-03T00:00:02Z` |

- Exit: 0; receipt reported 3 records across 2 jobs.
- Source SQLite SHA-256 before and after: `60a161517c62caae759401ef6e9bca495b2866612dac64ffb1fba30ee60b5b7d`.
- Verified exact marker `.store-format = kotoba-notepad-v1\n`, KNP1 serialization, empty value, multiline value, Unicode key filename, job isolation, and 3 record hashes in `.conversion-receipt.json`.

## Refusal controls

- Re-running against the published disposable target exited 1 with `FileExistsError: target already exists; refusing to overwrite`. The source DB digest remained unchanged.
- A second fixture with key `bad\nkey` exited 1 during all-row preflight. No target marker/tree or staging directory was left; its source SQLite SHA-256 remained `621bfe404ce8d4dfa48d4361c380c5370f4e3720dea3e82329b2a4eabd6ae9f6`.

The machine-readable receipt (including captured stdout/stderr) and disposable source/output databases are retained in the task workspace at `github/workspaces/codex/notepad-converter-fixture-20261103/`. The converter was exercised only against those disposable fixtures. This does not qualify live-profile quiescence, concurrent writers, crash durability across power loss, or a cutover; retain those gates before migration.
