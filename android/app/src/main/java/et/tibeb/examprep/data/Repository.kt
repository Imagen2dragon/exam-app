package et.tibeb.examprep.data

import android.content.Context
import android.net.Uri
import et.tibeb.examprep.net.Api
import et.tibeb.examprep.net.ApiQuestion
import et.tibeb.examprep.net.ExamReq
import et.tibeb.examprep.net.ExtractReq
import et.tibeb.examprep.net.FlashReq
import et.tibeb.examprep.net.GradeReq
import et.tibeb.examprep.net.MockPaperReq
import et.tibeb.examprep.net.NotesReq
import et.tibeb.examprep.net.json
import et.tibeb.examprep.util.MAX_UPLOAD_BYTES
import et.tibeb.examprep.util.answersDir
import et.tibeb.examprep.util.b64
import et.tibeb.examprep.util.compressedJpeg
import et.tibeb.examprep.util.readBytes
import et.tibeb.examprep.util.sizeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.abs

data class JobState(val running: Boolean = false, val error: String? = null, val resultId: Long? = null, val label: String = "")

data class ExamConfig(
    val subject: String, val topic: String, val grade: Int, val count: Int, val difficulty: String,
    val types: List<String>, val useAi: Boolean,
)

class Repository(private val ctx: Context, private val db: AppDb, private val api: Api) {
    val dao = db.dao()

    /** Jobs run in an app-wide scope so a rotation or screen change never cancels an AI request. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _jobs = MutableStateFlow<Map<String, JobState>>(emptyMap())
    val jobs: StateFlow<Map<String, JobState>> = _jobs

    fun run(key: String, label: String, block: suspend () -> Long) {
        if (_jobs.value[key]?.running == true) return
        _jobs.update { it + (key to JobState(running = true, label = label)) }
        scope.launch {
            val result = runCatching { block() }
            _jobs.update {
                it + (key to JobState(false, result.exceptionOrNull()?.let(::friendly), result.getOrNull(), label))
            }
        }
    }

    fun clearJob(key: String) = _jobs.update { it - key }

    private fun friendly(t: Throwable) = t.message ?: "Something went wrong"

    // ---------- Library ----------

    suspend fun importDocument(uri: Uri, mime: String, title: String, subject: String, grade: Int, docType: String): Long {
        val isImage = mime.startsWith("image/")
        val size = ctx.sizeOf(uri)
        if (!isImage && size > MAX_UPLOAD_BYTES) error("File is larger than 20 MB. Split the book into units (e.g. one PDF per unit).")
        val bytes = if (isImage) ctx.compressedJpeg(uri, maxSide = 2000) else ctx.readBytes(uri)
        val sendMime = if (isImage) "image/jpeg" else "application/pdf"
        val doc = Document(title = title.take(200), subject = subject, grade = grade, docType = docType)
        val id = dao.insert(doc)
        try {
            val res = api.extract(ExtractReq(sendMime, bytes.b64(), subject, doc.title, docType, grade))
            val chunks = res.chunks.map { Chunk(docId = id, subject = subject, grade = grade, unit = it.unit, topic = it.topic, text = it.text) }
            val qs = res.questions.map { it.toEntity(subject, grade, "past", id) }
            dao.replaceDocumentContent(doc.copy(id = id, status = "ready", summary = res.summary), chunks, qs)
        } catch (e: Exception) {
            dao.update(doc.copy(id = id, status = "failed", error = friendly(e)))
            throw e
        }
        return id
    }

    /** Finds the most relevant stored pages for a topic, fully on-device. */
    suspend fun context(subject: String, topic: String, limit: Int = 12): List<String> {
        val hits = ftsQuery(topic)?.let { runCatching { dao.searchChunks(subject, it, limit) }.getOrNull() }.orEmpty()
        val chunks = hits.ifEmpty { if (topic.isBlank()) dao.randomChunks(subject, limit.coerceAtMost(16)) else emptyList() }
        return chunks.map { "[${it.unit} / ${it.topic}]\n${it.text}" }
    }

