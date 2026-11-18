# awai-web-research fixtures

- `html/`: four small illustrative pages (no real site content).
- `snapshots/`: Kumade exports of those pages, made with Kumade's own `createSnapshot`
  (`network-awai/app-itonami-kumade` 443bab0, `src/core.js`) at a fixed `capturedAt`.
  `en-notes-mirror` differs from `en-notes` only by an HTML comment, so it has another
  snapshot id and the same text (a duplicate). `noindex` carries `robots: noindex`.
  `zz-private-host.json` is a hand-altered copy pointing at `http://127.0.0.1/admin`,
  which the bot must reject.
- `expected/bundle.edn` and `expected/root.txt`: the index draft and Awai root CID that
  `web-index-inbox` produced from `snapshots/`; `web-index-self-test` compares against them.
