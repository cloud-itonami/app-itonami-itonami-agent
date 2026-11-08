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
otehon/bin/itonami-otehon <basilisp|bb|nbb> pause      [reason words]...   (the store is ITONAMI_DIR, else ~/.itonami)
otehon/bin/itonami-otehon <basilisp|bb|nbb> resume
otehon/bin/itonami-otehon <basilisp|bb|nbb> status
otehon/bin/itonami-otehon <basilisp|bb|nbb> model      <dir> [NAME] [--provider P] [--model M]
otehon/bin/itonami-otehon <basilisp|bb|nbb> run-exec   <profile-dir> <job-id-or-name> [--capability c]...
otehon/bin/itonami-otehon <basilisp|bb|nbb> fallback   <dir> [-m M] [--provider P]
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
| `src/itonami/otehon/io.cljc` | files, JSON, EDN, exit, clock, listing, utf8 writes, pid liveness, atomic replace: the only host code | — |
| `src/itonami/otehon/pause.cljc` | emergency-stop marker paths, record, messages (pure) | `src/itonami/agent/pause.cljk`, `main.cljk` pause/resume-cmd |
| `src/itonami/otehon/status.cljc` | store status lines (pure) | `main.cljk` status-cmd |
| `src/itonami/otehon/model.cljc` | model/provider display, textual config edit (pure) | `provider.cljk` (chain/resolution display), `profile.cljk` (set-model-text, yaml-scalar) |
| `src/itonami/otehon/cli.cljc` | commands, canonical print, shared-case runner | — |
| `src/itonami/otehon/tick.cljc` | due jobs, next fire times (pure cron math) | `src/itonami/agent/cron.cljk` `parse-cron`, `next-run`, `due?` |
| `src/itonami/otehon/store.cljc` | import/export copy plan (pure) | `src/itonami/agent/profile.cljk` `copy-profile!` |
| `src/itonami/otehon/turn.cljc` | one-turn `-z` gate (pure) | `main.cljk` `oneshot` gating, `loop.cljk` toolset rule |
| `src/itonami/otehon/run.cljc` | real-run plan: prompt, output doc, record (pure) | `src/itonami/agent/cron.cljk` `build-prompt`, `output-doc`, `record-run-state!` |
| `src/itonami/otehon/text.cljc` | portable string ops, JVM whitespace pinned | `kotoba.lang.text` (the `.cljk` reference nbb cannot load) |
| `src/itonami/otehon/cas.cljc` | pure SHA-256, object sharding, manifests, gc/rebuild/retention plans | AWAI-232 (slices 1, 6, 8, 9) |
| `src/itonami/otehon/peer.cljc` | permissioned node registry, sync/repair plans, fleet census | AWAI-232 (slices 2, 4, 7, 10) |
| `src/itonami/otehon/market.cljc` | capacity attestations, ask/bid match, escrow, HMAC + injected-asym flow | AWAI-232 (slices 3, 5, 11) |

Everything except `io.cljc` and the two host-leaf branches in `text.cljc`
(`lower-case` case-folding) is pure and host-independent; `io.cljc` is the
one namespace with `#?(:lpy … :clj … :cljs …)` branches. `#?@` is not used
(the Kotoba reader does not accept it). The second slice (2026-11-08, owner
choice: expand the host surface) adds clock and directory listing to `io.cljc`
only — no other namespace carries `#?`, so every host difference stays
visible in this one file. The third slice (same day, AWAI-232) adds
latin-1 byte read/write and recursive mkdir to `io.cljc` under the same
rule; hashing itself stays pure so digests agree on every runner. The fourth
addition (same day, JUN-1036 S3a) is utf8 writes and pid liveness, again
`io.cljc`-only: `pause`/`resume`/`status` keep every other namespace pure.
`text.cljc` pins `trim`/`blank?` to JVM
`Character/isWhitespace` (NBSP kept) and `split` trailing empties to Java
semantics; regex matching itself stays host regex, as in `kotoba.lang.text`.
No `#?@`, no `.charAt`, no `str/trimr`, no `array-map`, no `\\\\` in regex
literals (Basilisp folds it); `\\x5c` is used for a literal backslash, and no
`\\u` escapes appear anywhere (Basilisp rejects them — whitespace is built
from code points via `char`). The full constraint set and the oracle
placement standard live in [PORTABILITY.md](./PORTABILITY.md).

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

- `test/cases.edn` — 185 cases, 27 of them negative controls. Each
  negative control with an edit is also run on the unedited fixture and
  must then *not* match, so the edit causes the rejection. The store
  cases (pause/status/model/fallback/run) touch a real scratch store
  (`test/scratch/`, ignored) instead of fixture text: `:seed` writes
  files, `:at` pins the marker timestamp, and adversarial seeds (garbage
  pid, unparsable peers, unnamable profile dirs, folded config text,
  non-string provider values) are asserted as positive cases — paths the
  legacy commands never refuse carry no negative control.
