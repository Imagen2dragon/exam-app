"""Request models (validated, size-limited) and JSON schemas for Claude's structured outputs."""
from typing import Literal

from pydantic import BaseModel, Field, model_validator

Subject = Literal[
    "Mathematics", "Physics", "Chemistry", "Biology", "English",
    "Scholastic Aptitude", "Geography", "History", "Economics", "Other",
]
QType = Literal["mcq", "numeric", "short", "graph"]
MAX_B64 = 28_000_000  # ~20 MB file once base64-encoded; Claude's request limit is 32 MB
MAX_CHUNK = 6_000


MAX_PAGES = 30
MAX_PAGE_B64 = 3_000_000  # ~2.2 MB per photographed page; the app sends ~300 KB JPEGs


class PageImage(BaseModel):
    mime: Literal["image/jpeg", "image/png", "image/webp"]
    data_b64: str = Field(min_length=1, max_length=MAX_PAGE_B64)


class ExtractReq(BaseModel):
    """Either one file (`data_b64` + `mime`) or a chapter of page photos in reading order (`pages`)."""
    mime: Literal["application/pdf", "image/jpeg", "image/png", "image/webp"]
    data_b64: str = Field(default="", max_length=MAX_B64)
    pages: list[PageImage] = Field(default_factory=list, max_length=MAX_PAGES)
    subject: Subject
    title: str = Field(max_length=200)
    doc_type: Literal["textbook", "teacher_guide", "past_exam", "worksheet", "notes"]
    grade: int = Field(ge=9, le=12)

    @model_validator(mode="after")
    def one_source(self):
        if bool(self.data_b64) == bool(self.pages):
            raise ValueError("send either data_b64 or pages, not both or neither")
        return self


class ContextReq(BaseModel):
    subject: Subject
    topic: str = Field(max_length=300)
    grade: int = Field(ge=9, le=12)
    context: list[str] = Field(default_factory=list, max_length=24)
    language: Literal["en", "am"] = "en"

    def context_text(self) -> str:
        parts = [c[:MAX_CHUNK] for c in self.context]
        return "\n\n---\n\n".join(parts) if parts else "(no uploaded material - use the Ethiopian curriculum)"


class NotesReq(ContextReq):
    style: Literal["summary", "detailed", "formula_sheet", "cheat_sheet"] = "summary"


class FlashReq(ContextReq):
    count: int = Field(default=15, ge=3, le=40)


class ExamReq(ContextReq):
    count: int = Field(default=20, ge=3, le=60)
    difficulty: Literal["standard", "hard", "very_hard"] = "hard"
    types: list[QType] = Field(default_factory=lambda: ["mcq"], min_length=1, max_length=4)
    past_questions: list[str] = Field(default_factory=list, max_length=30)
    weak_topics: list[str] = Field(default_factory=list, max_length=10)


class MockPaperReq(BaseModel):
    subject: Subject
    stream: Literal["natural", "social"]
    count: int = Field(ge=10, le=120)
    context: list[str] = Field(default_factory=list, max_length=24)
    past_questions: list[str] = Field(default_factory=list, max_length=30)
    weak_topics: list[str] = Field(default_factory=list, max_length=10)

    def context_text(self) -> str:
        parts = [c[:MAX_CHUNK] for c in self.context]
        return "\n\n---\n\n".join(parts) if parts else "(no uploaded material - use the Ethiopian curriculum)"


class GradeReq(BaseModel):
    subject: Subject
    question: str = Field(max_length=4_000)
    reference_answer: str = Field(max_length=4_000)
    marks: int = Field(ge=1, le=20)
    student_text: str = Field(default="", max_length=6_000)
    image_b64: str | None = Field(default=None, max_length=8_000_000)
    image_mime: Literal["image/jpeg", "image/png", "image/webp"] = "image/jpeg"


def _obj(props: dict, required: list[str] | None = None) -> dict:
    return {
        "type": "object",
        "properties": props,
        "required": required if required is not None else list(props),
        "additionalProperties": False,
    }


S = {"type": "string"}
I = {"type": "integer"}
N = {"type": "number"}

QUESTION = _obj({
    "type": {"type": "string", "enum": ["mcq", "numeric", "short", "graph"]},
    "stem": S,
    "options": {"type": "array", "items": S},
    "answer": S,
    "numeric_answer": N,
    "tolerance": N,
    "unit": S,
    "explanation": S,
    "topic": S,
    "difficulty": {"type": "string", "enum": ["standard", "hard", "very_hard"]},
    "marks": I,
})

EXTRACT_SCHEMA = _obj({
    "summary": S,
    "chunks": {"type": "array", "items": _obj({"unit": S, "topic": S, "text": S})},
    "questions": {"type": "array", "items": QUESTION},
})
NOTES_SCHEMA = _obj({"title": S, "markdown": S, "key_formulas": {"type": "array", "items": S}})
FLASH_SCHEMA = _obj({"cards": {"type": "array", "items": _obj({"front": S, "back": S, "topic": S})}})
EXAM_SCHEMA = _obj({"title": S, "minutes": I, "questions": {"type": "array", "items": QUESTION}})
BLUEPRINT_SCHEMA = _obj({"sections": {"type": "array", "items": _obj({
    "grade": I, "unit": S, "topics": S, "count": I})}})
GRADE_SCHEMA = _obj({
    "score": N,
    "verdict": {"type": "string", "enum": ["correct", "partially_correct", "incorrect", "unreadable"]},
    "feedback": S,
    "mistakes": {"type": "array", "items": S},
})
