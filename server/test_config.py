"""/v1/config hands the phone its backup settings - and never a secret Supabase key."""
import base64
import json

import pytest
from fastapi.testclient import TestClient

import app as server

client = TestClient(server.app)
PHONE = {"Authorization": "Bearer " + "t" * 32}


def jwt(role):
    part = base64.urlsafe_b64encode(json.dumps({"role": role, "iss": "supabase"}).encode()).decode().rstrip("=")
    return f"eyJhbGciOiJIUzI1NiJ9.{part}.c2lnbmF0dXJl"


@pytest.fixture(autouse=True)
def clean():
    server._hits.clear()


def configure(monkeypatch, url, key):
    monkeypatch.setattr(server, "SUPABASE_URL", url)
    monkeypatch.setattr(server, "SUPABASE_KEY", key)


def test_needs_a_device_token():
    assert client.get("/v1/config").status_code == 401


def test_backup_off_when_not_configured(monkeypatch):
    configure(monkeypatch, "", "")
    assert client.get("/v1/config", headers=PHONE).json() == {"backup": None}


@pytest.mark.parametrize("key", ["sb_publishable_abc123", jwt("anon")])
def test_public_keys_are_handed_out(monkeypatch, key):
    configure(monkeypatch, "https://abcd.supabase.co", key)
    assert client.get("/v1/config", headers=PHONE).json()["backup"] == {
        "url": "https://abcd.supabase.co", "key": key, "bucket": "backups"}


@pytest.mark.parametrize("key", ["sb_secret_abc123", jwt("service_role"), "not-a-key", "a.b.c"])
def test_secret_or_unknown_keys_are_never_handed_out(monkeypatch, key):
    configure(monkeypatch, "https://abcd.supabase.co", key)
    assert client.get("/v1/config", headers=PHONE).json() == {"backup": None}


def test_plain_http_supabase_url_is_refused(monkeypatch):
    configure(monkeypatch, "http://abcd.supabase.co", "sb_publishable_abc123")
    assert client.get("/v1/config", headers=PHONE).json() == {"backup": None}
