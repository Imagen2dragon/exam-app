"""AI usage tracking and per-device daily budgets.

Every Claude call is recorded with its token counts and an estimated cost, so the owner can see
what each device spends and cap it. Device tokens are never stored: only a short SHA-256 hash.
"""
import contextvars
import hashlib
import os
import sqlite3
import threading
import time
from datetime import datetime
from zoneinfo import ZoneInfo

DB_PATH = os.environ.get("USAGE_DB", "usage.db")
TZ = ZoneInfo(os.environ.get("USAGE_TZ", "Africa/Addis_Ababa"))  # budgets reset at local midnight
DAILY_BUDGET_USD = float(os.environ.get("DAILY_BUDGET_USD", "5"))  # per device; 0 = no limit

# USD per million tokens (input, output). Cache writes (5-minute TTL) cost 1.25x input;
# cache reads cost the model's read rate. Unknown models are priced as Opus 5.5.
PRICES = {
    "claude-opus-5-5": (4.0, 20.0, 0.20),
    "claude-opus-5": (5.0, 25.0, 0.50),
    "claude-opus-4-8": (5.0, 25.0, 0.50),
    "claude-sonnet-5-5": (2.0, 10.0, 0.20),
    "claude-haiku-5-5": (0.10, 0.50, 0.01),
}

current_device: contextvars.ContextVar[str] = contextvars.ContextVar("device", default="unknown")
current_endpoint: contextvars.ContextVar[str] = contextvars.ContextVar("endpoint", default="")

_lock = threading.Lock()
_db: sqlite3.Connection | None = None


def device_id(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()[:12]


def _conn() -> sqlite3.Connection:
    global _db
    if _db is None:
        _db = sqlite3.connect(DB_PATH, check_same_thread=False)
        _db.execute("""CREATE TABLE IF NOT EXISTS usage (
            ts REAL, day TEXT, device TEXT, endpoint TEXT, model TEXT, batch INTEGER,
            input INTEGER, output INTEGER, cache_write INTEGER, cache_read INTEGER, cost_usd REAL)""")
        _db.execute("CREATE INDEX IF NOT EXISTS usage_day_device ON usage(day, device)")
    return _db


def reset(path: str | None = None):
    """Tests: point at a fresh database."""
    global _db, DB_PATH
    if _db is not None:
        _db.close()
    _db = None
    if path:
        DB_PATH = path


def today(ts: float | None = None) -> str:
    return datetime.fromtimestamp(ts if ts is not None else time.time(), TZ).date().isoformat()


def cost(model: str, input_tokens: int, output_tokens: int, cache_write: int = 0, cache_read: int = 0,
         batch: bool = False) -> float:
    inp, out, read = PRICES.get(model, PRICES["claude-opus-5-5"])
    usd = (input_tokens * inp + output_tokens * out + cache_write * inp * 1.25 + cache_read * read) / 1_000_000
    return usd * (0.5 if batch else 1.0)  # Message Batches are billed at 50%


def record(model: str, input_tokens: int, output_tokens: int, cache_write: int = 0, cache_read: int = 0,
           batch: bool = False, device: str | None = None, endpoint: str | None = None) -> float:
    usd = cost(model, input_tokens, output_tokens, cache_write, cache_read, batch)
    now = time.time()
    with _lock:
        _conn().execute("INSERT INTO usage VALUES (?,?,?,?,?,?,?,?,?,?,?)", (
            now, today(now), device or current_device.get(), endpoint or current_endpoint.get(), model, int(batch),
            input_tokens, output_tokens, cache_write, cache_read, usd))
        _conn().commit()
    return usd


def record_message(msg) -> float:
    """Record a Messages API response (the SDK's usage fields may be None)."""
    u = msg.usage
    return record(msg.model, u.input_tokens or 0, u.output_tokens or 0,
                  getattr(u, "cache_creation_input_tokens", 0) or 0, getattr(u, "cache_read_input_tokens", 0) or 0)


def spent_today(device: str) -> tuple[float, int]:
    with _lock:
        row = _conn().execute("SELECT COALESCE(SUM(cost_usd), 0), COUNT(*) FROM usage WHERE day = ? AND device = ?",
                              (today(), device)).fetchone()
    return float(row[0]), int(row[1])


def over_budget(device: str) -> bool:
    return DAILY_BUDGET_USD > 0 and spent_today(device)[0] >= DAILY_BUDGET_USD


def report(days: int = 7) -> list[tuple]:
    """(day, device, requests, cost, cache-read share of input) for the owner's report."""
    with _lock:
        return _conn().execute("""
            SELECT day, device, COUNT(*), ROUND(SUM(cost_usd), 4),
                   ROUND(1.0 * SUM(cache_read) / MAX(1, SUM(input + cache_write + cache_read)), 3)
            FROM usage GROUP BY day, device ORDER BY day DESC, device LIMIT ?""", (days * 50,)).fetchall()
