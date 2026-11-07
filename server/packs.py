"""Overnight question packs: many questions generated through the Message Batches API at 50% of
the normal price. Results usually arrive within an hour (at most 24 h); the app collects them later
and stores them on the phone for offline practice."""
import json
import secrets
import time

import ai
import usage

SECTION = 25            # questions per batch request (keeps each output well under the token limit)
BATCH_SCHEMA = None     # set from schemas.EXAM_SCHEMA by app.py (avoids a circular import)


def _db():
    db = usage._conn()
    db.execute("""CREATE TABLE IF NOT EXISTS packs (
        id TEXT PRIMARY KEY, device TEXT, batch_id TEXT, subject TEXT, count INTEGER,
        created REAL, status TEXT, questions TEXT)""")
    return db


def split(count: int, topics: list[str]) -> list[tuple[str, int]]:
    """Spread `count` questions over the topics in sections of at most SECTION questions."""
    topics = [t for t in topics if t.strip()] or ["the whole Grade 9-12 syllabus"]
    per_topic = [count // len(topics) + (1 if i < count % len(topics) else 0) for i in range(len(topics))]
    out = []
    for topic, n in zip(topics, per_topic):
        while n > 0:
            out.append((topic, min(n, SECTION)))
            n -= SECTION
    return out


def request_params(shared: dict, topic: str, n: int, model: str) -> dict:
    """One batch request. Note: the server-side `fallbacks` parameter is not allowed in batches."""
    return {
        "model": model,
        "max_tokens": 32_000,
        "system": ai.SYSTEM_BLOCKS,
        "thinking": {"type": "adaptive"},
        "output_config": {"effort": "medium", "format": {"type": "json_schema", "schema": BATCH_SCHEMA}},
        "messages": [{"role": "user", "content": [shared, {"type": "text", "text":
            f"Write exactly {n} questions for this section. Topic: {topic}."}]}],
    }


async def create(device: str, subject: str, count: int, topics: list[str], shared: dict) -> dict:
    sections = split(count, topics)
    pack_id = secrets.token_urlsafe(9)
    requests = [{"custom_id": f"{pack_id}-{i}", "params": request_params(shared, t, n, ai.MODEL)}
                for i, (t, n) in enumerate(sections)]
    batch = await ai.client.messages.batches.create(requests=requests)
    with usage._lock:
        _db().execute("INSERT INTO packs VALUES (?,?,?,?,?,?,?,?)",
                      (pack_id, device, batch.id, subject, count, time.time(), "processing", None))
        _db().commit()
    return {"pack_id": pack_id, "status": "processing", "sections": len(sections)}


async def collect(device: str, pack_id: str) -> dict | None:
    """Status of a pack; downloads and stores the results once, the first time the batch has ended."""
    with usage._lock:
        row = _db().execute("SELECT batch_id, subject, count, status, questions FROM packs WHERE id = ? AND device = ?",
                            (pack_id, device)).fetchone()
    if row is None:
        return None                      # unknown pack, or another device's pack
    batch_id, subject, count, status, stored = row
    if status in ("ready", "failed"):
        return {"pack_id": pack_id, "subject": subject, "status": status, "questions": json.loads(stored or "[]")}
    batch = await ai.client.messages.batches.retrieve(batch_id)
    if batch.processing_status != "ended":
        return {"pack_id": pack_id, "subject": subject, "status": "processing", "questions": []}

    questions = []
    async for result in await ai.client.messages.batches.results(batch_id):
        if result.result.type != "succeeded":
            continue                     # errored / expired / canceled sections are skipped
        msg = result.result.message
        u = msg.usage
        usage.record(msg.model, u.input_tokens or 0, u.output_tokens or 0,
                     getattr(u, "cache_creation_input_tokens", 0) or 0, getattr(u, "cache_read_input_tokens", 0) or 0,
                     batch=True, device=device, endpoint="/v1/packs")
        if msg.stop_reason != "end_turn":
            continue                     # refused or cut off: no server-side fallback in batches
        text = next((b.text for b in msg.content if b.type == "text"), None)
        try:
            qs = json.loads(text or "{}").get("questions", [])
        except json.JSONDecodeError:
            continue
        questions += [q for q in qs if q.get("type") == "mcq" and len(q.get("options", [])) == 4]
    status = "ready" if questions else "failed"
    with usage._lock:
        _db().execute("UPDATE packs SET status = ?, questions = ? WHERE id = ?", (status, json.dumps(questions), pack_id))
        _db().commit()
    return {"pack_id": pack_id, "subject": subject, "status": status, "questions": questions}
