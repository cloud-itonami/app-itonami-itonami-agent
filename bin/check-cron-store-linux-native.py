#!/usr/bin/env python3
"""Orchestrate an Amu-selfhost Linux runtime gate for the Kotoba cron store.

Python only pins inputs, invokes the Amu compiler/KX64 runner, and checks the
receipt. All product behavior runs in the Kotoba artifact on Amu's native
Linux static runtime; no legacy implementation or language runtime is used.
"""

import argparse
import datetime as dt
import hashlib
import json
import re
import shutil
import subprocess
import time
from pathlib import Path


AMU_COMMIT = "49dccb68f1a6ef9aece7e9de30bf6224687d06fc"
AMU_BINARY_PATH = "bin/amu-linux-x86_64"
AMU_BINARY_SHA256 = "2cbd90970f9766481ae46fd30ae515548e7b3163a9272f7a0497c988258dd654"
AMU_RUNNER_SOURCE = "seed/x86_64-kx64-runner.kotoba"
AMU_RUNNER_SHA256 = "c9f1fe1b5c1748450fb11b233cbdc9d9234880a60dc480c0387b173f60d905be"
KOTOBA_COMMIT = "63e30efc6402320fca6f9c70abd93f113e05d99c"
KOTOBA_JCS_PATH = "lib/org.ietf.jcs/kotoba/jcs/reader.kotoba"
KOTOBA_JCS_SHA256 = "f8af2953904bd0a96e7b5c3b9e34151bcae3754e5358669adb8ac289acc0872f"
KOTOBA_STORE_IO_PATH = "lib/kotoba.hata/src/hata/store_io.kotoba"
KOTOBA_STORE_IO_SHA256 = "096f6d29d77f374313600a51a18366ee59525c7385456a01218a160904187b10"

ROOT = Path(__file__).resolve().parents[1]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def git(repo, *args):
    return subprocess.check_output(["git", "-C", str(repo), *args])


