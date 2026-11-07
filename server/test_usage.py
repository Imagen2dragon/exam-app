"""Cost tracking and daily budgets, exercising the real ai.structured with a fake Anthropic client."""
import pytest
from fastapi.testclient import TestClient

import ai
import app as server
import usage

client = TestClient(server.app)
TOKEN = "t" * 32
AUTH = {"Authorization": "Bearer " + TOKEN}
DEVICE = usage.device_id(TOKEN)


def test_cost_math():
    assert usage.cost("claude-opus-5-5", 1_000_000, 1_000_000) == pytest.approx(24.0)
    assert usage.cost("claude-opus-5-5", 0, 0, cache_write=1_000_000) == pytest.approx(5.0)   # 1.25x
    assert usage.cost("claude-opus-5-5", 0, 0, cache_read=1_000_000) == pytest.approx(0.20)   # 5% on Opus 5.5
    assert usage.cost("claude-opus-5-5", 1_000_000, 0, batch=True) == pytest.approx(2.0)      # batches 50%
    assert usage.cost("some-future-model", 1_000_000, 0) == pytest.approx(4.0)                # priced as Opus 5.5


def test_every_ai_call_is_recorded_against_a_hashed_device(fake_claude):
    fake_claude({"cards": []}, input_tokens=2000, output_tokens=1000, cache_read_input_tokens=3000)
    r = client.post("/v1/flashcards", json={"subject": "Biology", "topic": "Cells", "grade": 10}, headers=AUTH)
    assert r.status_code == 200
    spent, n = usage.spent_today(DEVICE)
    assert n == 1 and spent == pytest.approx((2000 * 0.10 + 1000 * 0.50 + 3000 * 0.01) / 1e6)   # flashcards run on Haiku 5.5
    row = usage._conn().execute("SELECT device, endpoint FROM usage").fetchone()
    assert row == (DEVICE, "/v1/flashcards") and TOKEN not in str(row)


def test_parallel_mock_sections_are_all_attributed(fake_claude):
    blueprint = {"sections": [{"grade": 12, "unit": "U", "topics": "t", "count": 30}]}
    q = {"type": "mcq", "stem": "q", "options": ["a", "b", "c", "d"], "answer": "A", "numeric_answer": 0, "tolerance": 0,
         "unit": "", "explanation": "", "topic": "t", "difficulty": "standard", "marks": 1}
    fake_claude(script=[(blueprint, "end_turn"), ({"title": "t", "minutes": 0, "questions": [q] * 25}, "end_turn"),
                        ({"title": "t", "minutes": 0, "questions": [q] * 5}, "end_turn")])
    r = client.post("/v1/mock-paper", json={"subject": "Physics", "stream": "natural", "count": 30}, headers=AUTH)
    assert r.status_code == 200, r.text
    devices = usage._conn().execute("SELECT DISTINCT device FROM usage").fetchall()
    assert usage.spent_today(DEVICE)[1] == 3 and devices == [(DEVICE,)]


def test_budget_blocks_ai_calls_but_not_the_usage_view(fake_claude, monkeypatch):
    fake_claude({"cards": []})
    monkeypatch.setattr(usage, "DAILY_BUDGET_USD", 0.02)
    usage.record("claude-opus-5-5", 0, 1000, device=DEVICE)          # $0.02 spent
    r = client.post("/v1/flashcards", json={"subject": "Biology", "topic": "Cells", "grade": 10}, headers=AUTH)
    assert r.status_code == 402 and "budget" in r.json()["detail"]
    u = client.get("/v1/usage", headers=AUTH).json()
    assert u == {"today_usd": 0.02, "budget_usd": 0.02, "requests_today": 1}
    assert client.get("/v1/usage").status_code == 401


def test_zero_budget_means_unlimited(monkeypatch):
    monkeypatch.setattr(usage, "DAILY_BUDGET_USD", 0)
    usage.record("claude-opus-5-5", 10_000_000, 10_000_000, device=DEVICE)
    assert not usage.over_budget(DEVICE)


def test_refusal_is_recorded_and_mapped(fake_claude):
    fake_claude({}, stop="refusal")
    r = client.post("/v1/exam", json={"subject": "History", "topic": "x", "grade": 12}, headers=AUTH)
    assert r.status_code == 422
    assert usage.spent_today(DEVICE)[1] == 1     # declined calls still count against the budget


def test_days_follow_addis_ababa_midnight():
    # 2026-11-07 21:30 UTC = 2026-11-08 00:30 in Addis Ababa (UTC+3)
    assert usage.today(1794087000) == "2026-11-08"
