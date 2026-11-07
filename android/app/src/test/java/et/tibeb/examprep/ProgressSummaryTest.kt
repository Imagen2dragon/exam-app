package et.tibeb.examprep

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import et.tibeb.examprep.data.AppDb
import et.tibeb.examprep.data.Card
import et.tibeb.examprep.data.Document
import et.tibeb.examprep.data.Exam
import et.tibeb.examprep.data.Mock
import et.tibeb.examprep.data.Note
import et.tibeb.examprep.data.buildProgress
import et.tibeb.examprep.net.json
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The summary shared with the admin: correct numbers, and never notes or private text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgressSummaryTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDb

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(ctx, AppDb::class.java).allowMainThreadQueries().build() }
    @After fun tearDown() = db.close()

    @Test fun summaryHasTheNumbersAndNoPrivateText() = runBlocking {
        val dao = db.dao()
        dao.insert(Note(subject = "Physics", topic = "Waves", title = "My secret diary", markdown = "PRIVATE NOTE TEXT"))
        dao.insertCards(listOf(Card(subject = "Physics", topic = "Waves", front = "PRIVATE CARD", back = "x", due = 0)))
        dao.insert(Document(title = "Physics G12", subject = "Physics", grade = 12, docType = "textbook", status = "ready", summary = "PRIVATE SUMMARY"))
        dao.insert(Exam(title = "Waves practice", subject = "Physics", minutes = 10, finishedAt = 2000, score = 15.0, maxScore = 20.0))
        val mock = dao.insert(Mock(stream = "natural", questionsPerPaper = 60, useAi = true))
        listOf("English", "Mathematics", "Scholastic Aptitude", "Physics", "Chemistry", "Biology").forEachIndexed { i, s ->
            dao.insert(Exam(title = "$s mock", subject = s, minutes = 60, finishedAt = 3000L + i, score = 30.0, maxScore = 60.0, mockId = mock))
        }
        val p = buildProgress(dao, "natural", 12, null, setOf(1L, 2L), now = 10_000)
        assertEquals(7, p.totals.exams_done)
        assertEquals(1, p.totals.mocks_done)
        assertEquals(300.0, p.totals.best_mock_total, 1e-9)          // 6 papers x 50%
        assertEquals(1, p.totals.cards_total); assertEquals(1, p.totals.books)
        val physics = p.subjects.first { it.subject == "Physics" }
        assertEquals(2, physics.exams)
        assertEquals(62.5, physics.avg_pct, 1e-9)     // practice 75% + mock paper 50%
        assertEquals(50.0, physics.last_pct, 1e-9)    // the mock paper is the most recent
        assertEquals(listOf("Physics G12"), p.books.map { it.title })
        val wire = json.encodeToString(p)
        listOf("PRIVATE NOTE TEXT", "My secret diary", "PRIVATE CARD", "PRIVATE SUMMARY").forEach { assertFalse(it, wire.contains(it)) }
    }
}
