package et.tibeb.examprep

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import et.tibeb.examprep.data.AppDb
import et.tibeb.examprep.data.Exam
import et.tibeb.examprep.data.ExamItem
import et.tibeb.examprep.data.Question
import et.tibeb.examprep.data.Repository
import et.tibeb.examprep.net.Api
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wrong answers -> flashcards + notebook; a later fully-correct answer clears them; works offline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MistakeNotebookTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDb
    private lateinit var repo: Repository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDb::class.java).allowMainThreadQueries().build()
        repo = Repository(ctx, db, Api({ "https://x" }, { null }, { id, _ -> ctx.getString(id) })) { id, a -> ctx.getString(id, *a) }
    }

    @After fun tearDown() = db.close()

    private fun mcq(stem: String, answer: String, subject: String = "Physics") = Question(subject = subject, grade = 12, topic = "Waves",
        type = "mcq", stem = stem, optionsJson = """["1","2","3","4"]""", answer = answer, explanation = "because", difficulty = "hard",
        marks = 1, source = "ai")

    /** Builds and submits an exam with the given answers; returns nothing - just like a student finishing it. */
    private suspend fun sit(questionIds: List<Long>, answers: List<String>) {
        val exam = repo.dao.insert(Exam(title = "t", subject = "Physics", minutes = 10, maxScore = questionIds.size.toDouble()))
        repo.dao.insertItems(questionIds.mapIndexed { i, q -> ExamItem(examId = exam, idx = i, questionId = q, response = answers[i]) })
        repo.submit(exam)
        Thread.sleep(5) // distinct finish times
    }

    @Test fun wrongAnswersBecomeCardsAndRetryClearsThem() = runBlocking {
        val ids = repo.dao.insertQuestions(listOf(mcq("Q1", "A"), mcq("Q2", "B"), mcq("Q3", "C")))
        sit(ids, listOf("A", "C", ""))                       // Q1 right, Q2 wrong, Q3 blank
        assertEquals(setOf("Q2", "Q3"), repo.dao.mistakes().first().map { it.stem }.toSet())
        val cards = repo.dao.dueCards(Long.MAX_VALUE).first()
        assertEquals(2, cards.size)
        assertTrue(cards.first { it.front.startsWith("Q2") }.front.contains("B. 2"))
        assertTrue(cards.first { it.front.startsWith("Q2") }.back.startsWith(ctx.getString(R.string.card_answer, "B")))

        // Retry exam contains exactly the mistakes, built offline
        val retry = repo.createMistakeExam("Physics")
        assertEquals(setOf(ids[1], ids[2]), repo.dao.items(retry).first().map { it.questionId }.toSet())

        // Get Q2 right now, Q3 still wrong -> only Q3 stays; no duplicate cards
        val items = repo.dao.items(retry).first()
        items.forEach { repo.answer(retry, it.idx, if (it.stem == "Q2") "B" else "A") }
        repo.submit(retry)
        assertEquals(listOf("Q3"), repo.dao.mistakes().first().map { it.stem })
        assertEquals(2, repo.dao.dueCards(Long.MAX_VALUE).first().size)
    }

    @Test fun notebookIsPerSubject() = runBlocking {
        val ids = repo.dao.insertQuestions(listOf(mcq("P", "A"), mcq("C", "A", subject = "Chemistry")))
        sit(ids, listOf("B", "B"))
        val retry = repo.createMistakeExam("Chemistry")
        assertEquals(listOf("C"), repo.dao.items(retry).first().map { it.stem })
    }
}
