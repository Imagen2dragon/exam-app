package et.tibeb.examprep

import et.tibeb.examprep.data.Card
import et.tibeb.examprep.data.ftsQuery
import et.tibeb.examprep.data.review
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsTest {
    private val day = 24L * 60 * 60 * 1000
    private val c = Card(subject = "Physics", topic = "Waves", front = "f", back = "b", due = 0)

    @Test fun goodAnswersGrowTheInterval() {
        val a = c.review(4, now = 0); assertEquals(1, a.intervalDays); assertEquals(day, a.due)
        val b = a.review(4, now = 0); assertEquals(3, b.intervalDays)
        val d = b.review(4, now = 0); assertTrue(d.intervalDays > 3)
    }

    @Test fun againResetsAndRetriesSoon() {
        val learned = c.review(5, 0).review(5, 0)
        val lapsed = learned.review(0, now = 1000)
        assertEquals(0, lapsed.reps); assertTrue(lapsed.due - 1000 < day)
    }

    @Test fun easeNeverDropsBelowFloor() {
        var x = c
        repeat(20) { x = x.review(3, 0) }
        assertTrue(x.ease >= 1.3)
    }

    @Test fun ftsQueryIsSanitised() {
        assertEquals("newton* OR laws* OR motion*", ftsQuery("Newton's laws of motion"))
        assertNull(ftsQuery("\" OR 1=1 --"))
        assertTrue(!ftsQuery("a\"b) NEAR(c*")!!.contains("\""))
    }
}
