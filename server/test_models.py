"""Per-task models: Haiku 5.5 for flashcards/notes, Opus 5.5 elsewhere; Haiku refusals retried on Opus."""
import pytest
from fastapi.testclient import TestClient

import ai
import app as server
import usage

client = TestClient(server.app)
AUTH = {"Authorization": "Bearer " + "t" * 32}


def test_simple_tasks_use_haiku_without_fallback_params(fake_claude):
    fake = fake_claude({"cards": []})
    assert client.post("/v1/flashcards", json={"subject": "Biology", "topic": "Cells", "grade": 10}, headers=AUTH).status_code == 200
    req = fake.requests[0]
    assert req["model"] == "claude-haiku-5-5"
    assert "fallbacks" not in req and "betas" not in req       # Haiku 5.5 has no server-side fallback
    assert req["thinking"] == {"type": "adaptive"} and req["output_config"]["effort"] == "low"


def test_notes_use_haiku_and_exams_stay_on_opus(fake_claude):
    fake = fake_claude(script=[({"title": "t", "markdown": "m", "key_formulas": []}, "end_turn")])
    client.post("/v1/notes", json={"subject": "Physics", "topic": "Waves", "grade": 11}, headers=AUTH)
    assert fake.requests[-1]["model"] == "claude-haiku-5-5"
    fake = fake_claude({"title": "t", "minutes": 10, "questions": []})
    client.post("/v1/exam", json={"subject": "Physics", "topic": "Waves", "grade": 11}, headers=AUTH)
    req = fake.requests[-1]
    assert req["model"] == "claude-opus-5-5" and req["fallbacks"] == "default"


def test_haiku_refusal_is_retried_once_on_opus(fake_claude):
    fake = fake_claude(script=[({}, "refusal"), ({"cards": [{"front": "f", "back": "b", "topic": "t"}]}, "end_turn")])
    r = client.post("/v1/flashcards", json={"subject": "Biology", "topic": "Cells", "grade": 10}, headers=AUTH)
    assert r.status_code == 200 and r.json()["cards"][0]["front"] == "f"
    assert [q["model"] for q in fake.requests] == ["claude-haiku-5-5", "claude-opus-5-5"]
    models = [m for (m,) in usage._conn().execute("SELECT model FROM usage ORDER BY ts")]
    assert models == ["claude-haiku-5-5", "claude-opus-5-5"]      # both attempts are billed and recorded


def test_owner_can_switch_back_to_opus(monkeypatch, fake_claude):
    monkeypatch.setitem(ai.TASK_MODELS, "flashcards", "claude-opus-5-5")
    fake = fake_claude({"cards": []})
    client.post("/v1/flashcards", json={"subject": "Biology", "topic": "Cells", "grade": 10}, headers=AUTH)
    assert fake.requests[0]["model"] == "claude-opus-5-5" and fake.requests[0]["fallbacks"] == "default"


def test_haiku_pricing_and_long_prompt_tier():
    assert usage.cost("claude-haiku-5-5", 1_000_000, 1_000_000) == pytest.approx(0.50 + 2.50)  # >100K-token prompt: long card
    assert usage.cost("claude-haiku-5-5", 100_000, 100_000) == pytest.approx((100_000 * 0.10 + 100_000 * 0.50) / 1e6)