def fetch_commit(url, commit, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.mkdir(parents=True, exist_ok=False)
    subprocess.run(["git", "init", "--bare", str(path)], check=True, stdout=subprocess.DEVNULL)
    subprocess.run(["git", "-C", str(path), "remote", "add", "origin", url], check=True)
    subprocess.run(["git", "-C", str(path), "-c", "protocol.version=2", "fetch",
                    "--depth=1", "--filter=blob:none", "origin", commit], check=True)
    actual = git(path, "rev-parse", "FETCH_HEAD").decode().strip()
    if actual != commit:
        raise RuntimeError(f"source revision mismatch: expected {commit}, got {actual}")


def git_blob(repo, commit, path):
    return subprocess.check_output(["git", "-C", str(repo), "show", f"{commit}:{path}"])


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def run(name, argv, cwd, env, calls, timeout=900):
    started = time.monotonic()
    result = subprocess.run(list(map(str, argv)), cwd=cwd, env=env,
                            capture_output=True, timeout=timeout)
    calls.append({"name": name, "exit": result.returncode,
                  "seconds": round(time.monotonic() - started, 3),
                  "stdout_sha256": sha(result.stdout), "stderr_sha256": sha(result.stderr)})
    if result.returncode != 0:
        raise RuntimeError(f"{name} failed ({result.returncode}): "
                           f"{result.stderr.decode(errors='replace')[-2000:]}")
    if result.stderr:
        raise RuntimeError(f"{name} wrote stderr: {result.stderr.decode(errors='replace')[-2000:]}")
    return result.stdout


def locked_project(out, kotoba_repo):
    sources = out / "src"
    gate = sources / "gate/cron_jobs_store_clock_gate.kotoba"
    store_io = sources / "hata/store_io.kotoba"
    jobs = sources / "itonami/cron_jobs_json.kotoba"
    store = sources / "itonami/cron_jobs_store.kotoba"
    schedule = sources / "itonami/schedule.kotoba"
    jcs = sources / "jcs/reader.kotoba"
    shutil.copy2(ROOT / "test/native/cron_jobs_store_clock_gate.kotoba", gate)
    shutil.copy2(ROOT / "kotoba/itonami/cron_jobs_json.kotoba", jobs)
    shutil.copy2(ROOT / "kotoba/itonami/cron_jobs_store.kotoba", store)
    shutil.copy2(ROOT / "kotoba/itonami/schedule.kotoba", schedule)
    write(jcs, git_blob(kotoba_repo, KOTOBA_COMMIT, KOTOBA_JCS_PATH))
    write(store_io, git_blob(kotoba_repo, KOTOBA_COMMIT, KOTOBA_STORE_IO_PATH))

    modules = [
        ("hata.store-io", store_io),
        ("itonami.cron-jobs-json", jobs),
        ("itonami.cron-jobs-store", store),
        ("itonami.cron-jobs-store-clock-gate", gate),
        ("itonami.schedule", schedule),
        ("jcs.reader", jcs),
    ]
    modules.sort(key=lambda row: row[0])
    files = sorted([gate, store_io, jobs, store, schedule, jcs], key=lambda p: str(p))
    lock_lines = ["KSL2", f"entry {gate}"]
    lock_lines.extend(f"module {name} {path}" for name, path in modules)
    lock_lines.extend(f"file {sha(path.read_bytes())} {path}" for path in files)
    lock = out / "cron-store.ksl"
    lock.write_text("\n".join(lock_lines) + "\n", encoding="ascii")
    return gate, lock, {str(path): sha(path.read_bytes()) for path in files}


def execute(name, runner, payload, scope, mode, out, calls):
    env = {
        "PATH": "/nonexistent",
        "KEXE_ALLOW": "7,35,38",
        "KEXE_CAP_RESOURCES_35": str(scope),
        "KEXE_STACK_BYTES": "8388608",
        "KEXE_STRUCTURED_REPORT": "1",
    }
    argv = [runner, payload, "0", "0", "x86_64", "7,35,38", "--", "/d", mode]
    stdout = run(name, argv, out, env, calls)
    if not re.search(rb":result 0\b", stdout):
        raise RuntimeError(f"{name} returned no successful KX64 result: {stdout!r}")
    return stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--amu-url", default="https://github.com/kotoba-lang/amu.git")
    parser.add_argument("--kotoba-url", default="https://github.com/kotoba-lang/kotoba.git")
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    amu_repo = out / "inputs/amu"
    kotoba_repo = out / "inputs/kotoba"
    fetch_commit(args.amu_url, AMU_COMMIT, amu_repo)
    fetch_commit(args.kotoba_url, KOTOBA_COMMIT, kotoba_repo)

    amu_binary_bytes = git_blob(amu_repo, AMU_COMMIT, AMU_BINARY_PATH)
    runner_source_bytes = git_blob(amu_repo, AMU_COMMIT, AMU_RUNNER_SOURCE)
    if sha(amu_binary_bytes) != AMU_BINARY_SHA256:
        raise RuntimeError("pinned Amu Linux compiler digest mismatch")
    if sha(runner_source_bytes) != AMU_RUNNER_SHA256:
        raise RuntimeError("pinned Amu KX64 runner source digest mismatch")
    if sha(git_blob(kotoba_repo, KOTOBA_COMMIT, KOTOBA_JCS_PATH)) != KOTOBA_JCS_SHA256:
        raise RuntimeError("pinned Kotoba JCS source digest mismatch")
    if sha(git_blob(kotoba_repo, KOTOBA_COMMIT, KOTOBA_STORE_IO_PATH)) != KOTOBA_STORE_IO_SHA256:
        raise RuntimeError("pinned Kotoba Hata store source digest mismatch")

    amu_binary = out / "amu-linux-x86_64"
    write(amu_binary, amu_binary_bytes)
    amu_binary.chmod(0o700)
    runner_source = out / "x86_64-kx64-runner.kotoba"
    write(runner_source, runner_source_bytes)
    gate, lock, source_digests = locked_project(out, kotoba_repo)
    calls = []

    artifact = out / "cron-store.kx64"
    compile_env = {"PATH": "/nonexistent", "KEXE_CAP_RESOURCES_35": str(out),
                   "KEXE_STACK_BYTES": "8388608"}
    run("compile-project", [amu_binary, "compile", gate, "--target", "x86_64-linux",
        "--output", artifact, "--format", "kx64-v1", "--source-lock", lock,
        "--source-lock-sha256", sha(lock.read_bytes())], out, compile_env, calls)
    head, separator, payload_bytes = artifact.read_bytes().partition(b"\n\n")
    match = re.fullmatch(rb"KX64V1 x86_64-linux sysv-ctx-v11 ([0-9]+) 0", head)
    if not separator or not match or len(payload_bytes) != int(match.group(1)):
        raise RuntimeError("Amu emitted an invalid KX64V1 artifact")
    payload = out / "cron-store.payload"
    write(payload, payload_bytes)

    runner = out / "kx64-runner.elf"
    run("compile-runner", [amu_binary, "compile", runner_source, "--target", "x86_64-linux",
        "--output", runner, "--format", "kx64-runner"], out, compile_env, calls)
    runner.chmod(0o700)

    positive = out / "positive-scope"
    (positive / "d").mkdir(parents=True)
    (positive / "d/jobs.json").write_text(
        '{"jobs":[{"id":"remove-me","keep":1},{"id":"stay","future":1.25e+7}],'
        '"unknown":{"n":-0.0001},"updated_at":"old"}', encoding="ascii")
    positive_stdout = execute("runtime-clock-fs-positive", runner, payload, positive,
                              "positive", out, calls)
    persisted = (positive / "d/jobs.json").read_text(encoding="ascii")
    prefix = ('{"jobs":[{"id":"stay","future":1.25e+7}],'
              '"unknown":{"n":-0.0001},"updated_at":"')
    if not persisted.startswith(prefix) or not persisted.endswith('"}'):
        raise RuntimeError(f"positive store output mismatch: {persisted!r}")
    timestamp = persisted[len(prefix):-2]
    parsed = dt.datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    if parsed.tzinfo is None or abs(parsed.timestamp() - time.time()) > 10:
        raise RuntimeError(f"Amu wall clock is outside 10 seconds of host UTC: {timestamp}")
    if (positive / "d/.jobs.json.tmp").exists():
        raise RuntimeError("positive store left a temporary file")

    refusal = out / "refusal-scope"
    (refusal / "d").mkdir(parents=True)
    malformed = '{"jobs":'
    (refusal / "d/jobs.json").write_text(malformed, encoding="ascii")
    refusal_stdout = execute("runtime-clock-fs-refusal", runner, payload, refusal,
                             "refuse", out, calls)
    if (refusal / "d/jobs.json").read_text(encoding="ascii") != malformed:
        raise RuntimeError("refusal modified malformed source JSON")
    if (refusal / "d/.jobs.json.tmp").exists():
        raise RuntimeError("refusal left a temporary file")

    source_revision = subprocess.check_output(["git", "-C", str(ROOT), "rev-parse", "HEAD"]).decode().strip()
    receipt = {
        "format": "itonami.cron-jobs-linux-native-gate.v1",
        "itonami_source_revision": source_revision,
        "amu_source_commit": AMU_COMMIT,
        "amu_linux_compiler_sha256": sha(amu_binary_bytes),
        "kotoba_source_commit": KOTOBA_COMMIT,
        "kotoba_jcs_source_sha256": KOTOBA_JCS_SHA256,
        "kotoba_store_io_source_sha256": KOTOBA_STORE_IO_SHA256,
        "runtime": "Amu x86_64-linux KX64V1 static runner",
        "project_source_lock_sha256": sha(lock.read_bytes()),
        "project_kx64_sha256": sha(artifact.read_bytes()),
        "project_payload_sha256": sha(payload_bytes),
        "runner_source_sha256": sha(runner_source_bytes),
        "runner_elf_sha256": sha(runner.read_bytes()),
        "sources_sha256": source_digests,
        "wall_timestamp": timestamp,
        "positive_runtime": "PASS",
        "refusal_runtime": "PASS",
        "codesign": "not-applicable: Linux static ELF",
        "host_oracle_clock_check": "PASS within 10 seconds",
        "processes": calls,
        "stdout_sha256": {"positive": sha(positive_stdout), "refusal": sha(refusal_stdout)},
        "scope": "standalone Linux native clock plus scoped FS35 cron-store route; launcher and macOS clock qualification remain open",
    }
    receipt_path = out / "build-receipt.json"
    receipt_path.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(receipt_path.read_text(encoding="utf-8"), end="")


if __name__ == "__main__":
    main()
