package et.tibeb.examprep.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import et.tibeb.examprep.BuildConfig
import et.tibeb.examprep.TibebApp
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

// The progress summary shared with the admin (the person who set up the app). Results and activity
// only - never notes text or tutor chats. Field names match the server's admin.Progress model.

@Serializable data class SubjectSummary(val subject: String, val exams: Int, val avg_pct: Double, val last_pct: Double)
@Serializable data class TopicSummary(val subject: String, val topic: String, val attempted: Int, val accuracy: Double)
@Serializable data class ExamSummary(val title: String, val subject: String, val finished_at: Long, val score: Double, val max_score: Double, val mock: Boolean)
@Serializable data class MockSummary(val created_at: Long, val total: Double, val papers_done: Int)
@Serializable data class BookSummary(val title: String, val subject: String, val status: String)
@Serializable data class Totals(
    val exams_done: Int, val avg_score_pct: Double, val mocks_done: Int, val best_mock_total: Double, val cards_total: Int,
    val cards_due: Int, val mistakes_open: Int, val books: Int, val bank_questions: Int,
)
@Serializable data class ProgressSummary(
    val app_version: String, val generated_at: Long, val stream: String, val grade: Int, val exam_date: Long?,
    val streak: Int, val totals: Totals, val subjects: List<SubjectSummary>, val topics: List<TopicSummary>,
    val recent_exams: List<ExamSummary>, val mocks: List<MockSummary>, val activity_days: List<Long>, val books: List<BookSummary>,
)

private fun pct(e: Exam) = if (e.maxScore <= 0) 0.0 else (e.score / e.maxScore * 100).coerceIn(0.0, 100.0)

/** Builds the summary from the phone's database. Pure reads; capped to the server's size limits. */
suspend fun buildProgress(dao: StudyDao, stream: String, grade: Int, examDate: Long?, activeDays: Set<Long>, now: Long): ProgressSummary {
    val exams = dao.allExams().first()
    val finished = exams.filter { it.finishedAt > 0 && it.maxScore > 0 }.sortedByDescending { it.finishedAt }
    val mocks = dao.mocks().first().map { m ->
        val papers = finished.filter { it.mockId == m.id }
        MockSummary(m.createdAt, papers.sumOf { pct(it) }.coerceAtMost(600.0), papers.size.coerceAtMost(6))
    }
    val completeMocks = mocks.filter { it.papers_done == 6 }
    val tiles = Mastery.tiles(dao.allBookTopics().first().map { it.subject to it.topic }, dao.topicStats().first())
    val docs = dao.documents().first()
    return ProgressSummary(
        app_version = BuildConfig.VERSION_NAME, generated_at = now, stream = stream, grade = grade, exam_date = examDate,
        streak = Planner.streak(activeDays, now),
        totals = Totals(
            exams_done = finished.size,
            avg_score_pct = finished.take(20).map(::pct).average().takeIf { !it.isNaN() } ?: 0.0,
            mocks_done = completeMocks.size,
            best_mock_total = completeMocks.maxOfOrNull { it.total } ?: 0.0,
            cards_total = dao.cardCount().first(), cards_due = dao.dueCount(now).first(),
            mistakes_open = dao.mistakes().first().size, books = docs.size, bank_questions = dao.bankCount(),
        ),
        subjects = finished.groupBy { it.subject }.map { (s, es) -> SubjectSummary(s, es.size, es.map(::pct).average(), pct(es.first())) }.take(12),
        topics = tiles.filter { it.attempted > 0 }.map { TopicSummary(it.subject, it.topic.take(120), it.attempted, it.accuracy.coerceIn(0.0, 1.0)) }.take(400),
        recent_exams = finished.take(40).map { ExamSummary(it.title.take(200), it.subject, it.finishedAt, it.score, it.maxScore, it.mockId != null) },
        mocks = mocks.takeLast(20),
        activity_days = activeDays.sorted().takeLast(400),
        books = docs.take(150).map { BookSummary(it.title.take(200), it.subject, it.status) },
    )
}

/** Sends the summary every ~6 hours (and after each finished exam) whenever the phone is online. */
class ProgressWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result =
        if ((applicationContext as TibebApp).shareProgress()) Result.success() else Result.retry()

    companion object {
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        // Scheduling must never break the caller (e.g. finishing an exam), so failures are ignored.
        fun schedule(ctx: Context) = runCatching {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("progress", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ProgressWorker>(6, TimeUnit.HOURS).setConstraints(online).build())
        }

        fun now(ctx: Context) = runCatching {
            WorkManager.getInstance(ctx).enqueueUniqueWork("progress-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ProgressWorker>().setConstraints(online).build())
        }
    }
}
