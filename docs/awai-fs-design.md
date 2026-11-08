# Awai Object Storage — tiered object storage and local cache for itonami-agent CLI

> Naming update (2026-11-08): this B2-backed design is **Awai Object Storage**. **Awai FS** now refers to the separate Awai Network distributed filesystem; see [AWAI-161](https://linear.app/kotobalabs/issue/AWAI-161). The historical filename and proposed `:awai-fs` identifier are retained for reference, not an instruction to rename deployed resources.

Status: design proposal (2026-11-08). Not implemented. All measurements cited
below are single-run values from the JUN-964 JuiceFS trials, not benchmarks.

## 0. Problem

Local development storage is full: the Mac root filesystem reports 100% used
(892G / 901G, 8.4G free, observed 2026-11-08). Growth drivers are many full
checkouts (each Amu-class worktree holds hundreds of MB, mostly
`docs/evidence`), build caches, model weights, Lima VM disks (~7–14 GiB),
JuiceFS trial caches, restic restore targets, and a 100 GiB-capped rclone VFS
cache. The existing rclone `nfsmount` B2 volume (`b2-261023-github`) is
currently unhealthy: directory listing hangs and `b2-storage status` returns
empty (observed 2026-11-08). Stacking more FUSE-on-NFS on top of it is not
viable. This design builds the replacement on the already-evaluated local
JuiceFS instead.

## 1. Measured ground truths (JUN-964, 2026-11-07)

Source: `workspaces/codex/jun-964-juicefs/RESULTS.md`,
`LOCAL-BACKUP-DESIGN.md`, `OFFSITE-DESIGN.md`. JuiceFS 1.4.2.

| # | Observation | Design consequence |
|---|---|---|
| 1 | macFUSE mount never became ready (extension not loaded, root cause unknown). No native macOS FUSE today | No native Mac FUSE path until the kext-load root cause is fixed |
| 2 | Loopback WebDAV works but: symlink ENOTSUP, `flock` not enforced across processes, AppleDouble `._` files, exec-bit flips, npm install fails | WebDAV is cold-read only, never a dev worktree |
| 3 | Lima Linux VM + native FUSE works: Git init/add/commit/worktree/status/fsck, symlinks, `flock`, npm offline install, `go build/test` all pass | Phase-0 POSIX runs inside the Linux guest |
| 4 | 22,798-file Amu checkout: local APFS 4.95s; FUSE+writeback 40.78s; FUSE without writeback timed out at 180s (734 files) | Git common dir and active worktrees stay on local SSD; JuiceFS is warm/cold tier, not the hot Git path |
| 5 | Writeback ON + abrupt guest stop: fsync-closed 1 MiB file lost (0 bytes, EIO, B2 object absent). Corrupt rawstaging + no full verify: silent wrong content (SHA mismatch, exit 0) | Writeback defaults OFF for acknowledged data; `--verify-cache-checksum full` for critical reads |
| 6 | B2 native API rejected the restricted key; S3-compatible B2 works | Object backend is B2 via the S3-compatible endpoint |
| 7 | Restic (rclone backend) → B2: 1 GiB backup 33s, `check --read-data` clean, `restore --verify` clean, `worktree repair` fixes restored pointers | Restic is the proven snapshot tier for Git worktrees |
| 8 | No external NVMe was present; "NVMe" was never established as a protocol claim. Internal Apple Fabric SSD only | "Local NVMe cache" means: internal SSD today, dedicated external NVMe later after measurement |

## 2. What Awai Object Storage is (and is not)

- Awai Object Storage is a **tiered storage subsystem for itonami-agent CLI**: scalable
  B2-backed capacity with a local fast cache. It is a thin policy + mount +
  CLI-integration layer **over the JuiceFS client**, not a new filesystem
  implementation.
- Execution path follows the repo rule: new itonami-agent routes are
  Kotoba/Amu selfhost native kexe. No Node/kbb/JVM/nbb in runtime, gates, or
  fallback. An unported route stays blocked.
- Store separation (Two CLIs) holds: Hermes never reads Awai Object Storage state.
  `profile import/export` moves profile *definitions* only, never caches,
  sessions, runs, or volume state — same boundary as today.
- The `.cljc` Otehon mirrors this design later (`otehon/`). No fallback
  between Native and Otehon routes.

## 3. Tiering model

| Tier | Backing | Holds | Writeback |
|---|---|---|---|
| hot | local APFS SSD | Git common dir, active worktrees, build outputs | n/a (local fs) |
| warm | JuiceFS + local SSD cache | evidence, retired worktrees (read-mostly), model weights, npm/caches, cron outputs | OFF, except declared scratch |
| cold | B2 objects + restic snapshots + age-encrypted metadata dumps | point-in-time recovery, offsite | n/a |

Rule of thumb from measurement 4: anything `git status` must be fast on
stays hot. Anything re-downloadable or read-mostly goes warm. Anything needed
only for recovery goes cold.

## 4. Namespace

One JuiceFS volume per trust domain, prefixes per owner:

- `awai-dev` — scratch and warm working data
- `awai-runs` — per-profile run/receipt archives (`<profile>/<run-id>/…`)
- `awai-evidence` — `docs/evidence`-class bulk (the main disk hog)
- `awai-models` — weights and runtimes, pin-on-use

Never run two writable metadata engines against the same volume/prefix
concurrently (OFFSITE-DESIGN §5). Each Mac keeps a **separate cache dir per
volume/mount**; caches are never shared and never hand-edited while mounted.

## 5. Client architecture on macOS

- **Phase 0 (no kernel change):** FUSE mounts live inside the Lima Linux
  guest (proven pattern), exported to macOS via the VM shared-dir mechanism.
  Throughput/latency must pass the acceptance gate (§8) before any worktree
  depends on it. The existing isolated-`LIMA_HOME` layout is kept.
- **Native Mac track:** root-cause the macFUSE kext load failure first; no
  production dependence on macFUSE until `kmutil` shows it loaded and the Git
  matrix (§8) passes on it.
- **Blob path (no POSIX needed):** JuiceFS S3 gateway for models/evidence;
  the native CLI speaks S3 for these classes and skips POSIX entirely.
- Cache placement: dedicated directory (later dedicated APFS volume or
  measured external NVMe) with a size cap **and** a min-free-space eviction
  target. Critical reads use `--verify-cache-checksum full` (measurement 5).

## 6. Metadata and durability (mandatory, from trials)

- Engine path: SQLite on a single dev host today → server meta
  (PostgreSQL/Redis/TiKV-class) when going multi-host. Pin volume UUIDs;
  keep config backups; metadata DBs and dumps contain credentials.
- Writeback defaults OFF for acknowledged data. ON only for declared
  scratch with a live staging monitor and explicit, recorded loss
  acceptance — a successful `fsync` is not proof of B2 durability
  (measurement 5).
- Snapshot barrier before every metadata dump or restic run: stop writers
  (agents, Git maintenance, builds, editors) → verify staged blocks = 0 and
  bytes = 0 → `juicefs dump --keep-secret-key` → age-encrypt to ≥2
  independently held identities **before leaving the host** → upload one
  immutable generation → independent read-back + decrypt + manifest check
  (tool version, timestamp, volume UUID, digests, barrier evidence).
- Restic tier for Git worktrees per `LOCAL-BACKUP-DESIGN.md`: quiescent
  source, `check --read-data`, periodic clean-machine restore,
  `worktree repair` on restore, retention/deletion credentials separate from
  runtime write credentials.
- B2: S3-compatible endpoint; production needs an independently controlled
  backup bucket/account, scoped keys, and off-host key escrow. Version
  accumulation (`hard_delete=false`-class behavior) needs a lifecycle/retention
  decision before automation — backups must not silently outgrow the data.

## 7. itonami-agent CLI integration

- New native routes: `itonami-agent fs status|mount|evict|pin|snapshot`.
  `status` shows tiers, staging backlog, backup age, and free space —
  the disk-full early warning this task was missing.
- `itonami.edn` gains `:storage {:backend :awai-fs …}`. It is itonami-agent
  only; Hermes skips the file, exactly like `:inference` today.
- A `storage-verdict` check (same shape as `profile-verdict` in
  `kotoba/itonami/rail.kotoba`) rejects unknown backends. Behavior change to
  the store layout follows the Hermes-compatibility contract: format changes
  ship together with the reader.
- `evict <path>` removes only local cached copies after verifying the B2
  object (same semantics as the current rclone `evict`); deleting at the
  mount performs remote deletion.

## 8. Disk-full playbook (structure, not just space)

Immediate relief (measure before deleting; never delete live caches or
metadata to free space):
1. Trim the rclone VFS cache and confirm the dead NFS mount is not pinning it.
2. Reclaim Lima clone disks, restic restore targets, and trial scratch whose
   manifests are verified (`RESULTS.md` logs what is retained and why).
3. Migrate `docs/evidence`-class bulk to the warm tier first — it dominates
   per-worktree size.

Structural (this is the actual fix):
- New worktrees default to sparse checkout excluding `docs/evidence`
  (`git sparse-checkout set --no-cone '/*' '!/docs/evidence/'`).
- Retire flow: restic snapshot → `check --read-data` → verify → local
  delete. A snapshot whose verification failed is not a retirement.
- Model weights live warm with pin-on-use; `cron/output` and usage logs
  rotate to cold.
- Guardrails: free-space watermark with automatic cache eviction,
  per-profile quotas, `fs status` in the daily loop.
- Never replace a missing mount with an empty local directory (silent
  divergence); a missing mount is a hard error.

## 9. Scale path

Single Mac (SQLite meta, §4 volumes) → more volumes per domain →
shared meta engine for the agent fleet → Awai nodes mount read-mostly with
per-agent write prefixes and B2-lifecycle GC. Multi-host adds fencing and
quota enforcement; the single-host durability rules above do not relax.

## 10. Acceptance gates (including negative controls)

- Mac access matrix on the chosen path: 22,798-file checkout + clean
  `status` timeboxes; symlink, `flock`/lockf contention, npm install,
  `go build/test`, `git fsck` — all must pass where POSIX is claimed.
- Abrupt-stop loss test per new writeback config; corrupt-cache read-back
  with full verify; EIO-on-outage behavior documented, not assumed.
- Restic `check --read-data` + `restore --verify` on schedule; offsite
  restore on an independent host; metadata dump→load round-trip with
  external manifest match.
- Every timing is 3-run + p95 before any SLO claim; single runs are
  observations, not qualifications.

## 11. Open blockers / owner decisions

1. macFUSE kext root cause (fix vs. abandon native Mac FUSE).
2. Independent backup bucket/account + two off-host recovery identities.
3. RPO/RTO targets and snapshot schedule.
4. B2 key scope (S3-compatible vs native) and lifecycle/retention policy.
5. External NVMe identity and measurement (no NVMe claim until measured).
6. Meta engine choice for the fleet step.
7. Dead rclone NFS volume: repair or retire (do not stack Awai Object Storage on it).

## 12. Rollout

- **P0:** disk relief (§8, first half); this doc lands; read-only
  `fs status` native route.
- **P1:** warm tier for evidence/models/caches via guest-FUSE export or S3
  gateway; barrier-gated snapshot automation.
- **P2:** native Mac access (macFUSE fixed or chosen alternative);
  per-profile quotas; multi-host read.
- **P3:** fleet meta; Awai Object Storage as the default store backend.

## References

- `workspaces/codex/jun-964-juicefs/RESULTS.md`,
  `LOCAL-BACKUP-DESIGN.md`, `OFFSITE-DESIGN.md` (measurements + backup rules)
- `workspaces/codex/b2-261023-github/README.md` (current rclone mount)
- itonami-agent `README.md` (Two CLIs), `AGENTS.md` (selfhost + Otehon rules)
- Linear: JUN-964 (JuiceFS/restic trials), AWAI-60 (kbb removal),
  AWAI-131 (profile-aware host), AWAI-160/161, JUN-1008 (Otehon)
