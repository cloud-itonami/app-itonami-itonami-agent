# Itonami notepad-core Amu selfhost gate — 2026-11-01

This gate qualifies the existing pure `itonami.notepad-core` Kotoba module
through Amu's locked multi-module project compiler and native artifact runner.
It exercises the module's key/value validation, entry codec, and hex encoding
self-checks. It does not exercise filesystem effects, `notepad.kotoba`, the
Itonami launcher, profile loading, or an agent turn.

## Pinned inputs

- `kotoba/compat/itonami/notepad_core.kotoba`, SHA-256
  `14ce3b4690f023f928716d5bc1ed1193898c08dc2a00cedb28e2ea2d837ebb92`.
- Amu PR #1764 candidate commit:
  `d673111b66f43a8569e545c772a70e51737e1707`.
- Amu CLI SHA-256
  `ae94dc99ffe489ef77cb5eb0044bea1d2500a4273d45c55bcf57b887994f390a`.
- Host: macOS 26.4 (25E246), arm64.

## Reproduction

From this repository worktree, provide the pinned Amu CLI and a new output
directory under `github/workspaces`:

```sh
bin/check-notepad-core-selfhost \
  /path/to/amu-cli.macho \
  /Users/junkawasaki/github/workspaces/codex/awai60-notepad-core-selfhost-<run>
```

The gate builds a temporary `main` entry that invokes all four exported
notepad-core self-checks. The source lock binds both Kotoba modules. It then
executes the resulting Mach-O directly, with `amu run` from the locked source,
and with `amu run` replaying the project artifact. Each path must return mask
`15`. Finally, it changes the staged locked source and requires compile refusal
without producing an output artifact.

## Result

The 2026-11-01 run at
`workspaces/codex/awai60-notepad-core-selfhost-final-20261101` passed:

- Direct native result: `15`.
- Locked-source `amu run` result: `15`.
- Replayed artifact `amu run` result: `15`.
- Changed locked source refusal: PASS (`EINPUT a locked project source`;
  no output artifact).
- Generated project gate SHA-256:
  `4e928aed8f35e5a4e1fb7d657bdb230230641e45c5b1f67964c1e81862cb5de0`.
- Source lock SHA-256:
  `2b8ae65ac6f1a2069aefdbc3087fe7301f24be144e3d5b711b15b7014c81f05c`.
- Native program SHA-256:
  `16e44acea6a366aa7c20cee72bb50f4f5e0ab02f785c2f60ffbd7e479438c9ec`.

This is a pure-module native selfhost result only. The project launcher and
effectful notepad adapter still need their own loaderless host integration and
capability qualification.
