"""Smoke test for a deployed Tibeb server: calls every AI feature once with real Claude and prints
pass/fail plus what the run cost. Uses only the Python standard library, so it runs anywhere.

The keys are read from environment variables (never pass them as arguments: those end up in shell history):

    export TIBEB_DEVICE_TOKEN=...   # one of DEVICE_TOKENS from server/.env
    export TIBEB_ADMIN_TOKEN=...    # ADMIN_TOKEN from server/.env (optional: checks the admin API too)
    python smoke_test.py https://tibeb.example.com          # ~8 AI calls, roughly US$0.50-1.50
    python smoke_test.py https://tibeb.example.com --full   # also a mock paper + queues a question pack

Exit code 0 means every check passed.
"""
import argparse
import base64
import json
import os
import struct
import sys
import time
import urllib.error
import urllib.request
import zlib
from typing import Callable

# send(method, path, body, token) -> (status, json_or_text, retry_after_seconds)
Send = Callable[[str, str, dict | None, str | None], tuple[int, object, float]]

TOPIC = {"subject": "Physics", "topic": "Newton's second law", "grade": 11,
         "context": ["Newton's second law: the net force on a body equals its mass times its acceleration, "
                     "F = m a. Force is measured in newtons (N); 1 N = 1 kg m/s^2. Weight W = m g with "
                     "g = 9.8 m/s^2. If several forces act, add them as vectors first, then divide by mass."]}


def http_sender(base: str, timeout: float = 600) -> Send:
    base = base.rstrip("/")

    def send(method, path, body, token):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(base + path, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        req.add_header("User-Agent", "tibeb-smoke-test/1")
        if token:
            req.add_header("Authorization", "Bearer " + token)
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.status, json.loads(r.read() or b"null"), 0.0
        except urllib.error.HTTPError as e:
            raw = e.read().decode(errors="replace")
            try:
                payload = json.loads(raw)
            except ValueError:
                payload = raw[:300]
            return e.code, payload, float(e.headers.get("Retry-After") or 0)
    return send


# ---------- tiny test files, generated so the script needs nothing else ----------

def tiny_pdf() -> bytes:
    """A one-page PDF with a short physics passage and one exercise (for /v1/extract)."""
    lines = ["Unit 2: Dynamics", "Newton's second law states that F = m a.",
             "A net force of 1 newton gives a 1 kg mass an acceleration of 1 m/s^2.",
             "Weight is W = m g, where g = 9.8 m/s^2.", "",
             "Exercise 1. A 4 kg box is pushed with a net force of 10 N.",
             "What is its acceleration?  A. 0.4 m/s^2  B. 2.5 m/s^2  C. 14 m/s^2  D. 40 m/s^2"]
    text = "BT /F1 12 Tf 60 760 Td 16 TL " + " ".join(
        "(" + ln.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)") + ") Tj T*" for ln in lines) + " ET"
    objs = [b"<< /Type /Catalog /Pages 2 0 R >>", b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> "
            b"/Contents 4 0 R >>",
            b"<< /Length %d >>\nstream\n" % len(text) + text.encode() + b"\nendstream",
            b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    out, offsets = bytearray(b"%PDF-1.4\n"), []
    for i, o in enumerate(objs, start=1):
        offsets.append(len(out))
        out += b"%d 0 obj\n" % i + o + b"\nendobj\n"
    xref = len(out)
    out += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objs) + 1)
    out += b"".join(b"%010d 00000 n \n" % off for off in offsets)
    out += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objs) + 1, xref)
    return bytes(out)


def graph_png(size: int = 240) -> bytes:
    """A hand-drawn-looking sketch of y = 2x with axes (for photo grading on /v1/grade)."""
    px = [[255] * size for _ in range(size)]
    m = 20

    def dot(x, y, r=1):
        for dx in range(-r, r + 1):
            for dy in range(-r, r + 1):
                if 0 <= x + dx < size and 0 <= y + dy < size:
                    px[y + dy][x + dx] = 0
    for i in range(m, size - m):
        dot(m, i), dot(i, size - m)                    # y axis, x axis
    for t in range(0, size - 2 * m):                    # y = 2x from the origin, slope 2 in pixel space
        x, y = m + t // 2, size - m - t
        dot(x, y, 2)
    for k in range(1, 6):                               # ticks on the x axis
        x = m + k * (size - 2 * m) // 10
        for j in range(6):
            dot(x, size - m + j - 3)
    raw = b"".join(b"\x00" + bytes(row) for row in px)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 0, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def b64(data: bytes) -> str:
    return base64.b64encode(data).decode()


