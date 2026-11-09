# Kotoba CLI host eval on this Mac — 2026-11-09

Slice B of awai-os-agent-placement: evaluate `kotoba` CLI as the agent
host on the macOS side (guest access still blocked; see
awai-os-agent-placement-20261109.md).

## Setup

- Installed pinned release `v0.7.3` via repo `install.sh`
  (`KOTOBA_VERSION=v0.7.3`): `~/.local/share/kotoba/v0.7.3`,
  shim at `~/.local/bin/kotoba`. Checksum verified by installer.
- `kotoba check --kind cli-contract --json` → valid, 12 commands.

## Run

```
kotoba rad new --project hello   # scaffold to /tmp/koeval/hello
kotoba run src/hello.kotoba --json
```

`src/hello.kotoba` is `(ns hello) (defn main [] (+ 40 2))`.
Result: `:run/completed`, `kotoba.runtime/value: 42`, checked IR
`kotoba.runtime.edn-ir.v0`, 1 fn. Source → checked IR → value works
end to end on darwin-arm64 with the release binary.

## Notes

- `bin/kotoba` in-repo launcher falls through to `kotoba-clj` (JVM);
  not used per repo AGENTS.md. Release binary used instead.
-encountered: `--version` / `--help` are not commands (EDN
  `:command/unknown`); command surface is in `lang/cli.edn`.
- Next: try `kotoba run` against itonami-agent `kotoba/itonami/*.kotoba`
  pure modules (adapter/builtins gap unknown); native `kexe` path still
  needs Amu (2.1 GB source, no releases).
