#!/usr/bin/env python3
"""Build and qualify the Itonami notepad package and launcher through Amu selfhost.

This runner orchestrates pinned source/compiler inputs. Notepad behavior executes
inside the Amu-produced Kotoba KX64 artifact. It also invokes the candidate
package's product launcher with no legacy loader artifacts present.
"""

import argparse
import hashlib
import json
import os
import platform
import re
import shutil
import subprocess
import time
from pathlib import Path


AMU_COMMIT = "a73bd0350ecdb73abde7548f7fbb101e72b73d64"
AMU_BINARY = "bin/amu-linux-x86_64"
AMU_BINARY_SHA256 = "925fa6999fff4e48759fd1c355b32bf613e67d1497e8e8ab610c3890620f197b"
AMU_RUNNER_SOURCE = "seed/x86_64-kx64-runner.kotoba"
AMU_RUNNER_SHA256 = "c9f1fe1b5c1748450fb11b233cbdc9d9234880a60dc480c0387b173f60d905be"
AMU_PROFILE = "seed/experimental/98-x86_64-project-fs-cli.kotoba"
AMU_PROFILE_SOURCE_SHA256 = "07776b80a479f551725cd48101cfdb9e120c67454164e881ebc476a25bddef52"
AMU_PROFILE_SHA256 = "e975a3ee5308da4c69b2b1f1e7ce71024197f5e127d481aba7e27222b7108996"
AMU_PROFILE_MASK_FROM = b"bit-not 996440801408"
AMU_PROFILE_MASK_TO = b"bit-not 1013612281864"
WIRES = "3,34,35,37,38,39"
ROOT = Path(__file__).resolve().parents[1]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def run(name, argv, cwd, env, calls, timeout=900):
    started = time.monotonic()
    result = subprocess.run(list(map(str, argv)), cwd=cwd, env=env,
                            capture_output=True, timeout=timeout)
    calls.append({"name": name, "exit": result.returncode,
                  "seconds": round(time.monotonic() - started, 3),
                  "stdout_sha256": sha(result.stdout), "stderr_sha256": sha(result.stderr)})
    (Path(cwd) / f"{name}.stdout").write_bytes(result.stdout)
    (Path(cwd) / f"{name}.stderr").write_bytes(result.stderr)
    if result.returncode != 0 or result.stderr:
        raise RuntimeError(f"{name} failed: exit={result.returncode} "
                           f"stderr={result.stderr.decode(errors='replace')[-2000:]}")
    return result.stdout


def git_blob(repo, rev, path):
    return subprocess.check_output(["git", "-C", str(repo), "show", f"{rev}:{path}"])


