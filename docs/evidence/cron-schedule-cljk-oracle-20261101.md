# One-time CLJK schedule parser oracle — 2026-11-01

This is a local reference measurement for schedule parser behavior. It is not a
CI/build/runtime dependency and does not prove Kotoba parity.

## Source and tools

- Itonami `src/itonami/agent/cron.cljk` source: commit
  `2eb36c00cc21c02ede813719b3fccf8d3f164e2c`.
- Kotoba `kotoba/itonami/schedule.kotoba` under review: commit
  `da20ba9c920bea32e1c8481692ab33d92dcb5438`.
- Oracle command: `kbb --backend sci --classpath src:test -e ...`.
- Local wrapper: `kotoba 0.6.29`; its REPL reported NBB
  `1.5.212-cljk.1`; Node `v26.0.0`.
- Measurement date: 2026-11-01 UTC.

## Inputs and observed outputs

1. Input UTF-8 string ` 3h ` (U+00A0 NO-BREAK SPACE at both ends), SHA-256
   `3ff353bcfb6437f0debcdaf7d276683acfda2cd789418d1e741deb31194d404e`.
   Observed `cron/parse-schedule` output:
   `{"kind" "interval", "minutes" 180, "display" "every 180m"}`.
2. Input UTF-8 string `monday, mon at 9am`, SHA-256
   `745202ffb40e8d7da7616152f1adef28e3ff50b3935a5a82b8d927c6c108ed29`.
   Observed output:
   `{"kind" "cron", "expr" "0 9 * * 1", "display" "monday, mon at 9am"}`.

The command loaded the repository CLJK module and invoked only the pure
`parse-schedule` function. The Kotoba module has an Amu compile/extract receipt
in `cron-schedule-cli-native-compile-20261101.md`, but its guest was not run in
this environment; parity for these particular inputs remains unverified.