# ---------- checks ----------

def nonempty(s) -> bool:
    return isinstance(s, str) and len(s.strip()) > 0


def checks(full: bool) -> list[tuple[str, str, str, dict, Callable[[dict], str | None]]]:
    """(name, method, path, body, verify) - verify returns None when fine, else what is wrong."""
    def exam_ok(r):
        qs = r.get("questions") or []
        if len(qs) < 3:
            return f"expected 3+ questions, got {len(qs)}"
        bad = [q for q in qs if q.get("type") == "mcq" and (len(q.get("options") or []) < 4 or q.get("answer") not in "ABCD")]
        return f"{len(bad)} malformed multiple-choice questions" if bad else None

    out = [
        ("Notes (Haiku)", "POST", "/v1/notes", TOPIC | {"style": "summary"},
         lambda r: None if nonempty(r.get("markdown")) else "no notes text"),
        ("Notes in Amharic", "POST", "/v1/notes", TOPIC | {"style": "cheat_sheet", "language": "am"},
         lambda r: None if any("ሀ" <= ch <= "፿" for ch in r.get("markdown", "")) else "no Ge'ez script in reply"),
        ("Flashcards (Haiku)", "POST", "/v1/flashcards", TOPIC | {"count": 5},
         lambda r: None if len(r.get("cards") or []) >= 3 else f"only {len(r.get('cards') or [])} cards"),
        ("Hard exam (mcq + numeric)", "POST", "/v1/exam", TOPIC | {"count": 4, "types": ["mcq", "numeric"]}, exam_ok),
        ("Tutor chat", "POST", "/v1/explain",
         {"subject": "Physics", "question": "A 4 kg box is pushed with a net force of 10 N. What is its acceleration?",
          "options": ["0.4 m/s^2", "2.5 m/s^2", "14 m/s^2", "40 m/s^2"], "correct_answer": "B",
          "student_answer": "D", "turns": [{"role": "user", "text": "Why is my answer D wrong?"}]},
         lambda r: None if nonempty(r.get("reply")) else "empty reply"),
        ("Grade typed calculation", "POST", "/v1/grade",
         {"subject": "Physics", "question": "A 4 kg box is pushed with a net force of 10 N. Find its acceleration.",
          "reference_answer": "a = F/m = 10/4 = 2.5 m/s^2", "marks": 3, "student_text": "a = F/m = 10/4 = 2.5 m/s^2"},
         lambda r: None if r.get("score", 0) >= 2 else f"correct work scored only {r.get('score')}/3"),
        ("Grade graph photo", "POST", "/v1/grade",
         {"subject": "Mathematics", "question": "Sketch the graph of y = 2x for 0 <= x <= 5.",
          "reference_answer": "A straight line through the origin with positive slope 2, drawn on labelled axes.",
          "marks": 4, "image_b64": b64(graph_png()), "image_mime": "image/png"},
         lambda r: "the photo was reported unreadable" if r.get("verdict") == "unreadable" else None),
        ("Read uploaded PDF", "POST", "/v1/extract",
         {"mime": "application/pdf", "data_b64": b64(tiny_pdf()), "subject": "Physics",
          "title": "Smoke test chapter", "doc_type": "textbook", "grade": 11},
         lambda r: None if r.get("chunks") and r.get("questions") else "no chunks or no questions found"),
    ]
    if full:
        out.append(("National mock paper", "POST", "/v1/mock-paper",
                    {"subject": "Physics", "stream": "natural", "count": 10, "context": TOPIC["context"]},
                    lambda r: None if len(r.get("questions") or []) >= 10 else f"{len(r.get('questions') or [])}/10 questions"))
        out.append(("Queue question pack (batch)", "POST", "/v1/packs",
                    {"subject": "Physics", "count": 20, "context": TOPIC["context"]},
                    lambda r: None if r.get("pack_id") else "no pack id returned"))
    return out


