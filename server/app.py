"""Tibeb backend: a thin, authenticated proxy between the Android app and Claude.

Why a backend: an API key inside an APK can be extracted by anyone who has the APK.
Here the key stays on the server; each phone gets its own revocable device token.
Run:  uvicorn app:app --host 0.0.0.0 --port 8080   (put HTTPS in front, e.g. Caddy)
"""
import asyncio
import hmac
import os
import time
from collections import defaultdict, deque

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse

import ai
import packs
import usage
from schemas import (BLUEPRINT_SCHEMA, EXAM_SCHEMA, EXPLAIN_SCHEMA, EXTRACT_SCHEMA, FLASH_SCHEMA,
                     GRADE_SCHEMA, NOTES_SCHEMA, ExamReq, ExplainReq, ExtractReq, FlashReq, GradeReq,
                     MockPaperReq, NotesReq, PackReq)

TOKENS = [t.strip() for t in os.environ.get("DEVICE_TOKENS", "").split(",") if len(t.strip()) >= 24]
RATE = int(os.environ.get("RATE_LIMIT_PER_MIN", "8"))
MAX_BODY = 30_000_000

app = FastAPI(title="Tibeb", docs_url=None, redoc_url=None, openapi_url=None)
_hits: dict[str, deque] = defaultdict(deque)


@app.middleware("http")
async def limit_body(request: Request, call_next):
    if int(request.headers.get("content-length") or 0) > MAX_BODY:
        return JSONResponse({"detail": "Upload too large (max ~20 MB)"}, status_code=413)
    resp = await call_next(request)
    resp.headers["Cache-Control"] = "no-store"
    resp.headers["X-Content-Type-Options"] = "nosniff"
    return resp


# These dependencies must stay `async def`: FastAPI runs plain `def` dependencies in a worker
# thread, and the context variables set here would then not reach the endpoint.
async def authenticated(request: Request, authorization: str = Header(default="")) -> str:
    token = authorization.removeprefix("Bearer ").strip()
    if not token or not any(hmac.compare_digest(token, t) for t in TOKENS):
        raise HTTPException(401, "Invalid device token")
    # Usage is recorded against a hash of the token, never the token itself.
    usage.current_device.set(usage.device_id(token))
    usage.current_endpoint.set(request.url.path)
    return token


async def device(token: str = Depends(authenticated)) -> str:
    """Auth + rate limit + daily AI budget, for every endpoint that calls Claude."""
    if usage.over_budget(usage.device_id(token)):
        raise HTTPException(402, "Today's AI budget for this device is used up. It resets at midnight.")
    q, now = _hits[token], time.monotonic()
    while q and now - q[0] > 60:
        q.popleft()
    if len(q) >= RATE:
        raise HTTPException(429, "Too many requests - wait a minute")
    q.append(now)
    return token


@app.exception_handler(ai.AIError)
async def ai_error(_: Request, e: ai.AIError):
    return JSONResponse({"detail": e.message}, status_code=e.status)


@app.get("/v1/usage")
async def my_usage(token: str = Depends(authenticated)):
    """Today's estimated AI spend for this device (shown in the app's Settings)."""
    spent, requests = usage.spent_today(usage.device_id(token))
    return {"today_usd": round(spent, 4), "budget_usd": usage.DAILY_BUDGET_USD, "requests_today": requests}


@app.get("/healthz")
async def health():
    return {"ok": True}


@app.post("/v1/extract")
async def extract(r: ExtractReq, _: str = Depends(device)):
    """Turn an uploaded textbook/exam into study chunks + a reusable question bank."""
    task = (
        f"Grade {r.grade} {r.subject} {r.doc_type.replace('_', ' ')} titled '{r.title}'.\n"
        "1. summary: 3-5 sentences on what this material covers.\n"
        "2. chunks: split the material into self-contained study chunks (150-400 words each), "
        "grouped by curriculum unit and topic. Keep definitions, laws, formulas and worked "
        "examples exactly. Skip covers, tables of contents and blank pages.\n"
        "3. questions: copy every exam-style question you find (past exams, end-of-unit "
        "exercises) faithfully. For mcq put the choices in options (without 'A.' prefixes) and "
        "the correct choice letter in answer; if the key is not shown, solve it yourself and "
        "say so in explanation. Use numeric_answer 0, tolerance 0 and unit '' when not numeric."
    )
    if r.pages:
        # A chapter photographed page by page: label each image so the reading order is unambiguous.
        content: list[dict] = []
        for i, page in enumerate(r.pages, start=1):
            content.append({"type": "text", "text": f"Page {i} of {len(r.pages)}:"})
            content.append(ai.doc_block(page.mime, page.data_b64))
        task += ("\nThese are phone photos of consecutive pages: read them in order, join text that "
                 "continues across pages, and ignore fingers, shadows and anything outside the page.")
    else:
        content = [ai.doc_block(r.mime, r.data_b64)]
    return await ai.structured(content + [{"type": "text", "text": task}],
                               EXTRACT_SCHEMA, effort="medium", max_tokens=64_000)


