"""Host-side device run lock for E2E commands that mutate Android state."""
import json
import os
import re
import sys
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator, Optional

from .paths import DEFAULT_RESULTS_DIR


class RunLockError(RuntimeError):
    pass


@contextmanager
def device_run_lock(serial: Optional[str], purpose: str) -> Iterator[Path]:
    label = _lock_label(serial)
    lock_dir = DEFAULT_RESULTS_DIR / "locks"
    lock_dir.mkdir(parents=True, exist_ok=True)
    lock_path = lock_dir / f"e2e-{label}.lock"
    acquired = False

    try:
        fd = os.open(str(lock_path), os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    except FileExistsError as exc:
        owner = _read_lock_owner(lock_path)
        raise RunLockError(
            f"E2E device lock is already held for {label}; refusing to run "
            f"{purpose} concurrently. Lock: {lock_path}. Owner: {owner}"
        ) from exc

    try:
        acquired = True
        with os.fdopen(fd, "w", encoding="utf-8") as fh:
            json.dump(
                {
                    "pid": os.getpid(),
                    "serial": serial or "(default)",
                    "purpose": purpose,
                    "created_at": datetime.now(timezone.utc).isoformat(),
                    "argv": sys.argv,
                },
                fh,
                indent=2,
                sort_keys=True,
            )
        yield lock_path
    finally:
        if acquired:
            try:
                lock_path.unlink()
            except FileNotFoundError:
                pass


def _lock_label(serial: Optional[str]) -> str:
    raw = serial or os.environ.get("DEVKEY_DEVICE_SERIAL") or "default"
    return re.sub(r"[^A-Za-z0-9_.-]+", "_", raw)


def _read_lock_owner(lock_path: Path) -> str:
    try:
        return lock_path.read_text(encoding="utf-8").strip() or "(empty lock file)"
    except Exception as exc:
        return f"(unable to read lock: {type(exc).__name__}: {exc})"
