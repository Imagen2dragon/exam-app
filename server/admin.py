"""Progress summaries from students' phones, and the owner's admin API (used by the Windows admin app).

Phones send a *summary* (scores, topic mastery, mistakes, streak, books, AI use) - never private notes
or tutor chats. The student's app shows that sharing is on. The admin API needs a separate ADMIN_TOKEN
that no phone has; repeated wrong admin tokens from one address are locked out for 15 minutes.
"""
import hmac
import json
import os
import time
from collections import defaultdict, deque

from fastapi import APIRouter, Depends, Header, HTTPException, Request
from pydantic import BaseModel, Field

import usage

ADMIN_TOKEN = os.environ.get("ADMIN_TOKEN", "")
router = APIRouter()
_admin_failures: dict[str, deque] = defaultdict(deque)
LOCKOUT_ATTEMPTS, LOCKOUT_SECONDS = 10, 15 * 60


# ---------- What a phone sends (validated and size-limited) ----------

class SubjectSummary(BaseModel):
    subject: str = Field(max_length=40)
    exams: int = Field(ge=0)
    avg_pct: float = Field(ge=0, le=100)
    last_pct: float = Field(ge=0, le=100)


class TopicSummary(BaseModel):
    subject: str = Field(max_length=40)
    topic: str = Field(max_length=120)
    attempted: int = Field(ge=0)
    accuracy: float = Field(ge=0, le=1)


class ExamSummary(BaseModel):
    title: str = Field(max_length=200)
    subject: str = Field(max_length=40)
    finished_at: int
    score: float = Field(ge=0)
    max_score: float = Field(ge=0)
    mock: bool = False


class MockSummary(BaseModel):
    created_at: int
    total: float = Field(ge=0, le=600)
    papers_done: int = Field(ge=0, le=6)


class BookSummary(BaseModel):
    title: str = Field(max_length=200)
    subject: str = Field(max_length=40)
    status: str = Field(max_length=20)


class Totals(BaseModel):
    exams_done: int = Field(ge=0)
    avg_score_pct: float = Field(ge=0, le=100)
    mocks_done: int = Field(ge=0)
    best_mock_total: float = Field(ge=0, le=600)
    cards_total: int = Field(ge=0)
    cards_due: int = Field(ge=0)
    mistakes_open: int = Field(ge=0)
    books: int = Field(ge=0)
    bank_questions: int = Field(ge=0)


class Progress(BaseModel):
    app_version: str = Field(max_length=20)
    generated_at: int
    stream: str = Field(max_length=10)
    grade: int = Field(ge=9, le=12)
    exam_date: int | None = None
    streak: int = Field(ge=0)
    totals: Totals
    subjects: list[SubjectSummary] = Field(default_factory=list, max_length=12)
    topics: list[TopicSummary] = Field(default_factory=list, max_length=400)
    recent_exams: list[ExamSummary] = Field(default_factory=list, max_length=40)
    mocks: list[MockSummary] = Field(default_factory=list, max_length=20)
    activity_days: list[int] = Field(default_factory=list, max_length=400)
    books: list[BookSummary] = Field(default_factory=list, max_length=150)


def _db():
    db = usage._conn()
    db.execute("""CREATE TABLE IF NOT EXISTS progress (
        device TEXT, day TEXT, received REAL, payload TEXT, PRIMARY KEY (device, day))""")
    db.execute("CREATE TABLE IF NOT EXISTS devices (device TEXT PRIMARY KEY, label TEXT)")
    return db


def save_progress(device: str, p: Progress):
    """Keeps one snapshot per device per day (the latest of that day), so history can be charted."""
    with usage._lock:
        _db().execute("INSERT OR REPLACE INTO progress VALUES (?,?,?,?)",
                      (device, usage.today(), time.time(), p.model_dump_json()))
        _db().execute("DELETE FROM progress WHERE device = ? AND received < ?", (device, time.time() - 400 * 86400))
        _db().commit()


# ---------- Admin authentication ----------

