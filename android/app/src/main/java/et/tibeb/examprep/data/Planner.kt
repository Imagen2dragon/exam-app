package et.tibeb.examprep.data

import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Builds today's study plan from what the student actually has: due flashcards, open mistakes,
 * weakest subject, and how close the exam is. Pure logic (no Android), so it is unit-tested.
 */
object Planner {
    enum class Kind { REVIEW_CARDS, FIX_MISTAKES, PRACTICE_EXAM, FULL_MOCK }

    data class Item(val kind: Kind, val count: Int = 0, val subject: String = "", val done: Boolean = false)

    data class Input(
        val now: Long,
        val examDate: Long?,                 // null = not set yet
        val dueCards: Int,
        val reviewedToday: Int,
        val mistakes: Int,
        val streamSubjects: List<String>,
        val subjectAccuracy: Map<String, Double>,   // 0..1, only subjects with data
        val examsFinishedToday: List<String>,        // subjects
        val lastMockFinished: Long?,
    )

    const val MOCK_WINDOW_DAYS = 60
    private const val MISTAKES_PER_DAY = 10

    /** Whole calendar days from today to the exam day in the phone's time zone (0 = exam is today). */
    fun daysUntil(examDate: Long, now: Long, tz: TimeZone = TimeZone.getDefault()): Int =
        TimeUnit.MILLISECONDS.toDays(startOfDay(examDate, tz) - startOfDay(now, tz) + TimeUnit.HOURS.toMillis(12)).toInt()

    fun startOfDay(t: Long, tz: TimeZone = TimeZone.getDefault()): Long = Calendar.getInstance(tz).apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Weakest subject by accuracy; subjects with no results yet rotate by day so every subject gets time. */
    fun focusSubject(input: Input): String? {
        val subjects = input.streamSubjects.ifEmpty { return null }
        val untried = subjects.filter { it !in input.subjectAccuracy }
        if (untried.isNotEmpty()) return untried[(input.now / TimeUnit.DAYS.toMillis(1) % untried.size).toInt()]
        return subjects.minByOrNull { input.subjectAccuracy[it] ?: 0.0 }
    }

    fun plan(input: Input): List<Item> = buildList {
        if (input.dueCards > 0 || input.reviewedToday > 0)
            add(Item(Kind.REVIEW_CARDS, input.dueCards, done = input.dueCards == 0))
        if (input.mistakes > 0) add(Item(Kind.FIX_MISTAKES, minOf(input.mistakes, MISTAKES_PER_DAY)))
        focusSubject(input)?.let { s -> add(Item(Kind.PRACTICE_EXAM, subject = s, done = s in input.examsFinishedToday)) }
        val days = input.examDate?.let { daysUntil(it, input.now) }
        if (days != null && days in 0..MOCK_WINDOW_DAYS) {
            val mockThisWeek = input.lastMockFinished?.let { input.now - it < TimeUnit.DAYS.toMillis(7) } == true
            add(Item(Kind.FULL_MOCK, done = mockThisWeek))
        }
    }

    /** Consecutive days with any study activity, counting back from today (or yesterday if today is still empty). */
    fun streak(activeDays: Set<Long>, now: Long, tz: TimeZone = TimeZone.getDefault()): Int {
        val day = TimeUnit.DAYS.toMillis(1)
        var d = startOfDay(now, tz)
        if (d !in activeDays) d = startOfDay(d - day / 2, tz)
        var n = 0
        while (d in activeDays) { n++; d = startOfDay(d - day / 2, tz) }
        return n
    }
}