- `test/must-fail.edn` — eleven deliberately wrong cases (one a no-op edit).
  Every runner must report all eleven as FAIL; otherwise the harness could not
  tell a broken core from a working one.
- the canonical output of `check` / `dry-run` on the fixtures, a
  pause → status → resume → resume CLI flow on a scratch store
  (timestamps normalized), a model show → fallback → set → show flow on
  another scratch store, and the case summary, compared byte for byte
  across runners.

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
4. ~~Execute the plans on the host~~ — done 2026-11-08 (JUN-1036
   S2a): `run-exec` runs one job's host side (script spawn, monitor
   sources, output-doc save, jobs.json apply) verbatim from the legacy
   `run-job` path, stopping at `:planned` with exit 3 when an
   inference turn is needed — extended 2026-11-08 (JUN-1036 S2b) with
   finished-turn apply (ok/silent/error/`delivery_failed`, peer/placement
   lines, usage/run audit rows). What stays open: the live rail call;
   executions-ledger and delivery-queue rows stay slice S5.
5. Use these outputs as reference values for the Native route (AWAI-60,
   AWAI-131), recorded per the oracle rule, never as its fallback.
6. ~~Decentralized storage slice~~ — done 2026-11-08 (AWAI-232):
   `cas-put/get/verify` (content-addressed store, pure SHA-256),
   `peer-push/pull` (permissioned node sync with registry gate),
   `market-match` (capacity asks/bids, attestation expiry).
   Bytes travel latin-1; hashing stays pure. What stays open: native
   supply of the asymmetric verify-fn (flow + payload contract done)
   and native execution of these planners (the awai fs gap list).
7. ~~Store verbs `pause`/`resume`/`status`~~ — done 2026-11-08 (JUN-1036
   S3a): the emergency-stop marker and the store status lines verbatim
   from the legacy CLI (same texts, same exit codes), five shared cases
   plus two must-fail controls. `otehon/COMPAT.md` records the full
   API/command denominator and the remaining slices (S2 host execution,
   S3b model/fallback/doctor/sessions, S4 store writes, S5 messaging,
   S6 tool backends).
8. ~~Model display and textual set~~ — done 2026-11-08 (JUN-1036 S3b):
   `model`/`fallback` show the legacy lines; a set rewrites config.yaml
   textually (comments and key order survive) through an atomic
   sibling-rename that preserves the target mode (new files 0600),
   following symlinks like the legacy writer. Nineteen shared cases plus
   two must-fail controls. What stays open: the live inference calls.

Measured runs and their versions: `docs/evidence/otehon-cljc-20261107.md`,
`docs/evidence/otehon-cljc-20261108.md`,
`docs/evidence/otehon-cas12.md`,
`docs/evidence/otehon-repair.md` (106 shared cases; `peer-repair`
heals tampered objects from a permissioned peer, refuses when the
peer lacks or corrupts them),
`docs/evidence/otehon-market-sig.md` (113 shared cases; HMAC-SHA256
attestation sigs verified against python hashlib, `market-sign` +
`market-match --secrets`; asymmetric sigs stay open),
`docs/evidence/otehon-gc.md` (116 shared cases; `cas-gc` drops only
unreferenced objects, dry-run by default; time-based retention open),
`docs/evidence/otehon-multi-peer.md` (119 shared cases; `peer-repair`
across `<peerdir>...` with per-peer gates and copy fallback),
`docs/evidence/otehon-manifest-reconcile.md` (124 shared cases;
`cas-reconcile` rebuilds damaged manifests from content + peers,
conflicts refuse without writing),
`docs/evidence/otehon-retention.md` (130 shared cases; `cas-gc
--retain-days N` with tombstone grace, date math verified vs python),
`docs/evidence/otehon-fleet.md` (134 shared cases; `fleet-status`
copy census with `--copies N` threshold and per-peer gates),
`docs/evidence/otehon-asym-flow.md` (141 shared cases; injected-crypto
`verify-asymmetric` + `market-verify` demo suite; native handoff contract),
`docs/evidence/otehon-pause-status-20261108.md` (146 shared cases;
`pause`/`resume`/`status` verbatim with the legacy CLI, plus the
API/command compat matrix in `otehon/COMPAT.md`),
`docs/evidence/otehon-model-20261108.md` (165 shared cases;
`model`/`fallback` verbatim, textual config edit with atomic replace),
`docs/evidence/otehon-run-20261108.md` (178 shared cases; `run-exec`
host side verbatim, stops at `:planned` with exit 3),
`docs/evidence/otehon-turn-20261108.md` (185 shared cases; finished-turn
apply verbatim, unresolved-lane message, ledger/queue rows deferred).
