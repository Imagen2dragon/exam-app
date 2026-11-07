"""Prompt caching: identical shared prefixes with breakpoints, and fan-out after the first stream starts."""
import asyncio

from fastapi.testclient import TestClient

import ai
import app as server

client = TestClient(server.app)
AUTH = {"Authorization": "Bearer " + "t" * 32}
Q = {"type": "mcq", "stem": "q", "options": ["a", "b", "c", "d"], "answer": "A", "numeric_answer": 0, "tolerance": 0,
     "unit": "", "explanation": "", "topic": "t", "difficulty": "standard", "marks": 1}


def test_system_prompt_is_a_cache_breakpoint(fake_claude):
    fake = fake_claude({"cards": []})
    client.post("/v1/flashcards", json={"subject": "Biology", "topic": "Cells", "grade": 10}, headers=AUTH)
    system = fake.requests[0]["system"]
    assert system[0]["text"] == ai.SYSTEM and system[0]["cache_control"] == {"type": "ephemeral"}


def test_mock_sections_share_one_cached_prefix_and_first_goes_alone(fake_claude):
    plan = {"sections": [{"grade": 12, "unit": f"U{i}", "topics": f"t{i}", "count": 20} for i in range(3)]}
    fake = fake_claude(script=[(plan, "end_turn")] + [({"title": "t", "minutes": 0, "questions": [Q] * 20}, "end_turn")] * 3)
    r = client.post("/v1/mock-paper", json={"subject": "Physics", "stream": "natural", "count": 60,
                                            "context": ["Newton's laws " * 300]}, headers=AUTH)
    assert r.status_code == 200, r.text
    sections = fake.requests[1:]
    assert len(sections) == 3
    shared = [q["messages"][0]["content"][0] for q in sections]
    assert all(b == shared[0] for b in shared) and shared[0]["cache_control"] == {"type": "ephemeral"}
    assert "Newton's laws" in shared[0]["text"]
    briefs = [q["messages"][0]["content"][1]["text"] for q in sections]
    assert "U0" in briefs[0]                       # the first section is sent before any other
    assert len(set(briefs)) == 3 and all("cache_control" not in q["messages"][0]["content"][1] for q in sections)


def test_failed_first_section_does_not_hang_the_others(monkeypatch):
    plan = {"sections": [{"grade": 12, "unit": f"U{i}", "topics": "t", "count": 20} for i in range(3)]}
    calls = []

    async def fake(content, schema, **kw):
        calls.append(kw.get("started"))
        if "sections" in schema["properties"]:
            return plan
        if kw.get("started") is not None:              # the first section
            raise ai.AIError(502, "boom")
        await asyncio.sleep(0)
        return {"title": "t", "minutes": 0, "questions": [Q] * 20}

    monkeypatch.setattr(ai, "structured", fake)
    server._hits.clear()

    async def run():
        return await asyncio.wait_for(asyncio.to_thread(
            client.post, "/v1/mock-paper", json={"subject": "Physics", "stream": "natural", "count": 60}, headers=AUTH), 10)
    r = asyncio.run(run())
    assert r.status_code == 502 and len(calls) == 4   # blueprint + all three sections ran, nothing hung


def test_chat_caches_the_conversation_so_far(fake_claude):
    fake = fake_claude({"reply": "ok"})
    turns = [{"role": "user", "text": "Why?"}, {"role": "assistant", "text": "Because."}, {"role": "user", "text": "Why though?"}]
    body = {"subject": "Mathematics", "question": "q", "correct_answer": "C", "turns": turns}
    assert client.post("/v1/explain", json=body, headers=AUTH).status_code == 200
    msgs = fake.requests[0]["messages"]
    assert msgs[-1]["content"][0]["cache_control"] == {"type": "ephemeral"}
    assert isinstance(msgs[0]["content"], str) and isinstance(msgs[1]["content"], str)   # earlier turns unmarked