    // ---------- Notes & flashcards ----------

    suspend fun makeNotes(subject: String, topic: String, grade: Int, style: String, language: String): Long {
        val r = api.notes(NotesReq(subject, topic, grade, context(subject, topic), language, style))
        val md = if (r.key_formulas.isEmpty()) r.markdown else r.markdown + "\n\n## Key formulas\n" + r.key_formulas.joinToString("\n") { "- $it" }
        return dao.insert(Note(subject = subject, topic = topic, title = r.title, markdown = md))
    }

    suspend fun makeFlashcards(subject: String, topic: String, grade: Int, count: Int): Long {
        val r = api.flashcards(FlashReq(subject, topic, grade, context(subject, topic), count))
        dao.insertCards(r.cards.map { Card(subject = subject, topic = it.topic.ifBlank { topic }, front = it.front, back = it.back) })
        return r.cards.size.toLong()
    }

    suspend fun review(card: Card, grade: Int) = dao.update(card.review(grade))

    // ---------- Exams ----------

    suspend fun weakTopics(subject: String): List<String> =
        dao.topicStats().first().filter { it.subject == subject && it.attempted >= 2 }
            .sortedBy { it.accuracy }.take(5).filter { it.accuracy < 0.7 }.map { it.topic }

    suspend fun createExam(c: ExamConfig): Long {
        val (title, minutes, questions) = if (c.useAi) {
            val past = dao.randomPast(c.subject, listOf("mcq", "numeric", "short", "graph"), 12).map { it.stem }
            val r = api.exam(ExamReq(c.subject, c.topic.ifBlank { "Full syllabus (grades 9-12)" }, c.grade,
                context(c.subject, c.topic), c.count, c.difficulty, c.types, past, weakTopics(c.subject)))
            Triple(r.title, r.minutes, r.questions.map { it.toEntity(c.subject, c.grade, "ai", null) })
        } else {
            val bank = dao.randomPast(c.subject, c.types, c.count)
            if (bank.isEmpty()) error("No saved past-exam questions for ${c.subject} yet. Upload an old exam first, or use AI.")
            Triple("${c.subject} past-exam practice", (bank.size * 1.2).toInt().coerceAtLeast(5), bank)
        }
        val ids = if (c.useAi) dao.insertQuestions(questions) else questions.map { it.id }
        val examId = dao.insert(Exam(title = title, subject = c.subject, minutes = minutes,
            maxScore = questions.sumOf { it.marks }.toDouble()))
        dao.insertItems(ids.mapIndexed { i, q -> ExamItem(examId = examId, idx = i, questionId = q) })
        return examId
    }

    // ---------- Full national mock ----------

    suspend fun createMock(stream: String, perPaper: Int, useAi: Boolean): Long =
        dao.insert(Mock(stream = stream, questionsPerPaper = perPaper, useAi = useAi))

    /** Returns the paper for this subject, generating it the first time (lazily, to save AI cost). */
    suspend fun mockPaper(mockId: Long, subject: String): Long {
        dao.mockPaper(mockId, subject)?.let { return it.id }
        val mock = dao.mockNow(mockId) ?: error("Mock not found")
        val questions = if (mock.useAi) {
            val past = dao.randomPast(subject, listOf("mcq"), 12).map { it.stem }
            api.mockPaper(MockPaperReq(subject, mock.stream, mock.questionsPerPaper, context(subject, "", 16), past, weakTopics(subject)))
                .questions.map { it.toEntity(subject, 12, "ai", null) }
                .let { qs -> qs.zip(dao.insertQuestions(qs)) { q, id -> q.copy(id = id) } }
        } else {
            dao.randomPast(subject, listOf("mcq"), mock.questionsPerPaper).also {
                if (it.size < 10) error("Only ${it.size} saved $subject multiple-choice questions. Upload more past papers, or create the mock with AI.")
            }
        }
        return mockLock.withLock { dao.mockPaper(mockId, subject)?.id ?: insertPaper(mockId, subject, questions) }
    }

