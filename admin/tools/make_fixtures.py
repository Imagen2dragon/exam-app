"""Regenerate the admin app's test fixtures from the REAL server code, so the Windows app and the
server can't drift apart:  cd server && python ../admin/tools/make_fixtures.py"""
import json
import os
import pathlib
import sys
import tempfile
import time

sys.path.insert(0, os.getcwd())   # run from the server/ folder

os.environ.setdefault("DEVICE_TOKENS", "t" * 32)
os.environ.setdefault("ANTHROPIC_API_KEY", "unused")
os.environ["USAGE_DB"] = os.path.join(tempfile.mkdtemp(), "usage.db")

from fastapi.testclient import TestClient  # noqa: E402

import admin  # noqa: E402
import app as server  # noqa: E402
import usage  # noqa: E402

admin.ADMIN_TOKEN = "A" * 40
client = TestClient(server.app)
PHONE = {"Authorization": "Bearer " + "t" * 32}
ADMIN = {"Authorization": "Bearer " + admin.ADMIN_TOKEN}
dev = usage.device_id("t" * 32)
now_ms = int(time.time() * 1000)
day = 86_400_000

progress = {
    "app_version": "0.8.0", "generated_at": now_ms, "stream": "natural", "grade": 12, "exam_date": now_ms + 243 * day, "streak": 6,
    "totals": {"exams_done": 23, "avg_score_pct": 64.2, "mocks_done": 1, "best_mock_total": 341.5, "cards_total": 182, "cards_due": 14,
               "mistakes_open": 9, "books": 4, "bank_questions": 296},
    "subjects": [{"subject": s, "exams": n, "avg_pct": a, "last_pct": l} for s, n, a, l in [
        ("Mathematics", 6, 58.0, 66.0), ("Physics", 5, 71.5, 80.0), ("Chemistry", 4, 49.0, 55.0), ("Biology", 3, 77.0, 74.0),
        ("English", 3, 68.0, 70.0), ("Scholastic Aptitude", 2, 62.0, 65.0)]],
    "topics": [{"subject": s, "topic": t, "attempted": n, "accuracy": a} for s, t, n, a in [
        ("Mathematics", "Limits", 8, 0.81), ("Mathematics", "Derivatives", 6, 0.42), ("Mathematics", "Sequences", 4, 0.6),
        ("Physics", "Kinematics", 7, 0.86), ("Physics", "Electrostatics", 5, 0.4), ("Physics", "Waves", 2, 1.0),
        ("Chemistry", "Equilibrium", 6, 0.33), ("Chemistry", "Acids and bases", 4, 0.62), ("Biology", "Genetics", 5, 0.8)]],
    "recent_exams": [{"title": t, "subject": s, "finished_at": now_ms - i * day // 2, "score": sc, "max_score": mx, "mock": m}
                     for i, (t, s, sc, mx, m) in enumerate([
                         ("Chemistry – national mock", "Chemistry", 33, 60, True), ("Derivatives – hard practice", "Mathematics", 13, 20, False),
                         ("Waves practice", "Physics", 18, 20, False), ("Genetics", "Biology", 16, 20, False)])],
    "mocks": [{"created_at": now_ms - 9 * day, "total": 341.5, "papers_done": 6}, {"created_at": now_ms - day, "total": 118.0, "papers_done": 2}],
    "activity_days": [now_ms - k * day for k in range(0, 90) if k % 4 != 3 and k not in (12, 13, 40)],
    "books": [{"title": t, "subject": s, "status": st} for t, s, st in [
        ("Grade 12 Mathematics – Unit 3", "Mathematics", "ready"), ("ESSLCE Physics 2016 E.C.", "Physics", "ready"),
        ("Biology G11 – Unit 2 (photos)", "Biology", "queued"), ("Chemistry 2015 E.C. exam", "Chemistry", "ready")]],
}
assert client.post("/v1/progress", json=progress, headers=PHONE).status_code == 200
# a little history: rewrite earlier days directly (the API keeps one snapshot per day)
with usage._lock:
    for k, (avg, mock, mist) in enumerate([(51, 0, 15), (55, 0, 14), (57, 0, 12), (60, 312, 11), (62, 341.5, 10)]):
        p = json.loads(json.dumps(progress)); p["totals"].update(avg_score_pct=avg, best_mock_total=mock, mistakes_open=mist)
        d = usage.today(time.time() - (5 - k) * 7 * 86400)
        admin._db().execute("INSERT OR REPLACE INTO progress VALUES (?,?,?,?)", (dev, d, time.time() - (5 - k) * 7 * 86400, json.dumps(p)))
    admin._db().commit()
for k, (ep, model, i, o, cr) in enumerate([("/v1/exam", "claude-opus-5-5", 9000, 12000, 0), ("/v1/mock-paper", "claude-opus-5-5", 4000, 30000, 60000),
                                          ("/v1/flashcards", "claude-haiku-5-5", 3000, 2000, 0), ("/v1/explain", "claude-opus-5-5", 2500, 900, 2000)]):
    usage.record(model, i, o, cache_read=cr, device=dev, endpoint=ep)
client.put(f"/v1/admin/devices/{dev}", json={"label": "Brother's phone"}, headers=ADMIN)

out = pathlib.Path(__file__).resolve().parent.parent / "src/test/resources/fixtures"
for name, path in [("devices", "/v1/admin/devices"), ("history", f"/v1/admin/devices/{dev}/progress"), ("usage", f"/v1/admin/devices/{dev}/usage")]:
    r = client.get(path, headers=ADMIN); assert r.status_code == 200, r.text
    (out / f"{name}.json").write_text(json.dumps(r.json(), indent=1, ensure_ascii=False))
print("fixtures written to", out)
