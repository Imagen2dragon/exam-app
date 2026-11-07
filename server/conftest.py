import os

os.environ["DEVICE_TOKENS"] = "t" * 32
os.environ["RATE_LIMIT_PER_MIN"] = "3"
os.environ.setdefault("ANTHROPIC_API_KEY", "test-not-used")

import pytest  # noqa: E402

import usage  # noqa: E402


@pytest.fixture(autouse=True)
def fresh_usage_db(tmp_path, monkeypatch):
    """Every test gets its own empty usage database and the default budget."""
    usage.reset(str(tmp_path / "usage.db"))
    monkeypatch.setattr(usage, "DAILY_BUDGET_USD", 5.0)
    yield
    usage.reset()


import json  # noqa: E402
from types import SimpleNamespace  # noqa: E402

import ai  # noqa: E402


class FakeStream:
    """Stands in for client.beta.messages.stream(...). Plays `script` in order: each entry is
    (reply_dict, stop_reason). The final message reports the model that was requested."""
    def __init__(self, script, usage_fields):
        self.script, self.u, self.requests = list(script), usage_fields, []

    def __call__(self, **kwargs):
        self.requests.append(kwargs)
        return self

    async def __aenter__(self):
        return self

    async def __aexit__(self, *a):
        return False

    async def get_final_message(self):
        reply, stop = self.script.pop(0) if len(self.script) > 1 else self.script[0]
        u = {"input_tokens": 1000, "output_tokens": 500, "cache_creation_input_tokens": 0, "cache_read_input_tokens": 0} | self.u
        return SimpleNamespace(model=self.requests[-1]["model"], stop_reason=stop, usage=SimpleNamespace(**u),
                               content=[SimpleNamespace(type="text", text=json.dumps(reply))])


@pytest.fixture
def fake_claude(monkeypatch):
    """fake_claude(reply) or fake_claude(script=[(reply, stop), ...]); extra kwargs set usage token counts."""
    import app as server

    def install(reply=None, stop="end_turn", script=None, **usage_fields):
        fake = FakeStream(script or [(reply, stop)], usage_fields)
        monkeypatch.setattr(ai.client.beta.messages, "stream", fake)
        return fake
    server._hits.clear()
    return install
