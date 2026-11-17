# awai-growth-x402-adoption: profile check, 2026-11-18

Linear: AWAI-3251. One-off local developer oracle; not a CI gate or runtime path.

## Commands (run from the repository root)

```
kbb --backend sci --classpath src docs/evidence/awai-growth-20261031/profile-reference-oracle.cljk
kbb --backend sci --classpath src:test run-tests.cljk
```

- kbb: `/opt/homebrew/bin/kbb` (no `--version` option; version unknown), Node v26.0.0, macOS. Base revision: `main` 5046287.
- Oracle source: `docs/evidence/awai-growth-20261031/profile-reference-oracle.cljk` (sha256 `7c6163e7…66cd9`), unchanged. It reads every member of `docs/awai-growth-team.json`.

## Results

- `profile-reference-result.json`: all 9 members `ok: true`, including `awai-growth-x402-adoption`: no problems or drift, 8 bundle files, policy equals the declared capabilities, no cron jobs, no exposed tools, and the negative controls (openrouter provider, wrong bundle CID, unrestricted tools) are detected.
- Bundle CID `bafkreied6j7pyqqnowo72spxjdhmp5aiq7jb326adkhuup6pg75bh3krwy` was computed with `itonami.agent.profile/bundle-cid` over `bundle-files` with `{:dirs ["tasks" "policy"]}`. The same script reproduced the recorded CID of `awai-growth-isekai-adoption` (`bafkreicm3n…cmspm`).
- `run-tests.cljk`: 316 tests, 2024 assertions, 5 failures, all in `dapps-test/servers-resolve-from-placeholders`. PR #1 recorded the same 5 failures on unmodified `main` 06539ad; this change does not touch that code. Unmodified 5046287 was not re-run here (host load average about 100).

## Relation to cloud-itonami/loop-x402-qr-growth

The two coexist. `loop-x402-qr-growth` (c14780c) is a repository profile for QR merchant pilot growth; its `data/runtime.edn` records admission accepted but not provisioned or scheduled. This profile is the bot side of the 1:1 pair with the x402.nexus Tauri v3 Awai app (AWAI-3252) and drafts a single Base Sepolia payment trial contract. Neither replaces the other.

## Not verified

Native profile host admission, a live inference turn, the paired Tauri v3 Awai app (AWAI-3252), a registered business bot profile for x402.nexus (none found in the business profile index), and any x402 payment request, verification or settlement.
