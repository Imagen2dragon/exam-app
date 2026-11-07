"""Progress summaries and the owner-only admin API."""
import pytest
from fastapi.testclient import TestClient

import admin
import app as server
import usage

client = TestClient(server.app)
DEVICE_TOKEN = "t" * 32
DEVICE = usage.device_id(DEVICE_TOKEN)
PHONE = {"Authorization": "Bearer " + DEVICE_TOKEN}
ADMIN_KEY = "A" * 40
ADMIN = {"Authorization": "Bearer " + ADMIN_KEY}


@pytest.fixture(autouse=True)
def admin_key(monkeypatch):
    monkeypatch.setattr(admin, "ADMIN_TOKEN", ADMIN_KEY)
    admin._admin_failures.clear()
    server._hits.clear()


def progress(avg=62.5, mistakes=7, **kw):
    return {
        "app_version": "0.8.0", "generated_at": 1794000000000, "stream": "natural", "grade": 12,
        "exam_date": 1814000000000, "streak": 5,
        "totals": {"exams_done": 12, "avg_score_pct": avg, "mocks_done": 1, "best_mock_total": 341.5, "cards_total": 140,
                   "cards_due": 9, "mistakes_open": mistakes, "books": 3, "bank_questions": 260},
        "subjects": [{"subject": "Physics", "exams": 4, "avg_pct": 58, "last_pct": 71}],
        "topics": [{"subject": "Physics", "topic": "Waves", "attempted": 6, "accuracy": 0.83}],
        "recent_exams": [{"title": "Waves", "subject": "Physics", "finished_at": 1793990000000, "score": 17, "max_score": 20}],
        "mocks": [{"created_at": 1793000000000, "total": 341.5, "papers_done": 6}],
        "activity_days": [1793900000000], "books": [{"title": "Physics G12", "subject": "Physics", "status": "ready"}], **kw,
    }


def test_phone_uploads_progress_and_admin_sees_it():
    assert client.post("/v1/progress", json=progress(), headers=PHONE).json() == {"ok": True}
    usage.record("claude-opus-5-5", 1000, 1000, device=DEVICE, endpoint="/v1/exam")
    d = client.get("/v1/admin/devices", headers=ADMIN).json()
    dev = d["devices"][0]
    assert dev["device"] == DEVICE and dev["latest"]["totals"]["best_mock_total"] == 341.5
    assert dev["today_usd"] == pytest.approx(0.024) and d["budget_usd"] == 5.0


def test_history_keeps_one_snapshot_per_day():
    client.post("/v1/progress", json=progress(avg=50), headers=PHONE)
    client.post("/v1/progress", json=progress(avg=64, mistakes=3), headers=PHONE)      # same day: replaces
    h = client.get(f"/v1/admin/devices/{DEVICE}/progress", headers=ADMIN).json()
    assert len(h["history"]) == 1 and h["history"][0]["avg_score_pct"] == 64 and h["latest"]["totals"]["mistakes_open"] == 3


def test_usage_report_per_day_and_feature():
    usage.record("claude-opus-5-5", 2000, 1000, cache_read=6000, device=DEVICE, endpoint="/v1/exam")
    usage.record("claude-haiku-5-5", 1000, 500, device=DEVICE, endpoint="/v1/flashcards")
    u = client.get(f"/v1/admin/devices/{DEVICE}/usage", headers=ADMIN).json()
    assert u["days"][0]["requests"] == 2 and u["days"][0]["cached_share"] == pytest.approx(6000 / 9000, abs=1e-3)
    assert [f["endpoint"] for f in u["by_feature"]] == ["/v1/exam", "/v1/flashcards"]


def test_devices_can_be_named():
    client.post("/v1/progress", json=progress(), headers=PHONE)
    client.put(f"/v1/admin/devices/{DEVICE}", json={"label": "Brother's phone"}, headers=ADMIN)
    assert client.get("/v1/admin/devices", headers=ADMIN).json()["devices"][0]["label"] == "Brother's phone"


def test_phone_token_cannot_open_admin_and_bad_keys_lock_out():
    assert client.get("/v1/admin/devices", headers=PHONE).status_code == 401
    assert client.get("/v1/admin/devices").status_code == 401
    for _ in range(7):        # 2 failures above + 7 = 9
        client.get("/v1/admin/devices", headers={"Authorization": "Bearer wrong"})
    assert client.get("/v1/admin/devices", headers={"Authorization": "Bearer wrong"}).status_code == 401   # 10th
    assert client.get("/v1/admin/devices", headers=ADMIN).status_code == 429   # locked out, even with the right key


def test_admin_disabled_without_a_strong_key(monkeypatch):
    monkeypatch.setattr(admin, "ADMIN_TOKEN", "short")
    assert client.get("/v1/admin/devices", headers={"Authorization": "Bearer short"}).status_code == 401


def test_progress_is_validated_and_needs_a_device_token():
    assert client.post("/v1/progress", json=progress()).status_code == 401
    too_many = progress(topics=[{"subject": "P", "topic": f"t{i}", "attempted": 1, "accuracy": 0.5} for i in range(401)])
    assert client.post("/v1/progress", json=too_many, headers=PHONE).status_code == 422
    bad = progress(); bad["totals"]["avg_score_pct"] = 140
    assert client.post("/v1/progress", json=bad, headers=PHONE).status_code == 422


def test_unknown_device_progress_is_404():
    assert client.get("/v1/admin/devices/abcdef123456/progress", headers=ADMIN).status_code == 404


def test_last_backup_time_reaches_the_admin():
    client.post("/v1/progress", json=progress(last_backup=1794000500000), headers=PHONE)
    assert client.get("/v1/admin/devices", headers=ADMIN).json()["devices"][0]["latest"]["last_backup"] == 1794000500000
