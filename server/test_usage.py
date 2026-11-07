"""Cost tracking and daily budgets, exercising the real ai.structured with a fake Anthropic client."""
import json
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

import ai
import app as server
import usage

client = TestClient(server.app)
TOKEN = "t" * 32
AUTH = {"Authorization": "Bearer " + TOKEN}
DEVICE = usage.device_id(TOKEN)


class FakeStream:
    """Stands in for client.beta.messages.stream(...): returns a canned final message."""
    def __init__(self, reply: dict, stop="end_turn", **u):
        self.reply, self.stop, self.u = reply, stop, u
        self.requests = []

    def __call__(self, **kwargs):
        self.requests.append(kwargs)
        return self

    async def __aenter__(self):
        return self

    async def __aexit__(self, *a):
        return False

    async def get_final_message(self):
        u = {"input_tokens": 1000, "output_tokens": 500, "cache_creation_input_tokens": 0, "cache_read_input_tokens": 0} | self.u
        return SimpleNamespace(model="claude-opus-5-5", stop_reason=self.stop, usage=SimpleNamespace(**u),
                               content=[SimpleNamespace(type="text", text=json.dumps(self.reply))])


@pytest.fixture
def fake_claude(monkeypatch):
    def install(reply, **kw):
        fake = FakeStream(reply, **kw)
        monkeypatch.setattr(ai.client.beta.messages, "stream", fake)
        return fake
    server._hits.clear()
    return install


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
    assert n == 1 and spent == pytest.approx((2000 * 4 + 1000 * 20 + 3000 * 0.2) / 1e6)
    row = usage._conn().execute("SELECT device, endpoint FROM usage").fetchone()
    assert row == (DEVICE, "/v1/flashcards") and TOKEN not in str(row)


def test_parallel_mock_sections_are_all_attributed(fake_claude):
    blueprint = {"sections": [{"grade": 12, "unit": "U", "topics": "t", "count": 30}]}
    fake = fake_claude(blueprint)
    # one fake answers both shapes: blueprint first, then sections
    q = {"type": "mcq", "stem": "q", "options": ["a", "b", "c", "d"], "answer": "A", "numeric_answer": 0, "tolerance": 0,
         "unit": "", "explanation": "", "topic": "t", "difficulty": "standard", "marks": 1}
    replies = iter([blueprint] + [{"title": "t", "minutes": 0, "questions": [q] * 25}, {"title": "t", "minutes": 0, "questions": [q] * 5}])
    orig = fake.get_final_message

    async def next_reply():
        fake.reply = next(replies)
        return await orig()
    fake.get_final_message = next_reply
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
    r = client.post("/v1/notes", json={"subject": "History", "topic": "x", "grade": 12}, headers=AUTH)
    assert r.status_code == 422
    assert usage.spent_today(DEVICE)[1] == 1     # declined calls still count against the budget


def test_days_follow_addis_ababa_midnight():
    # 2026-11-07 21:30 UTC = 2026-11-08 00:30 in Addis Ababa (UTC+3)
    assert usage.today(1794087000) == "2026-11-08"
