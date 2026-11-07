package et.tibeb.examprep

import et.tibeb.examprep.data.NATURAL_STREAM
import et.tibeb.examprep.data.Planner
import et.tibeb.examprep.data.Planner.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PlannerTest {
    private val tz = TimeZone.getTimeZone("Africa/Addis_Ababa")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis
    private val now = at(2026, 11, 7, 22)

    private fun input(
        examDate: Long? = null, due: Int = 0, reviewed: Int = 0, mistakes: Int = 0,
        acc: Map<String, Double> = emptyMap(), finishedToday: List<String> = emptyList(), lastMock: Long? = null,
    ) = Planner.Input(now, examDate, due, reviewed, mistakes, NATURAL_STREAM, acc, finishedToday, lastMock)

    @Test fun countdownCountsCalendarDays() {
        assertEquals(0, Planner.daysUntil(at(2026, 11, 7, 8), now, tz))     // later today -> 0
        assertEquals(1, Planner.daysUntil(at(2026, 11, 8, 1), now, tz))     // tomorrow 1am -> 1 even if <24h away
        assertEquals(243, Planner.daysUntil(at(2027, 7, 8), now, tz))
    }

    @Test fun weakestSubjectIsPractisedOnceEverySubjectHasData() {
        val acc = NATURAL_STREAM.associateWith { 0.8 } + ("Chemistry" to 0.41)
        assertEquals("Chemistry", Planner.focusSubject(input(acc = acc)))
    }

    @Test fun untriedSubjectsComeFirst() {
        val acc = NATURAL_STREAM.filter { it != "Biology" }.associateWith { 0.2 }
        assertEquals("Biology", Planner.focusSubject(input(acc = acc)))
    }

    @Test fun planShowsOnlyWhatIsRelevant() {
        val p = Planner.plan(input(due = 12, mistakes = 25, acc = NATURAL_STREAM.associateWith { 0.5 }))
        assertEquals(listOf(Kind.REVIEW_CARDS, Kind.FIX_MISTAKES, Kind.PRACTICE_EXAM), p.map { it.kind })
        assertEquals(10, p[1].count)                       // mistakes capped per day
        assertTrue(Planner.plan(input(acc = NATURAL_STREAM.associateWith { 0.5 })).none { it.kind == Kind.REVIEW_CARDS })
    }

    @Test fun itemsTickOffWhenDone() {
        val acc = NATURAL_STREAM.associateWith { 0.9 } + ("Physics" to 0.3)
        val p = Planner.plan(input(due = 0, reviewed = 20, acc = acc, finishedToday = listOf("Physics")))
        assertTrue(p.first { it.kind == Kind.REVIEW_CARDS }.done)
        assertTrue(p.first { it.kind == Kind.PRACTICE_EXAM }.done)
    }

    @Test fun weeklyMockOnlyInTheLastSixtyDays() {
        assertTrue(Planner.plan(input(examDate = at(2027, 7, 8))).none { it.kind == Kind.FULL_MOCK })
        val close = Planner.plan(input(examDate = at(2026, 12, 20)))
        assertFalse(close.first { it.kind == Kind.FULL_MOCK }.done)
        val doneThisWeek = Planner.plan(input(examDate = at(2026, 12, 20), lastMock = now - 3 * 86_400_000L))
        assertTrue(doneThisWeek.first { it.kind == Kind.FULL_MOCK }.done)
    }

    @Test fun streakCountsBackFromTodayOrYesterday() {
        val days = (0..4).map { Planner.startOfDay(now - it * 86_400_000L, tz) }.toSet()
        assertEquals(5, Planner.streak(days, now, tz))
        assertEquals(4, Planner.streak(days - Planner.startOfDay(now, tz), now, tz))   // today not studied yet
        assertEquals(0, Planner.streak(emptySet(), now, tz))
    }
}