async def admin(request: Request, authorization: str = Header(default="")):
    ip = request.client.host if request.client else "?"
    q, now = _admin_failures[ip], time.monotonic()
    while q and now - q[0] > LOCKOUT_SECONDS:
        q.popleft()
    if len(q) >= LOCKOUT_ATTEMPTS:
        raise HTTPException(429, "Too many wrong admin keys. Try again in 15 minutes.")
    token = authorization.removeprefix("Bearer ").strip()
    if len(ADMIN_TOKEN) < 32 or not token or not hmac.compare_digest(token, ADMIN_TOKEN):
        q.append(now)
        raise HTTPException(401, "Admin key rejected")


# ---------- Admin API (read-only, except naming a device) ----------

@router.get("/v1/admin/devices", dependencies=[Depends(admin)])
async def devices():
    with usage._lock:
        rows = _db().execute("""
            SELECT d.device, COALESCE(n.label, ''), MAX(d.received), d.payload FROM progress d
            LEFT JOIN devices n ON n.device = d.device GROUP BY d.device""").fetchall()
        known = {r[0] for r in rows}
        spenders = [r[0] for r in _db().execute("SELECT DISTINCT device FROM usage WHERE device != 'unknown'").fetchall()]
    out = []
    for device, label, last_seen, payload in rows:
        out.append({"device": device, "label": label, "last_seen": last_seen, "latest": json.loads(payload),
                    "today_usd": round(usage.spent_today(device)[0], 4)})
    for device in spenders:   # devices that used AI but never sent progress (older app)
        if device not in known:
            out.append({"device": device, "label": "", "last_seen": None, "latest": None,
                        "today_usd": round(usage.spent_today(device)[0], 4)})
    return {"devices": out, "budget_usd": usage.DAILY_BUDGET_USD}


class Label(BaseModel):
    label: str = Field(max_length=60)


@router.put("/v1/admin/devices/{device}", dependencies=[Depends(admin)])
async def name_device(device: str, body: Label):
    with usage._lock:
        _db().execute("INSERT OR REPLACE INTO devices VALUES (?, ?)", (device[:12], body.label.strip()))
        _db().commit()
    return {"device": device[:12], "label": body.label.strip()}


@router.get("/v1/admin/devices/{device}/progress", dependencies=[Depends(admin)])
async def progress_history(device: str, days: int = 90):
    with usage._lock:
        rows = _db().execute("SELECT day, payload FROM progress WHERE device = ? ORDER BY day DESC LIMIT ?",
                             (device[:12], max(1, min(days, 400)))).fetchall()
    if not rows:
        raise HTTPException(404, "No progress from this device yet")
    return {"device": device[:12], "latest": json.loads(rows[0][1]),
            "history": [{"day": d, **_trend(json.loads(p))} for d, p in reversed(rows)]}


def _trend(p: dict) -> dict:
    t = p["totals"]
    return {"avg_score_pct": t["avg_score_pct"], "exams_done": t["exams_done"], "mistakes_open": t["mistakes_open"],
            "best_mock_total": t["best_mock_total"], "streak": p["streak"], "cards_total": t["cards_total"]}


@router.get("/v1/admin/devices/{device}/usage", dependencies=[Depends(admin)])
async def device_usage(device: str, days: int = 30):
    days = max(1, min(days, 400))
    with usage._lock:
        per_day = usage._conn().execute("""
            SELECT day, COUNT(*), ROUND(SUM(cost_usd), 4), SUM(input + cache_write + cache_read), SUM(cache_read), SUM(output)
            FROM usage WHERE device = ? GROUP BY day ORDER BY day DESC LIMIT ?""", (device[:12], days)).fetchall()
        by_feature = usage._conn().execute("""
            SELECT endpoint, model, COUNT(*), ROUND(SUM(cost_usd), 4) FROM usage WHERE device = ?
            AND day >= ? GROUP BY endpoint, model ORDER BY SUM(cost_usd) DESC""",
            (device[:12], per_day[-1][0] if per_day else usage.today())).fetchall()
    return {
        "device": device[:12], "budget_usd": usage.DAILY_BUDGET_USD,
        "days": [{"day": d, "requests": n, "usd": c, "input_tokens": i, "cached_share": round((cr or 0) / max(1, i or 0), 3),
                  "output_tokens": o} for d, n, c, i, cr, o in reversed(per_day)],
        "by_feature": [{"endpoint": e, "model": m, "requests": n, "usd": c} for e, m, n, c in by_feature],
        "total_usd": round(sum(c for _, _, c, *_ in per_day), 4),
    }
