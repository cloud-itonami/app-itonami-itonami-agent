# Itonami Agent — Tauri v3 app (Android / Awai OS)

The itonami agent as an installable app. The Otehon `.cljc` core
(`../otehon/src`) is compiled to ClojureScript and runs inside the WebView on
the device: it reads the profiles bundled from `../itonami/profiles`, runs
`check` on each one and dry-runs every cron job. The Rust side is a thin host
(`awai_host_info` only). No inference is called, no file is written and no
request leaves the device. Tracking: [AWAI-3327](https://linear.app/kotobalabs/issue/AWAI-3327).

**Path status.** This is the Otehon path (ADR 0026 D18, workspace Awai Otehon
rule): `.cljc`/CLJS for the shared domain, Tauri as a limited platform
adapter. Nothing on the Native route (`bin/`, `kotoba/`, the selfhost gates)
reads `app/`, and neither side falls back to the other.

## Layout

| path | role |
|---|---|
| `src/itonami/app/core.cljc` | pure view-model over `itonami.otehon.profile` (also runs on Babashka) |
| `src/itonami/app/main.cljs` | DOM entry: fetch `profiles.json`, render |
| `public/` | `index.html`, `style.css` |
| `scripts/bundle-profiles.mjs` | ships only `SOUL.md`, `config.yaml`, `profile.yaml`, `cron/jobs.json`, `itonami.edn`, `yakuwari.edn` per profile; refuses text that looks like a credential |
| `src-tauri/` | Tauri 3.0.0-alpha.6 host, Android project in `gen/android` (minSdk 35, target 37) |
| `icon-src/icon.svg` | source of the launcher icons (`npx tauri icon`) |

## Build

Needs Node, the Clojure CLI (JDK 21), Rust with `aarch64-linux-android`, the
Android SDK and NDK 27.0.12077973.

```
npm ci
npm test             # ClojureScript tests on node
npm run test:bb      # the same tests on Babashka
npm run build        # dist/: index.html, style.css, app.js, profiles.json
ANDROID_HOME=… NDK_HOME=… npm run build:android      # arm64 debug APK
```

The APK lands in
`src-tauri/gen/android/app/build/outputs/apk/universal/debug/`. Install with
`adb install -r <apk>`.

## Not covered yet

- Live turns and inference. Any external operation must go through the shared
  `kotoba.app.client-runtime.v1` descriptor and `kotoba-client-runtime.js`
  broker with per-operation consent; that integration is not here.
- Loading a profile from the device's own store (`~/.itonami` equivalent).
- Physical Awai OS, signing, OTA and reboot persistence are separate claims.
