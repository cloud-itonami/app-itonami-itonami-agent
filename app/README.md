# Itonami — Tauri v3 desktop app (Android / Awai OS)

Itonami Desktop, modelled on the Hermes Agent desktop's chat-first layout: a
sidebar with **Sessions** and **Bots** (a drawer on phones), the conversation,
a composer and a status bar.

- **Bots**: the profiles bundled from `../itonami/profiles`, checked on the
  device by the Otehon `.cljc` core compiled to ClojureScript, with their
  routines (cron jobs) dry-run and shown per bot. On request, the Agent's own
  directory is loaded and merged ("on Agent" / "local only").
- **Conversation**: the owner's Agent conversation service
  (`network-awai/awai-os` `otehon/bot-chat`, `/v1/bot-chat/{bots,sessions,history,turn}`,
  Muse `muse-spark-1.3`). Every request goes through the canonical
  `kotoba-client-runtime.js` broker and asks the person first; denying sends
  nothing. Nothing is retried automatically. An unconfirmed send keeps its
  request id and offers **Check result**, which resends the same id so the
  Agent replays a finished turn instead of running it again.
- The Agent owns the history; the app keeps only what is on screen.
- The Rust side is a thin host (`awai_host_info` only).

Tracking: [AWAI-3327](https://linear.app/kotobalabs/issue/AWAI-3327),
[AWAI-3355](https://linear.app/kotobalabs/issue/AWAI-3355).

**Path status.** This is the Otehon path (ADR 0026 D18, workspace Awai Otehon
rule): `.cljc`/CLJS for the shared domain, Tauri as a limited platform
adapter. Nothing on the Native route (`bin/`, `kotoba/`, the selfhost gates)
reads `app/`, and neither side falls back to the other.

## Layout

| path | role |
|---|---|
| `src/itonami/app/core.cljc` | pure view-model over `itonami.otehon.profile` (also runs on Babashka) |
| `src/itonami/app/chat.cljc` | request plans (same as Otehon `conversation/request-plan`, checked by `test/.../parity_test.clj`), response checks, transcript state |
| `src/itonami/app/main.cljs` | the desktop UI |
| `public/agent-client.js` | declares the five Agent operations and calls the broker; nothing else |
| `vendor/` | canonical `kotoba-client-runtime.js` / `kotoba-client-consent.css`, pinned by revision and SHA-256 in `PINS.json`; `npm run build` refuses a mismatch |
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

## Reaching the Agent

The service origin is fixed: `https://21mbp.tail110d8b.ts.net:8443`, the
owner's tailnet (Tailscale serve, owner identity, origin allowlist including
`https://tauri.localhost`). The device must be on that tailnet with MagicDNS.
The CSP `connect-src` names only that origin besides the local IPC.

## Not covered yet
- Streaming replies, tool-call rows, attachments, voice, terminal, file
  browser, skills, memory graph and multi-window from the Hermes desktop.
- Loading a profile from the device's own store (`~/.itonami` equivalent).
- Physical Awai OS, signing, OTA and reboot persistence are separate claims.
