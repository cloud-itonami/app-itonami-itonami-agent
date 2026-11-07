# Otehon .cljc core — measured runs, 2026-11-07

Linear: JUN-1008. Path: Otehon / oracle (ADR 0026 D18). Not a Native gate.
Host: macOS (Darwin 25.4.0, arm64).

## Tools

| runner | version | how obtained |
|---|---|---|
| Basilisp | 0.5.1 on CPython 3.14.7 | `uv tool install basilisp` (2026-11-07; PyPI, basilisp 0.5.1 + deps) |
| Babashka | 1.12.218 | already installed (`~/.local/bin/bb`) |
| nbb | 1.4.208 on node v26.0.0 | already installed (Homebrew) |

CI pins the same Basilisp (pipx), Babashka (release tarball, sha256
`7bd028cc794732ffde3da31ce4379840893c8e54f1046f92a8dfc4f4b3cddaf8`, read
from the release's `.sha256` asset) and nbb (npm) versions.

## Shared cases

```
otehon/bin/run-shared-cases basilisp bb nbb
```

```
PASS  basilisp cases: {:cases 40, :fail [], :negative-controls 24, :pass 40, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 4/4 reported FAIL
PASS  bb cases: {:cases 40, :fail [], :negative-controls 24, :pass 40, :runner "babashka 1.12.218"}
PASS  bb must-fail: 4/4 reported FAIL
PASS  nbb cases: {:cases 40, :fail [], :negative-controls 24, :pass 40, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 4/4 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
exit 0
```

Inputs: `otehon/test/cases.edn`, `otehon/test/must-fail.edn`,
`otehon/test/fixtures/{itonami-ok,hermes-ok}`.

## Code mutations (negative control on the core)

Each mutation was applied to a copy of `otehon/` and the shared cases run on
all three runners. The unmutated suite passes 40/40.

| mutation | failing cases (same on basilisp, bb, nbb) |
|---|---|
| `yakuwari/decide`: unlisted capability → `:autonomous` | `:decide/unlisted-fails-closed`, `:decide/itonami-edn-grants-nothing`, `:neg/decide-unlisted-after-removal` |
| `manifest/validate`: drop the `:deny-hosts` openrouter rule | `:neg/deny-hosts-without-openrouter`, `:neg/dry-run-invalid-profile` |

## Live profiles (read-only)

`check` on every profile under `~/.itonami/profiles/` and the repo's
`hermes/profiles/`, plus three dry-runs of henshu's `henshu-tick`, on each
runner. The three outputs are byte-identical (sha256 of the bb output
`7bd356f92bb4d4e766b03f0c69aded4cdab79bca7afd343eee8c35b20069bfa4`).

| profile | result |
|---|---|
| `~/.itonami/profiles/` awai-growth-* (7) | errors 0, warnings 0 |
| `~/.itonami/profiles/henshu` | errors 0, warnings 1 (`itonami.edn :capabilities` is `[]`, not the copy of yakuwari.edn) |
| repo `hermes/profiles/` awai-growth-* (7) | errors 0, warnings 0 |
| repo `hermes/profiles/awai-op-approver` | 1 error: `:agent/kind :approver` is not one of `:organism :role-bot :steward :unit :worker` |
| repo `hermes/profiles/itonami-compat` | 6 errors: itonami.edn present but config.yaml rides openrouter (`rail/denied-host openrouter.ai`, `rail/itonami-off-murakumo` for `openrouter` and `xiaomi/mimo-v2.6-pro`); `:bundle :cid`, `:bundle :files`, `:placement :order` missing |

henshu dry-run of `henshu-tick` (job `4d1f6a9c0b21`, `17 */6 * * *`):

| capabilities named | status | exit |
|---|---|---|
| `fetch_public_feed analyze_source write_workspace` | `:ok`; inference murakumo / mishima at `api.murakumo.cloud`, `rail/ok`; toolsets `terminal file`; plan run-script `collect_feeds.py` → build-prompt → inference → save-output, none executed | 0 |
| `publish_external` | `:held` `authority/needs-owner` | 2 |
| `enable_cron` | `:refused` `authority/blocked` | 1 |

The two repository findings match what `manifest.cljk` (same kinds set) and
the Two CLIs rule state; they are reported, not changed, in this work.

## Basilisp portability notes found while porting

- A regex literal `#"\\(.)"` reads as `\(.)` (Basilisp folds `\\`); use `\x5c`.
- Missing: `clojure.string/trimr`, `array-map`, `sorted-map`.
- `basilisp run -n <ns>` loads a namespace but does not call `-main`; the
  CLI calls it under `#?(:lpy (when (= (str *main-ns*) …)))`.
- An alias named `io` cannot be used for a project namespace (`can't
  identify aliased form`); the CLI uses `hio`.
- `py->lisp` takes `:keywordize-keys false` as plain arguments, not `**`.
