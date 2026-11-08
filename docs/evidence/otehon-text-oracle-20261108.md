# itonami Otehon text oracle — measured runs, 2026-11-08

Linear: JUN-1008 (append requested; body unopened from this host — no Linear
CLI/token here). Path: Otehon / oracle (ADR 0026 D18). Not a Native gate,
build, packaging, or fallback. Host: macOS (Darwin, arm64).

## Reported error and root cause

Reported (`kbb -e`, `kbb --version`, any `kbb` door taking an absolute
script path; Node v26.0.0):

```
Could not find namespace: kotoba.lang.text
    at .../org-babashka-nbb/lib/nbb_main.js:13:216
Node.js v26.0.0
```

Cause, pinned by reproduction on this host:

1. `kotoba.lang.text` lives only as `.cljk`
   (`kotoba-lang/text/src/kotoba/lang/text.cljk`,
   `kotoba-lang/kotoba/lib/kotoba.text/src/kotoba/lang/text.cljk`).
   Stock `nbb` probes `.cljs .cljc .clj` and never finds it; only the
   `kotoba-lang/org-babashka-nbb` fork (SCI with `.cljk` resolution) can.
2. The fork finds it through `nbb.edn` (`:deps` → local/root `lib/kotoba.text`
   → `.nbb/.cache/…/nbb-deps` containing `kotoba/lang/text.cljk`).
   `src/nbb/api.cljs` `resolve-nbb-edn` locates that `nbb.edn` by walking up
   from the script path — but it tests directories with `fs/lstatSync`
   (no symlink follow). `/Users/junkawasaki/github` resolves through a
   juicefs symlink, so an **absolute** script path
   (`/Users/…/kotoba/bin/kbb_shim.cljk`, exactly what `bin/kbb` passes as
   `$SCRIPT_DIR/…`) never finds `nbb.edn` and falls back to cwd-only
   classpath: no `:paths`, no `:deps` cache, no `kotoba.lang.text`.
   A **relative** script path (`bin/kbb_shim.cljk`) resolves through the
   juicefs real path, finds `nbb.edn`, and loads. Measured both ways on this
   host (same cwd, same file, same engine — absolute fails, relative works).
3. `bin/kbb`'s `--backend sci` door sets `KBB_CLASSPATH_TAIL` (with
   `text/src`) and works; the `-M/-e/script` deps door and the final shim
   door set no tail, so they fail on absolute paths. That is why
   `itonami-agent-kbb` (`--backend sci`) works while plain `kbb -e` fails.

Fix (this change, itonami-agent side): stop depending on the `.cljk`-only
namespace from the `.cljc` core. New oracle module
`otehon/src/itonami/otehon/text.cljc` ports the needed string ops to
portable `.cljc` (JVM whitespace pinned, Java split trailing semantics;
regex matching itself stays host regex, as in `kotoba.lang.text`). The
engine `lstatSync`→`statSync` and `bin/kbb` tail fixes are recorded here as
the upstream root cause; they live in `org-babashka-nbb` / `kotoba` and are
not edited in this unit.

Reference oracle rule (workspace): one-off measurement, recorded below;
never a required build, CI, runtime or fallback path. `text.cljc` itself is
Otehon-path code (oracle path, ADR 0026 D18), not Native-path code.

Reference implementation read for the port (hand, this session):
`kotoba-lang/text/src/kotoba/lang/text.cljk` (905 lines; `match-spans`,
`split`/`split-lines`, `java-whitespace?`, `trim`/`triml`/`trimr`,
`starts-with?`/`ends-with?`/`includes?`, `index-of`, `lower`, `join`,
`replace`). Ported subset: `trim`, `triml`, `trimr`, `blank?`,
`starts-with?`, `ends-with?`, `includes?`, `index-of`, `lower-case`
(+ `lower` alias), `join`, `split` (string sep portable; regex sep host +
pinned trailing), `split-lines` (no regex), `replace` (string match
portable literal; regex match host).

## Tools

| runner | version | how obtained |
|---|---|---|
| Basilisp | 0.5.1 | `~/.local/bin/basilisp` (existing) |
| Babashka | 1.12.218 | `bb --version` (existing) |
| nbb | 1.4.208 on node v26.0.0 | `nbb --version`, `node --version` (Homebrew) |

Same pins as `.github/workflows/oracle-otehon-cljc.yml`.

## What changed

- New: `otehon/src/itonami/otehon/text.cljc` (portable oracle above; no
  `#?@`, no `.charAt`, no `str/trimr`, no `array-map`, no `\\\\` in regex
  literals, no `\\u` escapes — whitespace built from code points via `char`).
- `otehon/src/itonami/otehon/cli.cljc`: require `itonami.otehon.text`,
  new `:text` shared-case op (`:call [fn & args]` → `{:value …}`).
- `otehon/test/cases.edn`: 60 → 78 cases (18 new `:text/*` cases, incl.
  NBSP-kept `trim`/`blank?` and trailing-empty-dropped `split`); header
  comment updated (`:text` listed).
- `otehon/README.md`: layout table + checks counts + portability notes.

## Shared cases

```
otehon/bin/run-shared-cases basilisp bb nbb
```

```
PASS  basilisp cases: {:cases 78, :fail [], :negative-controls 27, :pass 78, :runner "basilisp 0.5.1 / python 3.14.7"}
PASS  basilisp must-fail: 5/5 reported FAIL
PASS  bb cases: {:cases 78, :fail [], :negative-controls 27, :pass 78, :runner "babashka 1.12.218"}
PASS  bb must-fail: 5/5 reported FAIL
PASS  nbb cases: {:cases 78, :fail [], :negative-controls 27, :pass 78, :runner "nbb on node v26.0.0"}
PASS  nbb must-fail: 5/5 reported FAIL
PASS  bb output identical to basilisp
PASS  nbb output identical to basilisp
exit 0
```

Inputs: `otehon/test/cases.edn`, `otehon/test/must-fail.edn`,
`otehon/test/fixtures/{itonami-ok,hermes-ok}`.

## Code mutation (negative control on the new oracle)

Throwaway copy of `otehon/` (`160`/NBSP added to the pinned trim set in
`text.cljc`), then `bin/itonami-otehon bb test test/cases.edn`:

```
:text/trim-keeps-nbsp FAIL
:text/blank-nbsp-false FAIL
{:cases 78, :fail [trim-keeps-nbsp blank-nbsp-false], :pass 76}
```

Only the two NBSP-pinned cases fail; the other 76 still pass. The oracle
cases can tell a broken trim from a working one.

## Remaining

Upstream (not this unit): `org-babashka-nbb/src/nbb/api.cljs`
`resolve-nbb-edn` `lstatSync`→`statSync` (symlinked dirs), and `kotoba/bin/kbb`
stdlib tail for the deps/shim doors (today only the `sci` door sets it).
Otehon call sites still require `clojure.string`; switching them to
`itonami.otehon.text` is a follow-up, not this oracle unit.
