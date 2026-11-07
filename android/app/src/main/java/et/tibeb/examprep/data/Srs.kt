package et.tibeb.examprep.data

import kotlin.math.max
import kotlin.math.roundToInt

private const val DAY = 24L * 60 * 60 * 1000

/** SM-2 spaced repetition (the algorithm behind Anki). grade: 0=Again, 3=Hard, 4=Good, 5=Easy. */
fun Card.review(grade: Int, now: Long = System.currentTimeMillis()): Card {
    if (grade < 3) return copy(reps = 0, intervalDays = 0, due = now + 10 * 60 * 1000) // retry in 10 min
    val newEase = max(1.3, ease + (0.1 - (5 - grade) * (0.08 + (5 - grade) * 0.02)))
    val interval = when (reps) {
        0 -> 1
        1 -> if (grade == 5) 4 else 3
        else -> (intervalDays * newEase).roundToInt().coerceAtLeast(intervalDays + 1)
    }
    return copy(ease = newEase, reps = reps + 1, intervalDays = interval, due = now + interval * DAY)
}

/** Builds a safe FTS4 MATCH expression from free text (prevents FTS syntax errors/injection). */
fun ftsQuery(text: String): String? {
    val words = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length > 2 && it !in STOP }.distinct().take(8)
    return if (words.isEmpty()) null else words.joinToString(" OR ") { "$it*" }
}

private val STOP = setOf("the", "and", "for", "with", "from", "into", "unit", "chapter", "grade")
