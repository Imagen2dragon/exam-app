package et.tibeb.examprep.data

import android.content.Context
import android.net.Uri
import et.tibeb.examprep.R
import et.tibeb.examprep.ui.subjectRes
import et.tibeb.examprep.net.Api
import et.tibeb.examprep.net.ApiException
import et.tibeb.examprep.net.ApiQuestion
import et.tibeb.examprep.net.ExamReq
import et.tibeb.examprep.net.ExtractReq
import et.tibeb.examprep.net.FlashReq
import et.tibeb.examprep.net.GradeReq
import et.tibeb.examprep.net.MockPaperReq
import et.tibeb.examprep.net.PageReq
import et.tibeb.examprep.net.NotesReq
import et.tibeb.examprep.net.json
import et.tibeb.examprep.util.MAX_UPLOAD_BYTES
import et.tibeb.examprep.util.answersDir
import et.tibeb.examprep.util.b64
import et.tibeb.examprep.util.compressedJpeg
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

const val MAX_PAGES = 30

class Repository(private val ctx: Context, private val db: AppDb, private val api: Api, private val str: (Int, Array<out Any>) -> String) {
    private fun s(id: Int, vararg a: Any) = str(id, a)
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

    private fun subjectLabel(subject: String) = subjectRes(subject)?.let { s(it) } ?: subject

    private fun friendly(t: Throwable) = t.message ?: s(R.string.err_generic)

    // ---------- Library ----------

    // ---------- Library: uploads run in the background (WorkManager) ----------

    private fun pendingDir(docId: Long) = File(ctx.filesDir, "pending/$docId")

    /**
     * Copies the picked file into app-private storage (the system's read permission on a picked
     * file ends when the app closes) and queues the upload. Returns at once; ImportWorker does the rest.
     */
    suspend fun queueImport(uri: Uri, mime: String, title: String, subject: String, grade: Int, docType: String): Long {
        val isImage = mime.startsWith("image/")
        if (!isImage && ctx.sizeOf(uri) > MAX_UPLOAD_BYTES) error(s(R.string.err_too_big))
        val id = dao.insert(Document(title = title.take(200), subject = subject, grade = grade, docType = docType, status = "queued"))
        try {
            val dir = pendingDir(id).apply { mkdirs() }
            if (isImage) File(dir, "page_001.jpg").writeBytes(ctx.compressedJpeg(uri, maxSide = 2000))
            else File(dir, "book.pdf").outputStream().use { out -> ctx.contentResolver.openInputStream(uri)!!.use { it.copyTo(out) } }
        } catch (e: Exception) {
            pendingDir(id).deleteRecursively(); dao.deleteDocumentFully(id)
            throw e
        }
        ImportWorker.enqueue(ctx, id)
        return id
    }

    /** A chapter photographed page by page: the staged JPEGs move into the upload queue in this order. */
    suspend fun queuePages(pages: List<File>, title: String, subject: String, grade: Int, docType: String): Long {
        require(pages.size in 1..MAX_PAGES)
        val id = dao.insert(Document(title = title.take(200), subject = subject, grade = grade, docType = docType, status = "queued"))
        val dir = pendingDir(id).apply { mkdirs() }
        pages.forEachIndexed { i, f ->
            val target = File(dir, "page_%03d.jpg".format(i + 1))
            if (!f.renameTo(target)) { f.copyTo(target, overwrite = true); f.delete() }
        }
        ImportWorker.enqueue(ctx, id)
        return id
    }

