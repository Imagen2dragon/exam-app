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
