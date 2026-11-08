# itonami-agent Otehon (.cljc)

The Otehon version of the itonami agent CLI core, written once in `.cljc` and
run on Basilisp (Python, feature key `:lpy`), Babashka (`:clj`) and nbb
(`:cljs`). Owner instruction 2026-11-07: implement itonami in `.cljc`, with
the parts Hermes Agent does in Python written as Basilisp-readable `.cljc`,
and keep the profile format compatible. Tracking: [JUN-1008](https://linear.app/kotobalabs/issue/JUN-1008).

**Path status.** This is the Otehon path of ADR 0026 D18 (network-awai-apex):
build on stable runtimes first, then use the result as the oracle for the
Kotoba + Amu selfhost implementation. It is not the Native path. Nothing in
`bin/`, `kotoba/`, the native gates or the native build reads `otehon/`, and
`otehon/` never falls back to or from the native kexe. The repository's
selfhost-only rule for the Native route (`AGENTS.md`) is unchanged.

## What the first slice does

```
otehon/bin/itonami-otehon <basilisp|bb|nbb> check      <profile-dir>
otehon/bin/itonami-otehon <basilisp|bb|nbb> dry-run    <profile-dir> <job-id-or-name> [--capability c]...
otehon/bin/itonami-otehon <basilisp|bb|nbb> tick       <profile-dir>
otehon/bin/itonami-otehon <basilisp|bb|nbb> turn-gate  <profile-dir> <prompt> [--capability c]...
otehon/bin/itonami-otehon <basilisp|bb|nbb> run-plan   <profile-dir> <job-id-or-name> [--capability c]...
otehon/bin/itonami-otehon <basilisp|bb|nbb> store-plan --direction import|export --from <dir> --to <dir> [--force]
otehon/bin/itonami-otehon <basilisp|bb|nbb> test       otehon/test/cases.edn
otehon/bin/run-shared-cases [basilisp bb nbb]
```

1. Read a profile directory: `SOUL.md`, `config.yaml`, `profile.yaml`,
   `cron/jobs.json` (the Hermes files) and `itonami.edn`, `yakuwari.edn`
   (the itonami files). Nothing is written.
2. Check it: itonami.edn (`cloud.itonami.profile.v1/v2`), yakuwari.edn, the
   Two CLIs rail rule, and the inference host against `itonami.edn
   :inference`.
3. Dry-run one cron job: find it, resolve its inference target the way
   Hermes does (job pin, then `config.yaml model:`), apply the cron toolset
   rule and the reasoning-effort pin, decide the capabilities the caller
   names against yakuwari.edn, and return the plan. No script runs, no
   inference is called, no output is saved.

Output is one canonical EDN value with sorted map keys, so the three runners
are compared byte for byte. Exit codes: 0 ok, 1 refused or errors, 2 held for
the owner, 64 usage.

## Layout

| file | role | ported from |
|---|---|---|
| `src/itonami/otehon/yaml.cljc` | the YAML subset Hermes profiles use | `src/itonami/agent/yaml.cljk` |
| `src/itonami/otehon/manifest.cljc` | itonami.edn validation | `src/itonami/agent/manifest.cljk` `validate` |
| `src/itonami/otehon/yakuwari.cljc` | yakuwari.edn validation and decisions | `manifest.cljk`, `soul.cljk` vocabulary |
| `src/itonami/otehon/rail.cljc` | Hermes / itonami rail verdicts, inference target | `kotoba/itonami/rail.kotoba`, Hermes `cron/scheduler.py` order |
| `src/itonami/otehon/cron.cljc` | jobs.json, cron hint, toolsets, plan | `src/itonami/agent/cron.cljk` |
| `src/itonami/otehon/profile.cljc` | load, check, dry-run (pure) | `profile.cljk` |
| `src/itonami/otehon/io.cljc` | files, JSON, EDN, exit, clock, listing: the only host code | — |
| `src/itonami/otehon/cli.cljc` | commands, canonical print, shared-case runner | — |
| `src/itonami/otehon/tick.cljc` | due jobs, next fire times (pure cron math) | `src/itonami/agent/cron.cljk` `parse-cron`, `next-run`, `due?` |
| `src/itonami/otehon/store.cljc` | import/export copy plan (pure) | `src/itonami/agent/profile.cljk` `copy-profile!` |
| `src/itonami/otehon/turn.cljc` | one-turn `-z` gate (pure) | `main.cljk` `oneshot` gating, `loop.cljk` toolset rule |
| `src/itonami/otehon/run.cljc` | real-run plan: prompt, output doc, record (pure) | `src/itonami/agent/cron.cljk` `build-prompt`, `output-doc`, `record-run-state!` |
| `src/itonami/otehon/text.cljc` | portable string ops, JVM whitespace pinned | `kotoba.lang.text` (the `.cljk` reference nbb cannot load) |

Everything except `io.cljc` and the two host-leaf branches in `text.cljc`
(`lower-case` case-folding) is pure and host-independent; `io.cljc` is the
one namespace with `#?(:lpy … :clj … :cljs …)` branches. `#?@` is not used
(the Kotoba reader does not accept it). The second slice (2026-11-08, owner
choice: expand the host surface) adds clock and directory listing to `io.cljc`
only — no other namespace carries `#?`, so every host difference stays
visible in this one file. `text.cljc` pins `trim`/`blank?` to JVM
`Character/isWhitespace` (NBSP kept) and `split` trailing empties to Java
semantics; regex matching itself stays host regex, as in `kotoba.lang.text`.
No `#?@`, no `.charAt`, no `str/trimr`, no `array-map`, no `\\\\` in regex
literals (Basilisp folds it); `\\x5c` is used for a literal backslash, and no
`\\u` escapes appear anywhere (Basilisp rejects them — whitespace is built
from code points via `char`).

## Compatibility kept

- **Layout.** The six files above, at the paths Hermes uses. itonami.edn is
  additive: the `:additive` case reads a profile with and without the two
  itonami files and requires the Hermes files to parse identically, the
  runtime flipping from `itonami` to `hermes`.
- **Two CLIs** (`AGENTS.md`). A profile with itonami.edn is an itonami agent;
  every provider and model it names (config `model`, `providers`,
  `fallback_providers`, each job's pin) must be on the murakumo rail, the
  resolved host must be in `:authorities` and not in `:deny-hosts`, and
  `:deny-hosts` must name `openrouter.ai`. A profile without itonami.edn is a
  Hermes agent and must not name murakumo or mishima. Verdict strings are the
  ones `kotoba/itonami/rail.kotoba` pins, plus `rail/denied-host` and
  `rail/host-not-authorised` for the host check.
- **yakuwari.** `:autonomous`, `:voice-required`, `:approval-required`,
  `:blocked`; legacy `:self-executing` / `:propose` / `:forbidden` read as
  their modern words; any other word reads `:blocked`; an unlisted capability
  is `:blocked`. Without yakuwari.edn every capability is at most
  `:approval-required`. `itonami.edn :capabilities` grants nothing; drift from
  yakuwari.edn is a warning.
- **Store.** The CLI reads the directory it is given and writes nothing; it
  never touches `~/.hermes`.

## Decisions (recommended options taken)

- **Pure core, host I/O injected.** `load-profile` takes `read-text`,
  `parse-edn` and `parse-json`. The shared cases edit fixture text in memory,
  so negative controls need no extra fixture directories.
- **Port the legacy readers instead of using host YAML libraries.** PyYAML,
  clj-yaml and js-yaml differ at the edges; one `.cljc` reader gives the same
  data on all three runners and keeps the legacy reader's refusals.
- **`:yakuwari/id` is optional.** Seven live profiles have no `:yakuwari/id`;
  requiring it would break them.
- **Duplicate capabilities in yakuwari.edn are an error**, since which entry
  wins would be a guess.
- **A dry run gates on authority the same way an unattended run would**:
  all named capabilities `:autonomous` → `:ok`; any `:voice-required` or
  `:approval-required` → `:held`; any `:blocked` → `:refused`.
- **Capabilities are named by the caller.** Hermes jobs carry no capability
  list, and guessing one from toolsets would invent authority.

## Checks

`bin/run-shared-cases` runs on each runner:

- `test/cases.edn` — 78 cases (18 `:text` oracle cases), 27 of them
  negative controls. Each negative control with an edit is also run on the
  unedited fixture and must then *not* match, so the edit causes the
  rejection.
- `test/must-fail.edn` — five deliberately wrong cases (one a no-op edit).
  Every runner must report all four as FAIL; otherwise the harness could not
  tell a broken core from a working one.
- the canonical output of `check` / `dry-run` on the fixtures and the case
  summary, compared byte for byte across runners.

CI: `.github/workflows/oracle-otehon-cljc.yml`, an oracle-only job with
pinned runners. It is not a required check.

Measured runs and their versions: `docs/evidence/otehon-cljc-20261107.md`,
`docs/evidence/otehon-cljc-20261108.md`,
`docs/evidence/otehon-text-oracle-20261108.md`.

## Next slices

1. ~~Run a job for real on the Otehon path~~ — planned 2026-11-08
   (`run-plan`: prompt assembly, output doc, jobs.json record as data;
   `tick`: due jobs, `next_run_at`, the 5-field cron parser;
   `store-plan`: `profile import/export` copy plan). What stays host-side
   and unwritten: pre-run script spawn, the murakumo rail call, saving
   `cron/output/`, applying the jobs.json record, monitor sources,
   delivery lanes, the executions ledger.
2. ~~`cron tick` and `profile import/export`~~ — planned as above.
3. ~~One turn (`-z`) with the toolset gate~~ — gated as above (`turn-gate`).
4. Execute the plans on the host (script spawn, rail call, save, apply).
5. Use these outputs as reference values for the Native route (AWAI-60,
   AWAI-131), recorded per the oracle rule, never as its fallback.

Measured runs and their versions: `docs/evidence/otehon-cljc-20261107.md`,
`docs/evidence/otehon-cljc-20261108.md`.