@app.post("/v1/notes")
async def notes(r: NotesReq, _: str = Depends(device)):
    styles = {
        "summary": "concise revision notes with headings and bullet points",
        "detailed": "detailed study notes with explanations and one worked example per idea",
        "formula_sheet": "a formula sheet: every formula, its variables, units and when to use it",
        "cheat_sheet": "a one-page last-night cheat sheet of the highest-yield facts and traps",
    }
    lang = "Write in English." if r.language == "en" else "Write in Amharic, keep technical terms in English."
    prompt = (f"Make {styles[r.style]} for grade {r.grade} {r.subject}, topic: {r.topic}. {lang} "
              f"Use Markdown. Flag common exam traps.\n\nStudy material:\n{r.context_text()}")
    return await ai.structured(prompt, NOTES_SCHEMA, effort="medium", task="notes")


@app.post("/v1/flashcards")
async def flashcards(r: FlashReq, _: str = Depends(device)):
    prompt = (f"Create {r.count} flashcards for grade {r.grade} {r.subject}, topic: {r.topic}. "
              "One fact, definition, formula or concept per card. Fronts are questions; backs are "
              f"short answers (max 40 words).\n\nStudy material:\n{r.context_text()}")
    return await ai.structured(prompt, FLASH_SCHEMA, effort="low", task="flashcards")


@app.post("/v1/exam")
async def exam(r: ExamReq, _: str = Depends(device)):
    level = {
        "standard": "national-exam level",
        "hard": "harder than the national exam: multi-step, mixing two concepts",
        "very_hard": "olympiad-style: multi-step reasoning, tricky distractors, edge cases",
    }[r.difficulty]
    past = "\n".join(f"- {q[:800]}" for q in r.past_questions) or "(none provided)"
    weak = ", ".join(r.weak_topics) or "none recorded"
    prompt = (
        f"Write an original {r.count}-question grade {r.grade} {r.subject} practice exam on: "
        f"{r.topic}. Difficulty: {level}. Allowed question types: {', '.join(r.types)}.\n"
        "Rules: mcq has exactly 4 options and answer is the letter A-D, with plausible "
        "distractors built from typical student mistakes. numeric gives numeric_answer, a "
        "sensible tolerance and unit, and answer as the worked final value. short expects a "
        "1-3 line written answer. graph asks the student to sketch a graph or diagram on paper "
        "(answer describes what a correct sketch must show: intercepts, asymptotes, shape, "
        "labels). Every question has a step-by-step explanation. For non-numeric questions set "
        "numeric_answer 0, tolerance 0, unit ''. For non-mcq set options []. Marks: mcq 1, "
        "numeric 2, short 2, graph 4. minutes: realistic time (national exam pace is ~1.2 min "
        "per mcq).\n"
        f"The student is weak at: {weak} - over-weight these.\n"
        f"Past exam questions for style reference (do NOT copy them):\n{past}\n\n"
        f"Study material:\n{r.context_text()}"
    )
    return await ai.structured(prompt, EXAM_SCHEMA, effort="high", max_tokens=64_000)


SECTION_MAX = 25  # questions per generation call: keeps each output well under the token limit


