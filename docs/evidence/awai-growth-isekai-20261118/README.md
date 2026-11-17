# awai-growth-isekai-adoption: profile check, 2026-11-18

Linear: AWAI-3215. One-off local developer oracle; not a CI gate or runtime path.

## Commands (run from the repository root)

```
kbb --backend sci --classpath src docs/evidence/awai-growth-20261031/profile-reference-oracle.cljk
kbb --backend sci --classpath src:test run-tests.cljk
```

- kbb: `kotoba-lang/kotoba/bin/kbb` launcher (it has no `--version` option; version unknown), Node v26.0.0, macOS.
- Oracle source: `docs/evidence/awai-growth-20261031/profile-reference-oracle.cljk` (sha256 `7c6163e7…66cd9`), unchanged. It reads every member of `docs/awai-growth-team.json`.

## Results

- `profile-reference-result.json`: all 8 members `ok: true`, including `awai-growth-isekai-adoption`: no problems or drift, 8 bundle files, policy equals the declared capabilities, no cron jobs, no exposed tools, and the negative controls (openrouter provider, wrong bundle CID, unrestricted tools) are detected.
- Bundle CID `bafkreicm3nb6uycppgon6qpubbf5zjlgoqipfgulsux5jbrpsnrohcmspm` was computed with `itonami.agent.profile/bundle-cid` over `{:dirs ["tasks" "policy"]}`. The same method reproduces the recorded CID of `awai-growth-yataverse-adoption` (`bafkreidm2c…wd2oi`).
- `run-tests.cljk`: 316 tests, 2024 assertions, 5 failures, all in `dapps-test/servers-resolve-from-placeholders` (`MCP server timed out` for the fake dandori/mihari servers). The same 5 failures occur on unmodified `main` (06539ad); this change does not touch that code.

## Not verified

Native profile host admission, a live inference turn, the paired Tauri v3 Awai app (AWAI-3216) and any Isekai publication, gem mint or x402 payment.
