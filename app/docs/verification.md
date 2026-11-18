# Verification — 2026-11-18 (AWAI-3327)

Source, browser, Android VM and physical acceptance are separate claims.

- **Tests.** `npm test` (ClojureScript 1.12.42 on Node 26.0.0): 4 tests, 9 assertions, 0 failures. `npm run test:bb` (Babashka) on the same `.cljc` tests: 4 tests, 9 assertions, 0 failures.
- **Parity with the Otehon CLI.** Error/warning counts shown by the app equal `otehon/bin/itonami-otehon bb check itonami/profiles/<p>` for `awai-op-approver` (1/0), `itonami-compat` (4/1) and `awai-banking-ops` (0/0). Other profiles were not compared one by one.
- **Browser.** `dist/` served from 127.0.0.1: 14 profiles, 12 usable; at 375px wide the page does not scroll horizontally (scrollWidth 375). The job table scrolls inside its card.
- **Android build.** Tauri 3.0.0-alpha.6, minSdk 35 / target 37, NDK 27.0.12077973, `--debug --apk --target aarch64`. Debug APK 29.6 MB (first build). Final APK SHA-256 `b29af6e498179b5c4736f684be6a6f6ca7fe0ccd4af4005f92ac855ffa8ff666` (debug build, not reproducible-checked).
- **Defect found and fixed.** The first APK rendered blank: `cljs.main -c` injected the browser-REPL preload, which tried to frame `http://localhost:9000/` and was refused by the CSP. `:browser-repl false` in `build.edn` removed it (`localhost:9000` occurrences in `dist/app.js`: 0).
- **Android VM.** A dedicated API 35 arm64 `default` image AVD (`ro.build.version.sdk` = 35): install `Success`, `cloud.itonami.agent/.MainActivity` resumed, DOM read over the WebView DevTools socket: `https://tauri.localhost/`, 14 cards, summary `14 profiles · 12 usable`, no error text, scrollWidth 412 = innerWidth 412. Screenshot: `preview-android.png`. Another session installed and foregrounded its own app on the same emulator during the run; it did not change this app's result.
- **Open.** Physical Awai OS, signing, OTA, reboot persistence, TalkBack and user acceptance are not verified. Live turns/inference are not part of the app.