def fetch(url, rev, repo):
    repo.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["git", "init", "--bare", str(repo)], check=True,
                   stdout=subprocess.DEVNULL)
    subprocess.run(["git", "-C", str(repo), "remote", "add", "origin", url], check=True)
    subprocess.run(["git", "-C", str(repo), "-c", "protocol.version=2", "fetch",
                    "--depth=1", "--filter=blob:none", "origin", rev], check=True)
    found = subprocess.check_output(["git", "-C", str(repo), "rev-parse", "FETCH_HEAD"]).decode().strip()
    if found != rev:
        raise RuntimeError(f"source revision mismatch: expected {rev}, got {found}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--amu-url", default="https://github.com/kotoba-lang/amu.git")
    args = parser.parse_args()
    if platform.system() != "Linux" or platform.machine() not in ("x86_64", "amd64"):
        raise SystemExit("this native gate requires Linux x86_64 to execute its pinned Amu compiler")
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    amu_repo = out / "inputs/amu"
    fetch(args.amu_url, AMU_COMMIT, amu_repo)

    binary = git_blob(amu_repo, AMU_COMMIT, AMU_BINARY)
    runner_source = git_blob(amu_repo, AMU_COMMIT, AMU_RUNNER_SOURCE)
    profile_source = git_blob(amu_repo, AMU_COMMIT, AMU_PROFILE)
    if sha(binary) != AMU_BINARY_SHA256 or sha(runner_source) != AMU_RUNNER_SHA256:
        raise RuntimeError("pinned Amu compiler or runner source digest mismatch")
    if sha(profile_source) != AMU_PROFILE_SOURCE_SHA256:
        raise RuntimeError("pinned Amu experimental project-profile source digest mismatch")
    # The upstream Hata-focused profile intentionally omits Itonami's hash and
    # browse wires. Derive a bounded compile-only profile admitting exactly
    # this guest's declared wires; the runtime still enforces runner grants.
    if profile_source.count(AMU_PROFILE_MASK_FROM) != 1:
        raise RuntimeError("Amu profile no longer has the expected single capability mask")
    profile = profile_source.replace(AMU_PROFILE_MASK_FROM, AMU_PROFILE_MASK_TO)
    if sha(profile) != AMU_PROFILE_SHA256:
        raise RuntimeError("derived Itonami project-profile digest mismatch")
    manifest = git_blob(amu_repo, AMU_COMMIT, "seed/FULL-MANIFEST").decode("ascii")
    modules = [x for x in manifest.splitlines() if x and not x.startswith("#")]
    if len(modules) != 60 or "seed/98-x86_64-project.kotoba" not in modules:
        raise RuntimeError("pinned Amu manifest does not match the expected full source set")
    project_source = out / "amu-project.kotoba"
    with project_source.open("wb") as f:
        for module in modules:
            source = AMU_PROFILE if module == "seed/98-x86_64-project.kotoba" else module
            f.write(git_blob(amu_repo, AMU_COMMIT, source) + b"\n")

    compiler_path = out / "amu-linux-x86_64"
    compiler_path.write_bytes(binary)
    compiler_path.chmod(0o700)
    env = {"PATH": "/nonexistent", "KEXE_STACK_BYTES": "8388608",
           "KEXE_CAP_RESOURCES_34": str(out), "KEXE_CAP_RESOURCES_35": str(out)}
    calls = []
    generations = []
    for i in range(1, 4):
        image = out / f"amu-F{i}.elf"
        run(f"derive-F{i}", [compiler_path, "compile", project_source, "--target", "x86_64-linux",
            "--output", image, "--format", "static"], out, env, calls)
        image.chmod(0o700)
        data = image.read_bytes()
        generations.append({"sha256": sha(data), "bytes": len(data)})
        compiler_path = image
    if generations[0] != generations[1] or generations[1] != generations[2]:
        raise RuntimeError(f"Amu full-source derivation did not reach a fixed point: {generations}")

    src = out / "src"
    modules = [
        ("agent-cli-notepad", "agent_cli/notepad_cli.kotoba"),
        ("itonami.journal-core", "compat/itonami/journal_core.kotoba"),
        ("itonami.notepad-core", "compat/itonami/notepad_core.kotoba"),
        ("itonami.notepad-render", "compat/itonami/notepad_render.kotoba"),
        ("itonami.notepad", "compat/itonami/notepad.kotoba"),
    ]
    locked = []
    for name, relative in modules:
        src_path = src / relative
        src_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / "kotoba" / relative, src_path)
        locked.append((name, src_path))
    entry = src / "agent_cli/notepad_cli.kotoba"
    files = sorted((path for _, path in locked), key=str)
    lock_path = out / "notepad.ksl"
    lock_lines = ["KSL2", f"entry {entry}"]
    lock_lines += [f"module {name} {path}" for name, path in sorted(locked)]
    lock_lines += [f"file {sha(path.read_bytes())} {path}" for path in files]
    lock_path.write_text("\n".join(lock_lines) + "\n", encoding="ascii")

    artifact = out / "notepad.kx64"
    run("compile-notepad", [compiler_path, "compile", entry, "--target", "x86_64-linux",
        "--output", artifact, "--format", "kx64-v1", "--source-lock", lock_path,
        "--source-lock-sha256", sha(lock_path.read_bytes())], out, env, calls)
    header, separator, body = artifact.read_bytes().partition(b"\n\n")
    match = re.fullmatch(rb"KX64V1 x86_64-linux sysv-ctx-v11 ([0-9]+) 0", header)
    if not separator or not match or len(body) != int(match.group(1)):
        raise RuntimeError("invalid notepad KX64 output")
    payload = out / "notepad.payload"
    payload.write_bytes(body)

    runner_src = out / "x86_64-kx64-runner.kotoba"
    runner_src.write_bytes(runner_source)
    runner = out / "kx64-runner.elf"
    run("compile-runner", [compiler_path, "compile", runner_src, "--target", "x86_64-linux",
        "--output", runner, "--format", "kx64-runner"], out, env, calls)
    runner.chmod(0o700)

    def execute(name, root, args, allowed=WIRES, browse_root=None, app_root=None,
                expected_result=None, expected_trap=False):
        root.mkdir(parents=True, exist_ok=True)
        args = list(args)
        if args and args[0] == "set":
            args.append("2026-11-03T00:00:00Z")
        env2 = {"PATH": "/nonexistent", "KEXE_ALLOW": allowed,
                "KEXE_CAP_RESOURCES_34": str(browse_root or root),
                "KEXE_CAP_RESOURCES_35": str(app_root or root),
                "KEXE_STACK_BYTES": "8388608", "KEXE_STRUCTURED_REPORT": "1"}
        cmd = [runner, payload, "0", "0", "x86_64", allowed, "--", *args]
        started = time.monotonic()
        result = subprocess.run(list(map(str, cmd)), cwd=out, env=env2,
                                capture_output=True, timeout=60)
        calls.append({"name": name, "exit": result.returncode,
                      "seconds": round(time.monotonic() - started, 3),
                      "stdout_sha256": sha(result.stdout), "stderr_sha256": sha(result.stderr)})
        (out / f"{name}.stdout").write_bytes(result.stdout)
        (out / f"{name}.stderr").write_bytes(result.stderr)
        if expected_trap:
            if (result.returncode != 120
                    or b":status :trap :exit 120" not in result.stdout
                    or b"KEXE_TRAP" not in result.stderr):
                raise RuntimeError(f"{name} did not produce the expected fail-closed trap: "
                                   f"exit={result.returncode} stdout={result.stdout!r} "
                                   f"stderr={result.stderr!r}")
            return result
        if result.returncode != 0:
            raise RuntimeError(f"{name} trapped: exit={result.returncode} "
                               f"stdout={result.stdout!r} stderr={result.stderr!r}")
        if expected_result is not None and not re.search(
                rb":result " + str(expected_result).encode() + rb"\b", result.stdout):
            raise RuntimeError(f"{name} returned unexpected guest status: {result.stdout!r}")
        return result

    scope = out / "job-scope"
    scope.mkdir()
    positive = execute("set-positive", scope, ["set", str(scope), "alpha", "one"],
                       expected_result=0)
    digest_hex = hashlib.sha256(b"alpha").hexdigest().encode("ascii")
    encoded_digest = "".join(f"{byte:02x}" for byte in digest_hex)
    expected_file = scope / f"n{encoded_digest}.txt"
    entry = expected_file.read_text().split("\n", 3)
    if (b"alpha = one" not in positive.stdout or len(entry) != 4
            or entry[:3] != ["KNP1", "2026-11-03T00:00:00Z", "alpha"]
            or entry[3] != "one"):
        raise RuntimeError(f"set-positive mismatch: {positive.stdout!r}")
    got = execute("get-positive", scope, ["get", str(scope), "alpha"], expected_result=0)
    if b"one" not in got.stdout:
        raise RuntimeError(f"get-positive mismatch: {got.stdout!r}")
    execute("set-second-positive", scope, ["set", str(scope), "beta", "two"],
            expected_result=0)
    listed = execute("list-positive", scope, ["list", str(scope), "", ""],
                     expected_result=0)
    if b"alpha = one" not in listed.stdout or b"beta = two" not in listed.stdout:
        raise RuntimeError(f"list-positive mismatch: {listed.stdout!r}")
    execute("set-empty-value-positive", scope,
            ["set", str(scope), "empty", ""], expected_result=0)
    empty_get = execute("get-empty-value-positive", scope,
                        ["get", str(scope), "empty"], expected_result=0)
    if b"is not set" in empty_get.stdout:
        raise RuntimeError(f"empty value was confused with a missing key: {empty_get.stdout!r}")
    empty_list = execute("list-empty-value-positive", scope,
                         ["list", str(scope), "", ""], expected_result=0)
    if b"empty =" not in empty_list.stdout:
        raise RuntimeError(f"list omitted an empty-valued key: {empty_list.stdout!r}")
    deleted = execute("delete-positive", scope, ["delete", str(scope), "alpha"],
                      expected_result=0)
    if b"deleted" not in deleted.stdout or expected_file.exists():
        raise RuntimeError("delete-positive did not remove the selected note")
    missing_delete = execute("delete-missing", scope,
                             ["delete", str(scope), "alpha"], expected_result=0)
    if b"alpha is not set" not in missing_delete.stdout:
        raise RuntimeError("missing delete did not preserve the not-set response")
    execute("set-clear-target", scope, ["set", str(scope), "clear-me", "value"],
            expected_result=0)
    (scope / "keep.md").write_text("unrelated", encoding="ascii")
    cleared = execute("clear-positive", scope, ["clear", str(scope), "", ""],
                      expected_result=0)
    if b"cleared" not in cleared.stdout or (scope / "keep.md").read_text() != "unrelated":
        raise RuntimeError("clear-positive did not preserve unrelated job files")
    if any(p.name.startswith("n") and p.suffix == ".txt" for p in scope.iterdir()):
        raise RuntimeError("clear-positive left notepad note files behind")

    oversized = "x" * 16385
    before = {p.name: sha(p.read_bytes()) for p in scope.iterdir() if p.is_file()}
    too_large = execute("oversize-refusal", scope, ["set", str(scope), "large", oversized],
                        expected_result=2)
    after = {p.name: sha(p.read_bytes()) for p in scope.iterdir() if p.is_file()}
    if before != after or b"large =" in too_large.stdout:
        raise RuntimeError("oversize refusal changed the notepad files")
    long_key = execute("long-key-refusal", scope, ["set", str(scope), "k" * 129, "v"],
                       expected_result=2)
    if b"k" * 129 + b" =" in long_key.stdout:
        raise RuntimeError("long-key refusal produced a successful set response")

    outside = out / "outside-scope"
    outside.mkdir()
    escaped = execute("outside-scope-refusal", scope,
                       ["set", str(outside), "escape", "value"], expected_trap=True)
    if any(outside.iterdir()) or b"escape = value" in escaped.stdout:
        raise RuntimeError("outside-scope operation was not refused")

    package_bin = out / "candidate-package/bin"
    package_bin.mkdir(parents=True)
    package_launcher = package_bin / "itonami-agent"
    package_runner = package_bin / "notepad-runner"
    package_payload = package_bin / "notepad.payload"
    shutil.copy2(ROOT / "bin/itonami-agent", package_launcher)
    shutil.copy2(runner, package_runner)
    shutil.copy2(payload, package_payload)
    package_launcher.chmod(0o700)
    package_runner.chmod(0o700)
    if (package_bin / "kexe-loader").exists() or (package_bin / "guests").exists():
        raise RuntimeError("candidate package unexpectedly contains legacy loader artifacts")

    def execute_launcher(name, scope, args, expected_exit, expected_stdout=b"",
                         expected_stderr=b"", env_overrides=None):
        env3 = dict(os.environ)
        for key_name in ("ITONAMI_NOTEPAD_RUNNER", "ITONAMI_NOTEPAD_PAYLOAD",
                         "ITONAMI_NOTEPAD_REAL_RUNNER", "ITONAMI_NOTEPAD_TRAP_ROOT",
                         "KEXE_LOADER", "KEXE_GUESTS", "KEXE_ALLOW",
                         "KEXE_CAP_RESOURCES_34", "KEXE_CAP_RESOURCES_35",
                         "KEXE_STRUCTURED_REPORT", "KEXE_STACK_BYTES"):
            env3.pop(key_name, None)
        env3["PATH"] = "/usr/bin:/bin"
        env3["ITONAMI_NOTEPAD_RUNNER"] = str(package_runner)
        env3["ITONAMI_NOTEPAD_PAYLOAD"] = str(package_payload)
        if env_overrides:
            env3.update(env_overrides)
        started = time.monotonic()
        result = subprocess.run([str(package_launcher), "cron", "notepad", str(scope), *args],
                                cwd=out, env=env3, capture_output=True, timeout=60)
        calls.append({"name": name, "exit": result.returncode,
                      "seconds": round(time.monotonic() - started, 3),
                      "stdout_sha256": sha(result.stdout), "stderr_sha256": sha(result.stderr)})
        (out / f"{name}.stdout").write_bytes(result.stdout)
        (out / f"{name}.stderr").write_bytes(result.stderr)
        if result.returncode != expected_exit:
            raise RuntimeError(f"{name} launcher exit {result.returncode}, expected {expected_exit}: "
                               f"stdout={result.stdout!r} stderr={result.stderr!r}")
        if expected_stdout and expected_stdout not in result.stdout:
            raise RuntimeError(f"{name} launcher stdout mismatch: {result.stdout!r}")
        if expected_stderr and expected_stderr not in result.stderr:
            raise RuntimeError(f"{name} launcher stderr mismatch: {result.stderr!r}")
        return result

    launcher_scope = out / "launcher-job-scope"
    launcher_scope.mkdir()
    unmarked_parent = out / "unmarked-store"
    unmarked_scope = unmarked_parent / "job"
    unmarked_scope.mkdir(parents=True)
    execute_launcher("launcher-unconverted-store-refusal", unmarked_scope,
                     ["list"], 78,
                     expected_stderr=b"store is not converted to kotoba-notepad-v1")
    (out / ".store-format").write_text("kotoba-notepad-v1\n", encoding="ascii")
    launcher_positive = execute_launcher(
        "launcher-set-positive", launcher_scope, ["set", "alpha", "one"], 0,
        expected_stdout=b"alpha = one",)
    launcher_digest = hashlib.sha256(b"alpha").hexdigest().encode("ascii")
    launcher_encoded_digest = "".join(f"{byte:02x}" for byte in launcher_digest)
    launcher_file = launcher_scope / f"n{launcher_encoded_digest}.txt"
    launcher_entry = launcher_file.read_text().split("\n", 3)
    if (b":status :ok :result 0 " not in launcher_positive.stdout
            or len(launcher_entry) != 4 or launcher_entry[0] != "KNP1"
            or not re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}Z",
                                launcher_entry[1])
            or launcher_entry[2:] != ["alpha", "one"]):
        raise RuntimeError("launcher set did not persist the expected note under the scoped root")
    launcher_empty_set = execute_launcher(
        "launcher-empty-value-set-positive", launcher_scope,
        ["set", "empty", ""], 0, expected_stdout=b"empty =")
    launcher_empty_get = execute_launcher(
        "launcher-empty-value-get-positive", launcher_scope,
        ["get", "empty"], 0)
    if (b":status :ok :result 0 " not in launcher_empty_set.stdout
            or b":status :ok :result 0 " not in launcher_empty_get.stdout
            or b"is not set" in launcher_empty_get.stdout):
        raise RuntimeError("launcher did not preserve an empty stored value")
    launcher_empty_list = execute_launcher(
        "launcher-empty-value-list-positive", launcher_scope,
        ["list"], 0, expected_stdout=b"empty =")
    if b":status :ok :result 0 " not in launcher_empty_list.stdout:
        raise RuntimeError("launcher list omitted native success report for empty value")
    launcher_get = execute_launcher(
        "launcher-get-positive", launcher_scope, ["get", "alpha"], 0,
        expected_stdout=b"one")
    if b":status :ok :result 0 " not in launcher_get.stdout:
        raise RuntimeError("launcher get omitted native success report")
    launcher_list = execute_launcher(
        "launcher-list-positive", launcher_scope, ["list"], 0,
        expected_stdout=b"alpha = one")
    if b":status :ok :result 0 " not in launcher_list.stdout:
        raise RuntimeError("launcher list omitted native success report")
    launcher_before = {p.name: sha(p.read_bytes()) for p in launcher_scope.iterdir() if p.is_file()}
    launcher_refusal = execute_launcher(
        "launcher-oversize-refusal", launcher_scope,
        ["set", "large", "x" * 16385], 2,
        expected_stdout=b":status :ok :result 2 ",
        expected_stderr=b"cron notepad set refused")
    launcher_after = {p.name: sha(p.read_bytes()) for p in launcher_scope.iterdir() if p.is_file()}
    if launcher_before != launcher_after:
        raise RuntimeError("launcher validation refusal changed native notepad files")
    launcher_delete = execute_launcher(
        "launcher-delete-positive", launcher_scope, ["delete", "alpha"], 0,
        expected_stdout=b"deleted")
    if b":status :ok :result 0 " not in launcher_delete.stdout or launcher_file.exists():
        raise RuntimeError("launcher delete did not remove the selected note")
    launcher_missing_delete = execute_launcher(
        "launcher-delete-missing", launcher_scope, ["delete", "alpha"], 0,
        expected_stdout=b"alpha is not set")
    if b":status :ok :result 0 " not in launcher_missing_delete.stdout:
        raise RuntimeError("launcher missing delete omitted native success report")
    launcher_clear_target = execute_launcher(
        "launcher-clear-target-set", launcher_scope, ["set", "clear-me", "value"], 0)
    if b":status :ok :result 0 " not in launcher_clear_target.stdout:
        raise RuntimeError("launcher clear setup failed")
    (launcher_scope / "keep.md").write_text("unrelated", encoding="ascii")
    launcher_clear = execute_launcher(
        "launcher-clear-positive", launcher_scope, ["clear"], 0,
        expected_stdout=b"cleared")
    if (b":status :ok :result 0 " not in launcher_clear.stdout
            or (launcher_scope / "keep.md").read_text() != "unrelated"
            or any(p.name.startswith("n") and p.suffix == ".txt"
                   for p in launcher_scope.iterdir())):
        raise RuntimeError("launcher clear did not remove notes while preserving job files")

    trap_root = out / "launcher-trap-root"
    trap_root.mkdir()
    trap_scope = out / "launcher-trap-target"
    trap_scope.mkdir()
    shim = out / "notepad-runner-scope-shim"
    shim.write_text("#!/bin/sh\n"
                    "export KEXE_CAP_RESOURCES_34=\"$ITONAMI_NOTEPAD_TRAP_ROOT\"\n"
                    "export KEXE_CAP_RESOURCES_35=\"$ITONAMI_NOTEPAD_TRAP_ROOT\"\n"
                    "exec \"$ITONAMI_NOTEPAD_REAL_RUNNER\" \"$@\"\n", encoding="ascii")
    shim.chmod(0o700)
    launcher_trap = execute_launcher(
        "launcher-scope-trap-refusal", trap_scope, ["set", "escape", "value"], 1,
        expected_stdout=b":status :trap :exit 120",
        expected_stderr=b"refused by native capability boundary",
        env_overrides={"ITONAMI_NOTEPAD_RUNNER": str(shim),
                       "ITONAMI_NOTEPAD_REAL_RUNNER": str(package_runner),
                       "ITONAMI_NOTEPAD_TRAP_ROOT": str(trap_root)})
    if (any(trap_scope.iterdir()) or any(trap_root.iterdir())
            or b"escape = value" in launcher_trap.stdout):
        raise RuntimeError("launcher trap refusal wrote outside the authorized job root")

    execute_launcher("launcher-missing-artifact-blocked", launcher_scope, ["list"], 78,
                     expected_stderr=b"route 'cron notepad (native artifact missing)' is blocked",
                     env_overrides={"ITONAMI_NOTEPAD_RUNNER": str(out / "missing-runner")})

    receipt = {
        "format": "itonami.notepad-linux-native-gate.v1",
        "qualification": "Linux x86_64 candidate launcher/package route; no live fleet qualification",
        "target": "x86_64-linux",
        "itonami_source_revision": subprocess.check_output(["git", "-C", str(ROOT), "rev-parse", "HEAD"]).decode().strip(),
        "amu_source_commit": AMU_COMMIT,
        "amu_profile_source_sha256": AMU_PROFILE_SOURCE_SHA256,
        "amu_binary_sha256": AMU_BINARY_SHA256,
        "amu_profile_sha256": AMU_PROFILE_SHA256,
        "amu_project_source_sha256": sha(project_source.read_bytes()),
        "amu_compiler_generations": generations,
        "kotoba_sources": {
            f"kotoba/{relative}": sha((ROOT / "kotoba" / relative).read_bytes())
            for _, relative in modules
        },
        "project_kx64_sha256": sha(artifact.read_bytes()),
        "payload_sha256": sha(body),
        "runner_sha256": sha(runner.read_bytes()),
        "capabilities": ["hash/sha256", "fs/browse", "fs/app-data", "io/write", "cli/args", "io/write-error"],
        "scope_refusal": "Amu KX64 runner trap (exit 120) with no outside-scope write",
        "launcher_package": {
            "launcher_sha256": sha(package_launcher.read_bytes()),
            "runner_sha256": sha(package_runner.read_bytes()),
            "payload_sha256": sha(package_payload.read_bytes()),
            "legacy_loader_included": False,
        },
        "launcher_cases": [c["name"] for c in calls if c["name"].startswith("launcher-")],
        "cases": [c["name"] for c in calls if c["name"].endswith(("positive", "refusal"))],
        "processes": calls,
    }
    receipt_path = out / "build-receipt.json"
    receipt_path.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(receipt_path.read_text(), end="")


if __name__ == "__main__":
    main()