def plan_sections(sections: list[dict], total: int) -> list[dict]:
    """Normalise the AI blueprint so counts are positive, sum exactly to `total`, and no
    section is larger than SECTION_MAX (big ones are split)."""
    secs = [dict(s, count=max(1, int(s.get("count", 1)))) for s in sections if s.get("topics")]
    if not secs:
        secs = [{"grade": 12, "unit": "Full syllabus", "topics": "mixed national-exam topics", "count": total}]
    scale = total / sum(s["count"] for s in secs)
    for s in secs:
        s["count"] = max(1, round(s["count"] * scale))
    diff = total - sum(s["count"] for s in secs)
    secs.sort(key=lambda s: -s["count"])
    i = 0
    while diff != 0:
        s = secs[i % len(secs)]
        if diff > 0 or s["count"] > 1:
            s["count"] += 1 if diff > 0 else -1
            diff += -1 if diff > 0 else 1
        i += 1
    out = []
    for s in secs:
        n = s["count"]
        while n > 0:
            out.append(dict(s, count=min(n, SECTION_MAX)))
            n -= SECTION_MAX
    return out


@app.post("/v1/mock-paper")
async def mock_paper(r: MockPaperReq, _: str = Depends(device)):
    """One full national-exam paper: plan coverage like the real paper, then write sections in parallel."""
    syllabus = ("the new Ethiopian curriculum: grades 10-12 in full, and only grade 9 topics that are "
                "common to the old and new curricula")
    plan = await ai.structured(
        f"Plan the blueprint of a {r.count}-question Ethiopian Grade 12 national exam paper in {r.subject} "
        f"({r.stream} science stream), covering {syllabus}. Split it into 4-10 sections by grade and unit, "
        "weighting units the way the real national exam does (grade 12 and 11 units carry the most "
        "weight). counts must sum to the total. topics is a comma-separated list of specific subtopics.\n"
        f"Study material the student uploaded (shows which units they study):\n{r.context_text()[:20_000]}",
        BLUEPRINT_SCHEMA, effort="low", max_tokens=8_000)
    sections = plan_sections(plan["sections"], r.count)
    past = "\n".join(f"- {q[:600]}" for q in r.past_questions) or "(none provided)"
    weak = ", ".join(r.weak_topics) or "none recorded"
    sem = asyncio.Semaphore(4)

    # Everything the sections share comes first and ends in a cache breakpoint; only the short
    # section brief differs. The first section is sent alone; once it starts streaming its prefix is
    # cached, and the other sections read it (~5% of the input price) instead of paying in full.
    shared = ai.cached(
        f"You are writing one section of an original Ethiopian Grade 12 national exam paper in {r.subject}. "
        "Match the real national exam: 4 options, one correct answer letter A-D, distractors built from "
        "typical mistakes, a realistic mix of easy, medium and hard items, each answerable in about a minute "
        "without a calculator unless the subject needs arithmetic. Every question has a short step-by-step "
        "explanation. Use type 'mcq', marks 1, numeric_answer 0, tolerance 0, unit ''. Spread the answer "
        "letters evenly.\n"
        f"The student is weak at: {weak} - include a few on these if they fit the section.\n"
        f"Past exam questions for style reference (do NOT copy):\n{past}\n\n"
        f"Study material:\n{r.context_text()}")
    first_started = asyncio.Event()

    async def write(i: int, sec: dict) -> list[dict]:
        if i > 0:
            await first_started.wait()
        async with sem:
            brief = (f"Write exactly {sec['count']} questions for this section: grade {sec['grade']}, "
                     f"{sec['unit']}. Subtopics: {sec['topics']}.")
            out = await ai.structured([shared, {"type": "text", "text": brief}], EXAM_SCHEMA, effort="medium",
                                      max_tokens=32_000, started=first_started if i == 0 else None)
            return [q for q in out["questions"] if q.get("type") == "mcq" and len(q.get("options", [])) == 4][: sec["count"]]

    async def guarded(i: int, sec: dict) -> list[dict]:
        try:
            return await write(i, sec)
        finally:
            first_started.set()  # never leave the other sections waiting if the first one fails

    parts = await asyncio.gather(*(guarded(i, s) for i, s in enumerate(sections)))
    questions = [q for part in parts for q in part]
    if len(questions) < r.count * 0.8:
        raise ai.AIError(502, "The paper came back incomplete. Please try again.")
    return {"title": f"{r.subject} – national mock paper", "minutes": 0, "questions": questions}


packs.BATCH_SCHEMA = EXAM_SCHEMA