    private val mockLock = Mutex()

    private suspend fun insertPaper(mockId: Long, subject: String, questions: List<Question>): Long {
        val examId = dao.insert(Exam(title = "$subject – national mock", subject = subject, mockId = mockId,
            minutes = paperMinutes(subject, questions.size), maxScore = questions.sumOf { it.marks }.toDouble()))
        dao.insertItems(questions.mapIndexed { i, q -> ExamItem(examId = examId, idx = i, questionId = q.id) })
        return examId
    }

    /** Generates every remaining paper so the whole mock can then be sat offline. */
    suspend fun prepareMock(mockId: Long): Long {
        val mock = dao.mockNow(mockId) ?: error("Mock not found")
        streamSubjects(mock.stream).forEach { mockPaper(mockId, it) }
        return mockId
    }

    suspend fun start(examId: Long) {
        val e = dao.examNow(examId) ?: return
        if (e.startedAt == 0L) dao.update(e.copy(startedAt = System.currentTimeMillis()))
    }

    suspend fun answer(examId: Long, idx: Int, response: String) {
        dao.item(examId, idx)?.let { dao.update(it.copy(response = response)) }
    }

    suspend fun attachPhoto(examId: Long, idx: Int, uri: Uri) {
        val file = File(ctx.answersDir(), "e${examId}_q$idx.jpg")
        file.writeBytes(ctx.compressedJpeg(uri))
        dao.item(examId, idx)?.let { dao.update(it.copy(imagePath = file.absolutePath)) }
    }

    /** Grades MCQ/numeric instantly on the phone; written work and graphs go to the AI. */
    suspend fun submit(examId: Long): Long {
        val items = dao.items(examId).first()
        var aiFailures = 0
        for (it in items) {
            val item = dao.item(examId, it.idx) ?: continue
            if (item.graded) continue
            val graded = when (it.type) {
                "mcq" -> item.copy(earned = if (it.response.trim().equals(it.answer.trim().take(1), true)) it.marks.toDouble() else 0.0, graded = true)
                "numeric" -> {
                    val v = it.response.replace(",", ".").trim().toDoubleOrNull()
                    val tol = if (it.tolerance > 0) it.tolerance else abs(it.numericAnswer) * 0.01 + 1e-9
                    item.copy(earned = if (v != null && abs(v - it.numericAnswer) <= tol) it.marks.toDouble() else 0.0, graded = true)
                }
                else -> if (it.response.isBlank() && it.imagePath.isBlank()) item.copy(graded = true, feedback = "No answer given.")
                else runCatching {
                    val img = it.imagePath.takeIf { p -> p.isNotBlank() }?.let { p -> File(p).readBytes().b64() }
                    val g = api.grade(GradeReq(it.subject, it.stem, it.answer, it.marks, it.response, img))
                    item.copy(earned = g.score, graded = true, feedback = (listOf(g.feedback) + g.mistakes.map { m -> "• $m" }).joinToString("\n"))
                }.getOrElse { e -> aiFailures++; item.copy(feedback = "Not graded yet: ${friendly(e)}") }
            }
            dao.update(graded)
        }
        val after = dao.items(examId).first()
        val exam = dao.examNow(examId)!!
        dao.update(exam.copy(finishedAt = System.currentTimeMillis(), score = after.sumOf { it.earned }))
        if (aiFailures > 0) error("$aiFailures written answer(s) need the internet to be graded. Open the result and tap Retry grading.")
        return examId
    }

    suspend fun retryGrading(examId: Long) = submit(examId)
}

private fun ApiQuestion.toEntity(subject: String, grade: Int, source: String, docId: Long?) = Question(
    subject = subject, grade = grade, topic = topic.ifBlank { "General" }, type = type, stem = stem,
    optionsJson = json.encodeToString(options), answer = answer, numericAnswer = numeric_answer,
    tolerance = tolerance, unit = unit, explanation = explanation, difficulty = difficulty,
    marks = marks.coerceIn(1, 10), source = source, docId = docId,
)