    /** Called by ImportWorker. Sends the queued file to the AI and stores the extracted text. */
    suspend fun processImport(docId: Long, giveUp: Boolean): ImportOutcome {
        val doc = dao.document(docId) ?: return ImportOutcome.FAILED
        val files = pendingDir(docId).listFiles()?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) {
            dao.update(doc.copy(status = "failed", error = s(R.string.err_generic)))
            return ImportOutcome.FAILED
        }
        dao.update(doc.copy(status = "processing", error = ""))
        val file = files.first()
        val mime = if (file.extension == "pdf") "application/pdf" else "image/jpeg"
        val req = if (files.size > 1)   // a photographed chapter: all pages in one request, in order
            ExtractReq("image/jpeg", "", doc.subject, doc.title, doc.docType, doc.grade,
                pages = files.map { PageReq("image/jpeg", it.readBytes().b64()) })
        else ExtractReq(mime, file.readBytes().b64(), doc.subject, doc.title, doc.docType, doc.grade)
        return try {
            val res = api.extract(req)
            val chunks = res.chunks.map { Chunk(docId = docId, subject = doc.subject, grade = doc.grade, unit = it.unit, topic = it.topic, text = it.text) }
            val qs = res.questions.map { it.toEntity(doc.subject, doc.grade, "past", docId) }
            dao.replaceDocumentContent(doc.copy(status = "ready", summary = res.summary, error = ""), chunks, qs)
            pendingDir(docId).deleteRecursively()  // only the extracted text is kept
            ImportOutcome.DONE
        } catch (e: ApiException) {
            if (e.retryable && !giveUp) {
                dao.update(doc.copy(status = "queued", error = friendly(e)))
                ImportOutcome.RETRY
            } else {
                // Keep the pending file so the student can tap Retry after fixing the problem.
                dao.update(doc.copy(status = "failed", error = friendly(e)))
                ImportOutcome.FAILED
            }
        }
    }

    fun retryImport(docId: Long) {
        scope.launch {
            val doc = dao.document(docId) ?: return@launch
            dao.update(doc.copy(status = "queued", error = ""))
            ImportWorker.enqueue(ctx, docId)
        }
    }

    suspend fun deleteDocument(docId: Long) {
        ImportWorker.cancel(ctx, docId)
        pendingDir(docId).deleteRecursively()
        dao.deleteDocumentFully(docId)
    }

    private val startedAt = System.currentTimeMillis()

    /**
     * Books that were mid-upload when an older version closed have no queued file: mark them failed.
     * Only books added before this start are checked, so an upload being queued right now is never touched.
     */
    private suspend fun recoverStuckImports() {
        dao.documentsNow().filter { it.addedAt < startedAt && it.status in setOf("queued", "processing") && pendingDir(it.id).listFiles().isNullOrEmpty() }
            .forEach { dao.update(it.copy(status = "failed", error = s(R.string.lib_interrupted))) }
    }

    init {
        scope.launch {
            runCatching { recoverStuckImports() }
            // Photos from a chapter that was never uploaded (app closed mid-capture) are cleared after a day.
            File(ctx.filesDir, "staging").listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }
                ?.forEach { it.deleteRecursively() }
        }
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
            if (bank.isEmpty()) error(s(R.string.err_no_bank, subjectLabel(c.subject)))
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
                if (it.size < 10) error(s(R.string.err_mock_bank, it.size, subjectLabel(subject)))
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
                else -> if (it.response.isBlank() && it.imagePath.isBlank()) item.copy(graded = true, feedback = s(R.string.err_no_answer))
                else runCatching {
                    val img = it.imagePath.takeIf { p -> p.isNotBlank() }?.let { p -> File(p).readBytes().b64() }
                    val g = api.grade(GradeReq(it.subject, it.stem, it.answer, it.marks, it.response, img))
                    item.copy(earned = g.score, graded = true, feedback = (listOf(g.feedback) + g.mistakes.map { m -> "• $m" }).joinToString("\n"))
                }.getOrElse { e -> aiFailures++; item.copy(feedback = s(R.string.err_not_graded, friendly(e))) }
            }
            dao.update(graded)
        }
        val after = dao.items(examId).first()
        val exam = dao.examNow(examId)!!
        dao.update(exam.copy(finishedAt = System.currentTimeMillis(), score = after.sumOf { it.earned }))
        addMistakeCards(after)
        if (aiFailures > 0) error(s(R.string.err_grading_offline, aiFailures))
        return examId
    }

    suspend fun retryGrading(examId: Long) = submit(examId)

    // ---------- Mistake notebook ----------

    /** Every wrong answer becomes a flashcard (once per question), due immediately. */
    private suspend fun addMistakeCards(items: List<ItemWithQuestion>) {
        for (it in items.filter { it.graded && it.earned < it.marks }) {
            if (dao.cardsForQuestion(it.questionId) > 0) continue
            val options = runCatching { json.decodeFromString<List<String>>(it.optionsJson) }.getOrDefault(emptyList())
            val front = if (it.type == "mcq" && options.isNotEmpty())
                it.stem + "\n" + options.mapIndexed { i, o -> "${'A' + i}. $o" }.joinToString("\n") else it.stem
            val answer = it.answer + if (it.type == "numeric" && it.unit.isNotBlank()) " ${it.unit}" else ""
            val back = listOf(s(R.string.card_answer, answer), it.explanation).filter { t -> t.isNotBlank() }.joinToString("\n\n")
            dao.insertCards(listOf(Card(subject = it.subject, topic = it.topic, front = front, back = back, questionId = it.questionId)))
        }
    }

    /** An offline exam made only of the student's current mistakes in one subject. */
    suspend fun createMistakeExam(subject: String, limit: Int = 30): Long {
        val qs = dao.mistakes().first().filter { it.subject == subject }.take(limit)
        require(qs.isNotEmpty())
        val examId = dao.insert(Exam(title = s(R.string.mistakes_exam_title, subjectLabel(subject)), subject = subject,
            minutes = (qs.size * 1.5).toInt().coerceAtLeast(5), maxScore = qs.sumOf { it.marks }.toDouble()))
        dao.insertItems(qs.mapIndexed { i, q -> ExamItem(examId = examId, idx = i, questionId = q.id) })
        return examId
    }
}

private fun ApiQuestion.toEntity(subject: String, grade: Int, source: String, docId: Long?) = Question(
    subject = subject, grade = grade, topic = topic.ifBlank { "General" }, type = type, stem = stem,
    optionsJson = json.encodeToString(options), answer = answer, numericAnswer = numeric_answer,
    tolerance = tolerance, unit = unit, explanation = explanation, difficulty = difficulty,
    marks = marks.coerceIn(1, 10), source = source, docId = docId,
)