@app.post("/v1/packs")
async def create_pack(r: PackReq, token: str = Depends(device)):
    """Queue an overnight question pack (Message Batches API: half price, ready within ~1-24 h)."""
    past = "\n".join(f"- {q[:600]}" for q in r.past_questions) or "(none provided)"
    weak = ", ".join(r.weak_topics) or "none recorded"
    shared = ai.cached(
        f"You are writing part of a large practice question bank for an Ethiopian Grade 12 student preparing "
        f"for the national exam in {r.subject} (new curriculum, grades 9-12). Write national-exam-style "
        "multiple-choice questions: 4 options, one correct answer letter A-D, distractors built from typical "
        "mistakes, a mix of easy, medium and hard items, each with a short step-by-step explanation. Use type "
        "'mcq', marks 1, numeric_answer 0, tolerance 0, unit ''. Spread the answer letters evenly and never "
        "repeat a question.\n"
        f"The student is weak at: {weak} - weight those where they fit.\n"
        f"Past exam questions for style reference (do NOT copy):\n{past}\n\nStudy material:\n{r.context_text()}")
    try:
        return await packs.create(usage.device_id(token), r.subject, r.count, r.topics, shared)
    except ai.anthropic.APIError as e:
        raise ai.AIError(502, "Could not queue the question pack. Try again later.") from e


@app.get("/v1/packs/{pack_id}")
async def get_pack(pack_id: str, token: str = Depends(authenticated)):
    """Pack status; the questions once ready. Only the device that ordered a pack can read it."""
    try:
        out = await packs.collect(usage.device_id(token), pack_id[:40])
    except ai.anthropic.APIError as e:
        raise ai.AIError(502, "Could not check the question pack. Try again later.") from e
    if out is None:
        raise HTTPException(404, "Pack not found")
    return out


@app.post("/v1/explain")
async def explain(r: ExplainReq, _: str = Depends(device)):
    """Tutor chat about one question: why the answer is right, why the student's choice was wrong."""
    letters = "ABCDE"
    opts = "\n".join(f"{letters[i]}. {o}" for i, o in enumerate(r.options)) or "(not multiple choice)"
    lang = ("Reply in English." if r.language == "en"
            else "Reply in Amharic (Ge'ez script); keep formulas and technical terms in English.")
    context = (
        f"You are tutoring a Grade 12 student about one {r.subject} exam question.\n"
        f"Question: {r.question}\nOptions:\n{opts}\nCorrect answer: {r.correct_answer}\n"
        f"Official explanation: {r.explanation or '(none)'}\n"
        f"The student answered: {r.student_answer or '(no answer)'}\n\n"
        "Be a patient tutor: answer the student's question directly in at most ~150 words, show the key "
        "step or the misconception behind a wrong choice, and end with one short check-your-understanding "
        f"question when it helps. Stay on this question and its topic. {lang}\n\nStudent: "
    )
    messages = [{"role": t.role, "content": t.text} for t in r.turns]
    messages[0] = {"role": "user", "content": context + r.turns[0].text}
    # Cache the conversation so far: the next turn re-reads it from the cache instead of paying again.
    messages[-1] = {"role": "user", "content": [ai.cached(messages[-1]["content"])]}
    return await ai.structured(None, EXPLAIN_SCHEMA, effort="medium", max_tokens=8_000, messages=messages)


@app.post("/v1/grade")
async def grade(r: GradeReq, _: str = Depends(device)):
    """Grade written work or a photographed graph/calculation against the reference answer."""
    content: list[dict] = []
    if r.image_b64:
        content.append(ai.doc_block(r.image_mime, r.image_b64))
    content.append({"type": "text", "text": (
        f"Grade this {r.subject} answer out of {r.marks} marks.\nQuestion: {r.question}\n"
        f"Reference answer / what a correct answer must show: {r.reference_answer}\n"
        f"Student's typed answer: {r.student_text or '(see photo)'}\n"
        "If a photo is attached it is the student's handwritten work or sketched graph. Give "
        "method marks for correct steps even when the final answer is wrong. score is between "
        "0 and the maximum marks. feedback speaks directly to the student in 2-4 sentences. "
        "Use verdict 'unreadable' if the photo cannot be read."
    )})
    out = await ai.structured(content, GRADE_SCHEMA, effort="high", max_tokens=16_000)
    out["score"] = max(0.0, min(float(out["score"]), float(r.marks)))
    return out
