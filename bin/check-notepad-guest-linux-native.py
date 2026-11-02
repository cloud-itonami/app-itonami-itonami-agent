#!/usr/bin/env python3
"""Build and qualify the standalone Itonami notepad guest through Amu selfhost.

This runner orchestrates pinned source/compiler inputs. Notepad behavior executes
only inside the Amu-produced Kotoba KX64 artifact; it is not launcher or package
qualification.
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


AMU_COMMIT = "c7df2408be2386e8d837e9e1dbfd90c2c88e2e67"
AMU_BINARY = "bin/amu-linux-x86_64"
AMU_BINARY_SHA256 = "a91b2585fff847651033ce57f5421a49d0a3efadf88b3f08b92ad25fc7521913"
AMU_RUNNER_SOURCE = "seed/x86_64-kx64-runner.kotoba"
AMU_RUNNER_SHA256 = "c9f1fe1b5c1748450fb11b233cbdc9d9234880a60dc480c0387b173f60d905be"
AMU_PROFILE = "seed/experimental/98-x86_64-project-fs-cli.kotoba"
AMU_PROFILE_SHA256 = "008f212f1aa14e9551ba5251047174525f3dbb364ada330dc1379d23fa9e2e1a"
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
    profile = git_blob(amu_repo, AMU_COMMIT, AMU_PROFILE)
    if sha(binary) != AMU_BINARY_SHA256 or sha(runner_source) != AMU_RUNNER_SHA256:
        raise RuntimeError("pinned Amu compiler or runner source digest mismatch")
    if sha(profile) != AMU_PROFILE_SHA256:
        raise RuntimeError("pinned Amu experimental project-profile digest mismatch")
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
    if b"alpha = one" not in positive.stdout or expected_file.read_text() != "alpha\none":
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

    receipt = {
        "format": "itonami.notepad-linux-native-gate.v1",
        "qualification": "standalone Amu native runtime only; launcher/package not qualified",
        "target": "x86_64-linux",
        "itonami_source_revision": subprocess.check_output(["git", "-C", str(ROOT), "rev-parse", "HEAD"]).decode().strip(),
        "amu_source_commit": AMU_COMMIT,
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
        "cases": [c["name"] for c in calls if c["name"].endswith(("positive", "refusal"))],
        "processes": calls,
    }
    receipt_path = out / "build-receipt.json"
    receipt_path.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(receipt_path.read_text(), end="")


if __name__ == "__main__":
    main()
