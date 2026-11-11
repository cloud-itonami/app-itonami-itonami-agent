# Verification record — S6c-4 chat run options — 2026-11-12

JUN-1036 S6c-4: the fourth S6c slice — the data half of the chat
command's run options as a `run-opts` plan in `chat.cljc`,
verbatim from `run-opts` in main.cljk. The plan carries model,
provider, the model pin flag, toolsets, the `cli` platform, the
load-context flag, an explicit cwd and join-children. The `:chat`
verb `run-opts` wired through the existing `ops-chat`. 4 shared
cases + 2 must-fail controls; 1043 cases + 129 must-fail green on
basilisp 0.5.1 / bb 1.12.218 / nbb, canon byte-identical.

## Files (this slice only; earlier slices alongside untouched)

- `otehon/src/itonami/otehon/chat.cljc`: + `run-opts`.
- `otehon/src/itonami/otehon/cli.cljc`: `:chat` dispatch grows the
  `run-opts` verb.
- `otehon/COMPAT.md`: S6c-4 entry + chat table row updated.
- `test/cases.edn` +4, `test/must-fail.edn` +2.

## Portability notes

- No interop: plain map reads and `boolean` only — no `:lpy` branch
  needed.
- Gate detection: the first draft passed string-keyed option maps
  and the gate rejected the three non-trivial cases identically on
  every runner — the legacy `parse-args` builds keyword-keyed opts,
  and the twin reads keywords. The cases now pin the keyword
  contract.
- Verified divergence: none in the data half. The `cronjob_manage`
  tool override, the verbose event logger, the executor, the profile
  load and the cwd default stay host-side; a verbose run plans
  exactly the same data (pinned by case).

## Runs

- bb 1.12.218: 1043 cases, 0 fail; must-fail 129/129 FAIL (as designed).
- nbb (node v26.3.0): 1043 cases, 0 fail; must-fail 129/129 FAIL.
- basilisp 0.5.1: 1043 cases, 0 fail; must-fail 129/129 FAIL.
- Canon byte-identical across the three runners.
