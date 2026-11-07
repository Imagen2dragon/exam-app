package et.tibeb.examprep

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import et.tibeb.examprep.data.AppDb
import et.tibeb.examprep.data.Exam
import et.tibeb.examprep.data.ExamItem
import et.tibeb.examprep.data.Question
import et.tibeb.examprep.data.Repository
import et.tibeb.examprep.data.Repository.Turn
import et.tibeb.examprep.net.Api
import et.tibeb.examprep.net.ExplainReq
import et.tibeb.examprep.net.ExplainRes
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The chat sends the full question context, the student's latest answer and the turns in order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExplainTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDb
    private val sent = mutableListOf<ExplainReq>()
    private val api = object : Api({ "https://x" }, { "t" }, { id, _ -> ctx.getString(id) }) {
        override suspend fun explain(r: ExplainReq): ExplainRes { sent += r; return ExplainRes("Factor the top.") }
    }

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(ctx, AppDb::class.java).allowMainThreadQueries().build() }
    @After fun tearDown() = db.close()

    @Test fun sendsContextAndHistory() = runBlocking {
        val repo = Repository(ctx, db, api) { id, a -> ctx.getString(id, *a) }
        val q = repo.dao.insertQuestions(listOf(Question(subject = "Mathematics", grade = 12, topic = "Limits", type = "mcq",
            stem = "lim (x²−9)/(x−3)", optionsJson = """["0","3","6","∞"]""", answer = "C", explanation = "x+3 → 6",
            difficulty = "hard", marks = 1, source = "ai"))).single()
        val old = repo.dao.insert(Exam(title = "a", subject = "Mathematics", minutes = 5, finishedAt = 1))
        val new = repo.dao.insert(Exam(title = "b", subject = "Mathematics", minutes = 5, finishedAt = 2))
        repo.dao.insertItems(listOf(ExamItem(old, 0, q, response = "A"), ExamItem(new, 0, q, response = "B")))

        val reply = repo.explain(q, listOf(Turn(true, "Why not 3?"), Turn(false, "Try factoring."), Turn(true, "How?")), "am")
        assertEquals("Factor the top.", reply)
        val r = sent.single()
        assertEquals(listOf("0", "3", "6", "∞"), r.options)
        assertEquals("C", r.correct_answer)
        assertEquals("B", r.student_answer)            // the most recent attempt, not the first
        assertEquals("am", r.language)
        assertEquals(listOf("user", "assistant", "user"), r.turns.map { it.role })
    }
}
