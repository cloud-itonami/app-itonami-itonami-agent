# Otehon portable `.cljc` constraints

Basilisp / Babashka / nbb must print the same answer on the shared cases
(`bin/run-shared-cases` compares byte for byte). The constraints below were
found by breaking each one during the `yaml.cljc`, `text.cljc`
(`itonami.otehon.text`) and `kotoba.lang.text` (`.cljc` oracle) ports. Each
entry names the failure and the form that works on all three runners.

## Reader and literal level

- No `#?@`. The Kotoba reader does not accept it.
- No `\\u` escapes anywhere: not in strings, regex literals, or char
  literals. Basilisp rejects them at read time. Build non-ASCII characters
  from code points via `char` (e.g. `(set (map char [160 8192 ...]))`).
- No `\\\\` inside regex literals. Basilisp folds it to one backslash.
  Write a literal backslash as `\\x5c`.
- No `\0` char literal. Pad and compare with `"0"` / `" "` strings.
- Otherwise ordinary char literals (`\a`, `\#`, `\"`, `\n`, `\t`, `\\`,
  `\%`) and regex literals (`\s`, `\r?\n`, `\x5c`) read fine everywhere.

## Namespace level

- No `{:kotoba/export [...]}` (or any unknown) ns metadata. Basilisp
  rejects it at load with an inlining error. Oracle twins drop it and say
  so in the docstring.
- `#?` branches without a matching feature are skipped, not an error — but
  every `#?` in a portable file must still carry an `:lpy` branch, or
  Basilisp silently drops the form. Prefer branch-free portable loops
  (`subs`/`count`/`nth` comparisons) where values are identical anyway.

## Interop level (`:lpy` branches)

- Case folding: `(.upper s)` / `(.lower s)`, not `.toUpperCase`.
- Code points: `(ord ch)` and `(chr n)` builtins, not `.charCodeAt`.
- Java `Long/parseLong` / JS `js/parseInt`: `parse-long`.
- Java `Long/toHexString` / `toOctalString`: `(python/format n "x")`
  (negative inputs follow the host conversion; the measured corpus only
  renders non-negative values).
- `re-find` / `re-matches` / `re-seq` inside your own namespace must be
  `basilisp.core/`-qualified in the `:lpy` branch, or they resolve to the
  function being defined (infinite recursion).
- Regex matching: `(.finditer pattern s)` wrapped in `iterator-seq`
  (a bare finditer object is single-use and rejected). `.start` / `.end` /
  `(.group m i)` / `.groups` (Python `None` reads as `nil`) give spans.
  Zero-width matches already advance one step, as on the JVM.
- `re-matcher` / `.find` / `.groupCount` / `js/RegExp` / `.-flags` /
  `.-lastIndex` / `Math/*` have no Basilisp form. Port the loop instead.

## Verified portable core (no branches needed)

`nth` / `subs` / `count` / `str` / `=` on strings and char literals,
transients (`transient` / `conj!` / `persistent!`), `rseq`, `re-find` /
`re-matches` / `re-seq` with literals, `parse-long`, `quot` / `mod` / `long` /
`double`, `char`, `ord` on `(nth s i)`, `map` / `mapv` over strings,
`ex-info` / `ex-data`, `iterator-seq`.

## Oracle placement standard

- A `.cljc` oracle is a twin next to its `.cljk` (same directory, same
  base name). It is never referenced by `deps.edn` / `nbb.edn` / `bb.edn`,
  never wired into CI gates, builds, packaging, or runtime paths, and never
  a fallback. One-off measurement only.
- Each measurement records the command, tool versions, inputs, and outputs
  with a date (see `docs/evidence/`), plus a negative control (a mutation
  the shared cases must catch).
- A `.cljk` passed to JVM / nbb / Basilisp as an oracle is passed as its
  `.cljc` twin. Divergences found by measurement are reported, not silently
  aligned.
