"""Offline tests: auth, rate limit, validation, and the AI wiring (Claude is mocked)."""
import os

os.environ["DEVICE_TOKENS"] = "t" * 32
os.environ["RATE_LIMIT_PER_MIN"] = "3"
os.environ.setdefault("ANTHROPIC_API_KEY", "test-not-used")

from fastapi.testclient import TestClient  # noqa: E402

import ai  # noqa: E402
import app as server  # noqa: E402

client = TestClient(server.app)
AUTH = {"Authorization": "Bearer " + "t" * 32}
CALLS: list[dict] = []


def _q(i):
    return {"type": "mcq", "stem": f"q{i}", "options": ["a", "b", "c", "d"], "answer": "A", "numeric_answer": 0,
            "tolerance": 0, "unit": "", "explanation": "", "topic": "t", "difficulty": "standard", "marks": 1}


async def fake_structured(content, schema, **kw):
    CALLS.append({"content": content, "schema": schema, **kw})
    if "sections" in schema["properties"]:
        return {"sections": [{"grade": 12, "unit": "U1", "topics": "a, b", "count": 40},
                             {"grade": 11, "unit": "U2", "topics": "c", "count": 20}]}
    if "minutes" in schema["properties"] and "Write exactly" in str(content):
        n = int(str(content).split("Write exactly ")[1].split()[0])
        return {"title": "t", "minutes": 0, "questions": [_q(i) for i in range(n)]}
    if "score" in schema["properties"]:
        return {"score": 99, "verdict": "correct", "feedback": "ok", "mistakes": []}
    return {"title": "t", "minutes": 10, "questions": []}


ai.structured = fake_structured
server.ai.structured = fake_structured


def setup_function():
    server._hits.clear()
    CALLS.clear()


def test_health():
    assert client.get("/healthz").json() == {"ok": True}


def test_rejects_missing_and_wrong_token():
    body = {"subject": "Physics", "topic": "Kinematics", "grade": 11}
    assert client.post("/v1/exam", json=body).status_code == 401
    assert client.post("/v1/exam", json=body, headers={"Authorization": "Bearer nope"}).status_code == 401


def test_exam_ok_and_rate_limited():
    body = {"subject": "Physics", "topic": "Kinematics", "grade": 11, "types": ["mcq", "graph"]}
    for _ in range(3):
        assert client.post("/v1/exam", json=body, headers=AUTH).status_code == 200
    assert client.post("/v1/exam", json=body, headers=AUTH).status_code == 429
    assert "Kinematics" in CALLS[0]["content"] and CALLS[0]["effort"] == "high"


def test_validation_blocks_bad_input():
    bad = {"subject": "Astrology", "topic": "x", "grade": 11}
    assert client.post("/v1/exam", json=bad, headers=AUTH).status_code == 422
    bad = {"subject": "Physics", "topic": "x", "grade": 7}
    assert client.post("/v1/exam", json=bad, headers=AUTH).status_code == 422


def test_grade_clamps_score_and_sends_image():
    body = {"subject": "Mathematics", "question": "Sketch y=x^2", "reference_answer": "parabola",
            "marks": 4, "image_b64": "aGVsbG8="}
    r = client.post("/v1/grade", json=body, headers=AUTH)
    assert r.status_code == 200 and r.json()["score"] == 4
    assert CALLS[0]["content"][0]["type"] == "image"


def test_ai_error_maps_to_status():
    async def boom(*a, **k):
        raise ai.AIError(422, "declined")
    server.ai.structured = boom
    try:
        r = client.post("/v1/flashcards", json={"subject": "Biology", "topic": "Cells", "grade": 10},
                        headers=AUTH)
        assert r.status_code == 422 and r.json()["detail"] == "declined"
    finally:
        server.ai.structured = fake_structured


def test_body_size_limit():
    r = client.post("/v1/extract", headers={**AUTH, "content-length": str(40_000_000)}, content=b"{}")
    assert r.status_code == 413


def test_plan_sections_sums_and_splits():
    secs = server.plan_sections([{"grade": 12, "unit": "A", "topics": "x", "count": 70},
                                 {"grade": 11, "unit": "B", "topics": "y", "count": 10}], 100)
    assert sum(s["count"] for s in secs) == 100
    assert all(1 <= s["count"] <= server.SECTION_MAX for s in secs)
    assert server.plan_sections([], 30)[0]["count"] == 25  # fallback section, split at 25


def test_mock_paper_builds_full_paper():
    body = {"subject": "Chemistry", "stream": "natural", "count": 60}
    r = client.post("/v1/mock-paper", json=body, headers=AUTH)
    assert r.status_code == 200, r.text
    assert len(r.json()["questions"]) == 60
    assert CALLS[0]["effort"] == "low"  # blueprint call is cheap
    assert client.post("/v1/mock-paper", json={**body, "count": 500}, headers=AUTH).status_code == 422