def call(send: Send, method, path, body, token, wait_on_limit=True):
    status, payload, retry = send(method, path, body, token)
    if status == 429 and wait_on_limit:                     # the per-minute rate limit: wait once, then retry
        time.sleep(min(retry or 61, 90))
        status, payload, retry = send(method, path, body, token)
    return status, payload


def run(send: Send, device_token: str, admin_token: str | None, full: bool = False, out=print) -> bool:
    ok = True

    def report(name, passed, detail=""):
        nonlocal ok
        ok &= passed
        out(f"  {'PASS' if passed else 'FAIL'}  {name}{('  -  ' + detail) if detail else ''}")

    out("Server")
    status, _ = call(send, "GET", "/healthz", None, None)
    report("Server reachable", status == 200, "" if status == 200 else f"HTTP {status}")
    if status != 200:
        return False
    status, _ = call(send, "GET", "/v1/usage", None, "not-a-real-token-" + "x" * 16)
    report("Rejects unknown phones", status == 401, f"HTTP {status}")
    status, before = call(send, "GET", "/v1/usage", None, device_token)
    report("Accepts this phone's token", status == 200, "" if status == 200 else f"HTTP {status}: {before}")
    if status != 200:
        return False

    out("\nAI features (real Claude calls)")
    for name, method, path, body, verify in checks(full):
        t0 = time.monotonic()
        status, payload = call(send, method, path, body, device_token)
        secs = time.monotonic() - t0
        if status != 200:
            detail = payload.get("detail") if isinstance(payload, dict) else payload
            report(name, False, f"HTTP {status}: {detail}")
            continue
        problem = verify(payload if isinstance(payload, dict) else {})
        report(name, problem is None, problem or f"{secs:.0f}s")

    status, after = call(send, "GET", "/v1/usage", None, device_token)
    if status == 200:
        spent = after["today_usd"] - before["today_usd"]
        budget = after["budget_usd"]
        out(f"\nCost of this run: about US${spent:.3f}  (today so far ${after['today_usd']:.2f}"
            + (f" of the ${budget:.2f} daily budget)" if budget else ", no daily budget set)"))
        report("Usage is being recorded", spent > 0, "" if spent > 0 else "spend did not increase")

    if admin_token:
        out("\nAdmin API (Windows admin app)")
        status, devices = call(send, "GET", "/v1/admin/devices", None, admin_token)
        n = len(devices.get("devices", [])) if isinstance(devices, dict) else 0
        report("Admin key works", status == 200, f"{n} device(s) visible" if status == 200 else f"HTTP {status}")
        status, _ = call(send, "GET", "/v1/admin/devices", None, device_token, wait_on_limit=False)
        report("Phone token cannot open the admin API", status == 401, f"HTTP {status}")
    else:
        out("\n(Admin API skipped: set TIBEB_ADMIN_TOKEN to check it too.)")

    out("\n" + ("ALL CHECKS PASSED" if ok else "SOME CHECKS FAILED - see above"))
    return ok


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("url", help="server address, e.g. https://tibeb.example.com")
    p.add_argument("--full", action="store_true", help="also a 10-question mock paper and a batch question pack")
    a = p.parse_args(argv)
    if not a.url.startswith("https://") and not a.url.startswith(("http://localhost", "http://127.0.0.1")):
        p.error("use the https:// address (plain http is only allowed for localhost)")
    token = os.environ.get("TIBEB_DEVICE_TOKEN", "").strip()
    if not token:
        p.error("set TIBEB_DEVICE_TOKEN to one of the DEVICE_TOKENS in server/.env")
    print(f"Testing {a.url}\n")
    return 0 if run(http_sender(a.url), token, os.environ.get("TIBEB_ADMIN_TOKEN", "").strip() or None, a.full) else 1


if __name__ == "__main__":
    sys.exit(main())
