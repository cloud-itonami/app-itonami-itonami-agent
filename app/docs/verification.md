# Verification — 2026-11-18 (AWAI-3327)

Source, browser, Android VM and physical acceptance are separate claims.

- **Tests.** `npm test` (ClojureScript 1.12.42 on Node 26.0.0): 4 tests, 9 assertions, 0 failures. `npm run test:bb` (Babashka) on the same `.cljc` tests: 4 tests, 9 assertions, 0 failures.
- **Parity with the Otehon CLI.** Error/warning counts shown by the app equal `otehon/bin/itonami-otehon bb check itonami/profiles/<p>` for `awai-op-approver` (1/0), `itonami-compat` (4/1) and `awai-banking-ops` (0/0). Other profiles were not compared one by one.
- **Browser.** `dist/` served from 127.0.0.1: 14 profiles, 12 usable; at 375px wide the page does not scroll horizontally (scrollWidth 375). The job table scrolls inside its card.
- **Android build.** Tauri 3.0.0-alpha.6, minSdk 35 / target 37, NDK 27.0.12077973, `--debug --apk --target aarch64`. Debug APK 29.6 MB (first build). Final APK SHA-256 `b29af6e498179b5c4736f684be6a6f6ca7fe0ccd4af4005f92ac855ffa8ff666` (debug build, not reproducible-checked).
- **Defect found and fixed.** The first APK rendered blank: `cljs.main -c` injected the browser-REPL preload, which tried to frame `http://localhost:9000/` and was refused by the CSP. `:browser-repl false` in `build.edn` removed it (`localhost:9000` occurrences in `dist/app.js`: 0).
- **Android VM.** A dedicated API 35 arm64 `default` image AVD (`ro.build.version.sdk` = 35): install `Success`, `cloud.itonami.agent/.MainActivity` resumed, DOM read over the WebView DevTools socket: `https://tauri.localhost/`, 14 cards, summary `14 profiles · 12 usable`, no error text, scrollWidth 412 = innerWidth 412. Screenshot: `preview-android.png`. Another session installed and foregrounded its own app on the same emulator during the run; it did not change this app's result.
- **Open.** Physical Awai OS, signing, OTA, reboot persistence, TalkBack and user acceptance are not verified. Live turns/inference are not part of the app.

# Verification — 2026-11-18, Itonami Desktop (AWAI-3355)

- **Tests.** ClojureScript: 10 tests, 43 assertions, 0 failures. Babashka: 11 tests, 52 assertions, 0 failures, including `parity_test.clj`: nine inputs, refusals included, give the same path and body from `itonami.app.chat/plan` and Otehon `conversation/request-plan`. The chat tests include negative controls: bad bot or session ids, `default` as a request id, blank or 8193-character text, an untitled or 121-character session title, and replies that were not read back or that name a different request, bot or provider.
- **Broker.** `vendor/` holds kotoba-lang/kotoba `18b8698c475c` `kotoba-client-runtime.js` (`de5e684e…fac6`) and `kotoba-client-consent.css` (`eb1caab0…b086`). `npm run build` checks both digests. KOTOBA-159, the independent review of this source, was still in progress.
- **Browser (127.0.0.1).** Sending opened the canonical consent dialog for send-message, which named the endpoint, route and data categories. After Deny the transcript was empty, the draft was restored and the request log had no `ts.net` request. Desktop width 1280: sidebar and bot detail with 2 routines, no overflow.
- **Android (API 35 arm64, own AVD).** APK `7f5c3ba9…7fbd5` (45.3 MB debug). Install and launch succeeded: 17 bundled bots, no overflow at 412 px, drawer hidden when closed.
  - The emulator cannot use MagicDNS, so its `/system/etc/hosts` maps the service name to the tailnet IP (writable-system AVD). This Mac's own DNS also returns public IPs for that name; that is not changed here.
  - With "Allow once" (pressed by a trusted tap):
    - list-bots returned 797 bots ("on Agent").
    - read-history for `hiraku-soudan` returned an empty default session.
    - list-sessions returned no sessions besides the default.
    - read-history for `awai-banking-ops` returned 404, because that bot is not in the Agent catalog. The app now says so.
  - A denied send: no message was sent, the draft was restored, and the notice said "Not sent".
- **Not verified.** A live send, which runs Muse inference and writes the owner's history, was not exercised. Session creation (a write) was not exercised either. Also not verified: a physical Awai OS device on the tailnet, signing, OTA and TalkBack.
- **Interference.** During this run another session's ADB forward reused local port 9334 for its own emulator. CDP moved to port 47391 and every reading above is from this app's WebView.
