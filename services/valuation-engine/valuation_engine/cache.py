"""
Persistent JSON cache backed by SQLite (single file, no server, safe across restarts).

Replaces the Sprint-0 directory-of-files cache. One table keyed by URL; TTL enforced on read.
"""
from __future__ import annotations

import json
import sqlite3
import threading
import time
from pathlib import Path
from typing import Any


class SQLiteCache:
    def __init__(self, path: Path, ttl_seconds: int) -> None:
        self.path = Path(path)
        self.ttl = ttl_seconds
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()
        self._conn = sqlite3.connect(self.path, check_same_thread=False)
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._conn.execute(
            "CREATE TABLE IF NOT EXISTS cache (key TEXT PRIMARY KEY, payload BLOB NOT NULL, fetched_at REAL NOT NULL)"
        )
        self._conn.commit()

    def get(self, key: str) -> Any | None:
        with self._lock:
            row = self._conn.execute("SELECT payload, fetched_at FROM cache WHERE key = ?", (key,)).fetchone()
        if row is None:
            return None
        payload, fetched_at = row
        if time.time() - fetched_at > self.ttl:
            return None
        try:
            return json.loads(payload)
        except json.JSONDecodeError:
            return None

    def set(self, key: str, value: Any) -> None:
        blob = json.dumps(value).encode()
        with self._lock:
            self._conn.execute(
                "INSERT INTO cache(key, payload, fetched_at) VALUES(?,?,?) "
                "ON CONFLICT(key) DO UPDATE SET payload=excluded.payload, fetched_at=excluded.fetched_at",
                (key, blob, time.time()),
            )
            self._conn.commit()

    def stats(self) -> dict[str, Any]:
        with self._lock:
            n, oldest = self._conn.execute("SELECT COUNT(*), MIN(fetched_at) FROM cache").fetchone()
        size = self.path.stat().st_size if self.path.exists() else 0
        return {"entries": n, "size_bytes": size, "oldest_age_s": (time.time() - oldest) if oldest else None, "path": str(self.path)}

    def purge_expired(self) -> int:
        with self._lock:
            cur = self._conn.execute("DELETE FROM cache WHERE fetched_at < ?", (time.time() - self.ttl,))
            self._conn.commit()
        return cur.rowcount
