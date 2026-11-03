#!/usr/bin/env python3
"""One-time offline migration of Hermes cron/notepad.db into the Kotoba KNP1 store.

This tool is not part of Itonami runtime, build, CI, packaging, or fallback paths.
Stop Hermes writers before invoking it and retain the source SQLite database.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import sqlite3
import tempfile
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any


FORMAT = "kotoba-notepad-v1"
MAX_KEY_CHARS = 128
MAX_VALUE_BYTES = 16 * 1024
MAX_JOB_TOTAL_BYTES = 64 * 1024
SAFE_JOB_ID = re.compile(r"[A-Za-z0-9_-]+\Z")


def note_filename(key: str) -> str:
    digest_hex = hashlib.sha256(key.encode("utf-8")).hexdigest()
    return "n" + digest_hex.encode("ascii").hex() + ".txt"


def valid_timestamp(value: str) -> bool:
    if not value or "\n" in value:
        return False
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return False
    return True


def read_rows(db_path: Path) -> list[tuple[str, str, str, str]]:
    uri = db_path.resolve().as_uri() + "?mode=ro"
    conn = sqlite3.connect(uri, uri=True)
    try:
        conn.execute("BEGIN")
        columns = [row[1] for row in conn.execute("PRAGMA table_info(cron_notepad)")]
        expected = ["job_id", "key", "value", "updated_at"]
        if not all(name in columns for name in expected):
            raise ValueError(f"cron_notepad schema lacks required columns: {columns!r}")
        rows = conn.execute(
            "SELECT job_id, key, value, updated_at FROM cron_notepad ORDER BY job_id, key"
        ).fetchall()
        return [tuple(row) for row in rows]
    finally:
        conn.close()


def validate_rows(rows: list[tuple[str, str, str, str]]) -> dict[str, int]:
    totals: dict[str, int] = defaultdict(int)
    seen: set[tuple[str, str]] = set()
    filenames: dict[tuple[str, str], str] = {}
    for row_num, row in enumerate(rows, 1):
        if len(row) != 4 or any(not isinstance(value, str) for value in row):
            raise ValueError(f"row {row_num}: expected four text fields")
        job_id, key, value, updated_at = row
        if not SAFE_JOB_ID.fullmatch(job_id):
            raise ValueError(f"row {row_num}: unsafe job_id {job_id!r}")
        if (not key or key.isspace() or "\n" in key
                or len(key) > MAX_KEY_CHARS):
            raise ValueError(f"row {row_num}: key is not representable by KNP1: {key!r}")
        value_bytes = len(value.encode("utf-8"))
        if value_bytes > MAX_VALUE_BYTES:
            raise ValueError(f"row {row_num}: value exceeds {MAX_VALUE_BYTES} bytes")
        if not valid_timestamp(updated_at):
            raise ValueError(f"row {row_num}: invalid updated_at {updated_at!r}")
        pair = (job_id, key)
        if pair in seen:
            raise ValueError(f"row {row_num}: duplicate job/key pair {pair!r}")
        seen.add(pair)
        name = note_filename(key)
        prior = filenames.setdefault((job_id, name), key)
        if prior != key:
            raise ValueError(f"row {row_num}: SHA-256 filename collision in job {job_id!r}")
        totals[job_id] += len(key.encode("utf-8")) + value_bytes
        if totals[job_id] > MAX_JOB_TOTAL_BYTES:
            raise ValueError(f"job {job_id!r} exceeds {MAX_JOB_TOTAL_BYTES} bytes")
    return dict(totals)


def convert(profile_home: Path, db_path: Path) -> dict[str, Any]:
    profile_home = profile_home.resolve()
    db_path = db_path.resolve()
    cron_dir = profile_home / "cron"
    target = cron_dir / "notepad"
    if not db_path.is_file():
        raise FileNotFoundError(f"source database does not exist: {db_path}")
    if not target.parent.is_dir():
        raise FileNotFoundError(f"cron directory does not exist: {target.parent}")
    if target.exists():
        raise FileExistsError(f"target already exists; refusing to overwrite: {target}")

    rows = read_rows(db_path)
    totals = validate_rows(rows)
    row_hashes = []
    stage = Path(tempfile.mkdtemp(prefix=".notepad-stage-", dir=cron_dir))
    try:
        for job_id, key, value, updated_at in rows:
            job_dir = stage / job_id
            job_dir.mkdir(parents=True, exist_ok=True)
            content = f"KNP1\n{updated_at}\n{key}\n{value}"
            encoded = content.encode("utf-8")
            destination = job_dir / note_filename(key)
            with destination.open("xb") as handle:
                handle.write(encoded)
                handle.flush()
                os.fsync(handle.fileno())
            row_hashes.append(hashlib.sha256(encoded).hexdigest())

        receipt = {
            "format": FORMAT,
            "source": db_path.name,
            "row_count": len(rows),
            "job_count": len(totals),
            "per_job_key_value_bytes": dict(sorted(totals.items())),
            "records_sha256_sorted": sorted(row_hashes),
        }
        receipt_bytes = (json.dumps(receipt, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()
        with (stage / ".conversion-receipt.json").open("xb") as handle:
            handle.write(receipt_bytes)
            handle.flush()
            os.fsync(handle.fileno())
        with (stage / ".store-format").open("xb") as handle:
            handle.write((FORMAT + "\n").encode("ascii"))
            handle.flush()
            os.fsync(handle.fileno())
        os.rename(stage, target)
        return receipt
    except Exception:
        if stage.exists():
            shutil.rmtree(stage)
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile_home", type=Path, help="Hermes profile home containing cron/notepad.db")
    parser.add_argument("--offline-confirmed", action="store_true",
                        help="confirm Hermes writers are stopped and the source snapshot is stable")
    args = parser.parse_args()
    if not args.offline_confirmed:
        parser.error("conversion requires --offline-confirmed after stopping Hermes writers")
    profile_home = args.profile_home
    receipt = convert(profile_home, profile_home / "cron" / "notepad.db")
    print(json.dumps(receipt, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
