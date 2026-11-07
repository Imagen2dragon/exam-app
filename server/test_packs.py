"""Overnight question packs through a fake Message Batches API."""
import json
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

import ai
import app as server
import packs
import usage

client = TestClient(server.app)
AUTH = {"Authorization": "Bearer " + "t" * 32}
Q = {"type": "mcq", "stem": "q", "options": ["a", "b", "c", "d"], "answer": "A", "numeric_answer": 0, "tolerance": 0,
     "unit": "", "explanation": "", "topic": "t", "difficulty": "standard", "marks": 1}


class FakeBatches:
    def __init__(self):
        self.created, self.status, self.results_calls = [], "in_progress", 0

    async def create(self, requests):
        self.created = list(requests)
        return SimpleNamespace(id="msgbatch_1")

    async def retrieve(self, batch_id):
        return SimpleNamespace(processing_status=self.status)

    async def results(self, batch_id):
        self.results_calls += 1

        async def gen():
            for i, req in enumerate(self.created):
                if i == 1:   # one section errored
                    yield SimpleNamespace(custom_id=req["custom_id"], result=SimpleNamespace(type="errored"))
                    continue
                n = int(req["params"]["messages"][0]["content"][1]["text"].split("exactly ")[1].split()[0])
                stop = "refusal" if i == 2 else "end_turn"
                msg = SimpleNamespace(model="claude-opus-5-5", stop_reason=stop,
                                      usage=SimpleNamespace(input_tokens=1000, output_tokens=4000,
                                                            cache_creation_input_tokens=0, cache_read_input_tokens=9000),
                                      content=[SimpleNamespace(type="text", text=json.dumps({"title": "t", "minutes": 0, "questions": [Q] * n}))])
                yield SimpleNamespace(custom_id=req["custom_id"], result=SimpleNamespace(type="succeeded", message=msg))
        return gen()


@pytest.fixture
def batches(monkeypatch):
    fake = FakeBatches()
    monkeypatch.setattr(ai.client.messages, "batches", fake)
    server._hits.clear()
    return fake


def test_split_spreads_topics_and_caps_sections():
    assert packs.split(60, ["Waves", "Optics"]) == [("Waves", 25), ("Waves", 5), ("Optics", 25), ("Optics", 5)]
    assert packs.split(20, []) == [("the whole Grade 9-12 syllabus", 20)]
    assert sum(n for _, n in packs.split(200, ["a", "b", "c"])) == 200


def test_pack_lifecycle(batches):
    r = client.post("/v1/packs", json={"subject": "Physics", "count": 100, "topics": ["Waves", "Optics"],
                                       "context": ["Snell's law " * 200]}, headers=AUTH)
    assert r.status_code == 200, r.text
    pack = r.json()
    assert pack["status"] == "processing" and pack["sections"] == 4
    params = [q["params"] for q in batches.created]
    assert all("fallbacks" not in p for p in params)                          # not allowed in batches
    shared = [p["messages"][0]["content"][0] for p in params]
    assert all(s == shared[0] for s in shared) and shared[0]["cache_control"] == {"type": "ephemeral"}

    assert client.get(f"/v1/packs/{pack['pack_id']}", headers=AUTH).json()["status"] == "processing"
    batches.status = "ended"
    done = client.get(f"/v1/packs/{pack['pack_id']}", headers=AUTH).json()
    # 4 sections of 25: #1 errored, #2 refused -> 2 sections x 25 questions arrive
    assert done["status"] == "ready" and len(done["questions"]) == 50
    # billed at half price: 3 successful responses recorded (the refusal still costs), errored one not
    rows = usage._conn().execute("SELECT batch, cost_usd FROM usage WHERE endpoint = '/v1/packs'").fetchall()
    assert len(rows) == 3 and all(b == 1 for b, _ in rows)
    assert rows[0][1] == pytest.approx(usage.cost("claude-opus-5-5", 1000, 4000, 0, 9000, batch=False) / 2)
    # results are fetched once, then served from the server's store
    again = client.get(f"/v1/packs/{pack['pack_id']}", headers=AUTH).json()
    assert len(again["questions"]) == 50 and batches.results_calls == 1


def test_other_devices_cannot_read_a_pack(batches, monkeypatch):
    pack = client.post("/v1/packs", json={"subject": "Physics", "count": 20}, headers=AUTH).json()
    other = "o" * 32
    monkeypatch.setattr(server, "TOKENS", server.TOKENS + [other])
    r = client.get(f"/v1/packs/{pack['pack_id']}", headers={"Authorization": "Bearer " + other})
    assert r.status_code == 404
    assert client.get("/v1/packs/does-not-exist", headers=AUTH).status_code == 404


def test_pack_size_limits(batches):
    assert client.post("/v1/packs", json={"subject": "Physics", "count": 19}, headers=AUTH).status_code == 422
    server._hits.clear()
    assert client.post("/v1/packs", json={"subject": "Physics", "count": 201}, headers=AUTH).status_code == 422
