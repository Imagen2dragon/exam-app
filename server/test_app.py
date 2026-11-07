"""Offline tests: auth, rate limit, validation, and the AI wiring (Claude is mocked)."""
import pytest
from fastapi.testclient import TestClient

import ai
import app as server

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
    if "reply" in schema["properties"]:
        return {"reply": "Because 6."}
    if "score" in schema["properties"]:
        return {"score": 99, "verdict": "correct", "feedback": "ok", "mistakes": []}
    return {"title": "t", "minutes": 10, "questions": []}


@pytest.fixture(autouse=True)
def fake_ai(monkeypatch):
    monkeypatch.setattr(ai, "structured", fake_structured)
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
    ai.structured = boom  # restored by the fake_ai fixture
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


def _extract_body(**kw):
    return {"mime": "image/jpeg", "subject": "Biology", "title": "Unit 2", "doc_type": "textbook", "grade": 11, **kw}


def test_extract_chapter_pages_in_order():
    pages = [{"mime": "image/jpeg", "data_b64": f"cGFnZ{i}"} for i in range(3)]
    r = client.post("/v1/extract", json=_extract_body(pages=pages), headers=AUTH)
    assert r.status_code == 200, r.text
    content = CALLS[0]["content"]
    labels = [b["text"] for b in content if b["type"] == "text" and b["text"].startswith("Page ")]
    assert labels == ["Page 1 of 3:", "Page 2 of 3:", "Page 3 of 3:"]
    assert [b["source"]["data"] for b in content if b["type"] == "image"] == [p["data_b64"] for p in pages]
    assert "consecutive pages" in content[-1]["text"]


def test_extract_single_file_still_works():
    r = client.post("/v1/extract", json=_extract_body(mime="application/pdf", data_b64="JVBERi0="), headers=AUTH)
    assert r.status_code == 200 and CALLS[0]["content"][0]["type"] == "document"


def test_extract_rejects_both_neither_and_too_many_pages():
    page = {"mime": "image/jpeg", "data_b64": "eA=="}

    def post(body):
        server._hits.clear()  # these are validation checks, not rate-limit checks
        return client.post("/v1/extract", json=body, headers=AUTH).status_code

    assert post(_extract_body()) == 422
    assert post(_extract_body(data_b64="eA==", pages=[page])) == 422
    assert post(_extract_body(pages=[page] * 31)) == 422
    assert post(_extract_body(pages=[{"mime": "application/pdf", "data_b64": "eA=="}])) == 422
    assert post(_extract_body(pages=[page] * 30)) == 200


def _explain(turns, **kw):
    body = {"subject": "Mathematics", "question": "lim (x^2-9)/(x-3) as x->3", "options": ["0", "3", "6", "inf"],
            "correct_answer": "C", "student_answer": "B", "turns": turns, **kw}
    server._hits.clear()
    return client.post("/v1/explain", json=body, headers=AUTH)


def test_explain_multi_turn_with_context_in_first_turn():
    turns = [{"role": "user", "text": "Why not 3?"}, {"role": "assistant", "text": "Factor first."},
             {"role": "user", "text": "Factor how?"}]
    r = _explain(turns, language="am")
    assert r.status_code == 200 and r.json() == {"reply": "Because 6."}
    msgs = CALLS[0]["messages"]
    assert [m["role"] for m in msgs] == ["user", "assistant", "user"]
    assert "Correct answer: C" in msgs[0]["content"] and "C. 6" in msgs[0]["content"]
    assert "The student answered: B" in msgs[0]["content"] and msgs[0]["content"].endswith("Why not 3?")
    assert "Amharic" in msgs[0]["content"] and msgs[2]["content"][0]["text"] == "Factor how?"


def test_explain_rejects_bad_turn_order_and_size():
    assert _explain([{"role": "assistant", "text": "hi"}]).status_code == 422
    assert _explain([{"role": "user", "text": "a"}, {"role": "user", "text": "b"}]).status_code == 422
    assert _explain([{"role": "user", "text": "a"}, {"role": "assistant", "text": "b"}]).status_code == 422
    assert _explain([{"role": "user", "text": "x" * 2001}]).status_code == 422
    assert _explain([]).status_code == 422
