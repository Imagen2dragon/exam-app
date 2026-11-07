"""The deploy smoke test itself, run against the app in-process with Claude mocked."""
import pytest
from fastapi.testclient import TestClient

import admin
import ai
import app as server
import smoke_test
import usage

TOKEN = "t" * 32
ADMIN_KEY = "A" * 40


def fake_reply(schema):
    props = schema["properties"]
    q = {"type": "mcq", "stem": "q", "options": ["a", "b", "c", "d"], "answer": "B", "numeric_answer": 0,
         "tolerance": 0, "unit": "", "explanation": "", "topic": "t", "difficulty": "hard", "marks": 1}
    if "chunks" in props:
        return {"summary": "s", "chunks": [{"unit": "2", "topic": "Dynamics", "text": "F = m a"}], "questions": [q]}
    if "sections" in props:
        return {"sections": [{"grade": 12, "unit": "U1", "topics": "a", "count": 10}]}
    if "markdown" in props:
        return {"title": "t", "markdown": "# ማስታወሻ F = m a", "key_formulas": ["F = m a"]}
    if "cards" in props:
        return {"cards": [{"front": "f", "back": "b", "topic": "t"}] * 5}
    if "reply" in props:
        return {"reply": "Because a = F/m."}
    if "score" in props:
        return {"score": 3, "verdict": "correct", "feedback": "ok", "mistakes": []}
    return {"title": "t", "minutes": 10, "questions": [q] * 10}


@pytest.fixture
def send(monkeypatch):
    async def structured(content, schema, **kw):
        usage.record("claude-opus-5-5", 1000, 500)          # contextvars carry the device, as in production
        return fake_reply(schema)
    monkeypatch.setattr(ai, "structured", structured)
    monkeypatch.setattr(server, "RATE", 100)
    monkeypatch.setattr(admin, "ADMIN_TOKEN", ADMIN_KEY)
    admin._admin_failures.clear()
    server._hits.clear()
    client = TestClient(server.app)

    def _send(method, path, body, token):
        r = client.request(method, path, json=body, headers={"Authorization": "Bearer " + token} if token else {})
        return r.status_code, r.json(), 0.0
    return _send


def test_all_checks_pass_on_a_healthy_server(send):
    lines = []
    assert smoke_test.run(send, TOKEN, ADMIN_KEY, out=lines.append)
    text = "\n".join(lines)
    assert "FAIL" not in text and "ALL CHECKS PASSED" in text and "Cost of this run" in text


def test_a_broken_feature_is_reported(send, monkeypatch):
    async def broken(content, schema, **kw):
        raise ai.AIError(502, "The AI service is unavailable. Try again later.")
    monkeypatch.setattr(ai, "structured", broken)
    lines = []
    assert not smoke_test.run(send, TOKEN, None, out=lines.append)
    assert any(line.startswith("  FAIL  Hard exam") and "502" in line for line in lines)


def test_wrong_device_token_stops_early(send):
    lines = []
    assert not smoke_test.run(send, "w" * 32, None, out=lines.append)
    assert any("FAIL  Accepts this phone's token" in line for line in lines)


def test_generated_files_are_valid():
    pdf, png = smoke_test.tiny_pdf(), smoke_test.graph_png()
    assert pdf.startswith(b"%PDF-1.4") and pdf.rstrip().endswith(b"%%EOF") and b"F = m a" in pdf
    assert png.startswith(b"\x89PNG\r\n\x1a\n") and b"IEND" in png


def test_refuses_plain_http_for_remote_servers(monkeypatch):
    monkeypatch.setenv("TIBEB_DEVICE_TOKEN", TOKEN)
    with pytest.raises(SystemExit):
        smoke_test.main(["http://tibeb.example.com"])
