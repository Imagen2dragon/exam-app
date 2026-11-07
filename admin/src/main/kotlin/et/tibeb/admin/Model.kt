package et.tibeb.admin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Mirrors the server's admin API (server/admin.py). Unknown fields are ignored so newer servers keep working.

@Serializable data class Totals(
    val exams_done: Int = 0, val avg_score_pct: Double = 0.0, val mocks_done: Int = 0, val best_mock_total: Double = 0.0,
    val cards_total: Int = 0, val cards_due: Int = 0, val mistakes_open: Int = 0, val books: Int = 0, val bank_questions: Int = 0,
)
@Serializable data class SubjectSummary(val subject: String, val exams: Int, val avg_pct: Double, val last_pct: Double)
@Serializable data class TopicSummary(val subject: String, val topic: String, val attempted: Int, val accuracy: Double)
@Serializable data class ExamSummary(val title: String, val subject: String, val finished_at: Long, val score: Double, val max_score: Double, val mock: Boolean = false)
@Serializable data class MockSummary(val created_at: Long, val total: Double, val papers_done: Int)
@Serializable data class BookSummary(val title: String, val subject: String, val status: String)
@Serializable data class Progress(
    val app_version: String = "", val generated_at: Long = 0, val stream: String = "natural", val grade: Int = 12,
    val exam_date: Long? = null, val streak: Int = 0, val totals: Totals = Totals(),
    val subjects: List<SubjectSummary> = emptyList(), val topics: List<TopicSummary> = emptyList(),
    val recent_exams: List<ExamSummary> = emptyList(), val mocks: List<MockSummary> = emptyList(),
    val activity_days: List<Long> = emptyList(), val books: List<BookSummary> = emptyList(),
    val last_backup: Long? = null,
)
@Serializable data class Device(val device: String, val label: String = "", val last_seen: Double? = null,
                                val latest: Progress? = null, val today_usd: Double = 0.0) {
    val name get() = label.ifBlank { "Device $device" }
}
@Serializable data class Devices(val devices: List<Device>, val budget_usd: Double)
@Serializable data class TrendPoint(
    val day: String, val avg_score_pct: Double, val exams_done: Int, val mistakes_open: Int,
    val best_mock_total: Double, val streak: Int, val cards_total: Int,
)
@Serializable data class History(val device: String, val latest: Progress, val history: List<TrendPoint>)
@Serializable data class UsageDay(val day: String, val requests: Int, val usd: Double, val input_tokens: Long? = 0,
                                  val cached_share: Double = 0.0, val output_tokens: Long? = 0)
@Serializable data class Feature(val endpoint: String, val model: String, val requests: Int, val usd: Double)
@Serializable data class Usage(val device: String, val budget_usd: Double, val days: List<UsageDay>,
                               val by_feature: List<Feature>, val total_usd: Double)

val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

/** Friendly names for the server's endpoints in the AI-use table. */
fun featureName(endpoint: String) = when (endpoint) {
    "/v1/extract" -> "Reading books & past papers"
    "/v1/exam" -> "Practice exams"
    "/v1/mock-paper" -> "National mock papers"
    "/v1/grade" -> "Grading written answers & graphs"
    "/v1/notes" -> "Revision notes"
    "/v1/flashcards" -> "Flashcards"
    "/v1/explain" -> "Tutor chat"
    "/v1/packs" -> "Question packs (half price)"
    else -> endpoint
}
