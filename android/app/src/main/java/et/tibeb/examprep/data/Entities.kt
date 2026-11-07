package et.tibeb.examprep.data

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

val SUBJECTS = listOf(
    "Mathematics", "Physics", "Chemistry", "Biology", "English",
    "Scholastic Aptitude", "Geography", "History", "Economics",
)
val NATURAL_STREAM = listOf("English", "Mathematics", "Scholastic Aptitude", "Physics", "Chemistry", "Biology")
val SOCIAL_STREAM = listOf("English", "Mathematics", "Scholastic Aptitude", "Geography", "History", "Economics")

/** An uploaded book/exam. The original file is NOT kept, only the extracted text (saves storage). */
@Entity
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String,
    val grade: Int,
    val docType: String,
    val addedAt: Long = System.currentTimeMillis(),
    val status: String = "processing", // processing | ready | failed
    val summary: String = "",
    val error: String = "",
)

@Entity(indices = [Index("docId"), Index("subject")])
data class Chunk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: Long,
    val subject: String,
    val grade: Int,
    val unit: String,
    val topic: String,
    val text: String,
)

/** Full-text index over chunks so the phone itself finds relevant pages for a topic (offline RAG). */
@Fts4(contentEntity = Chunk::class)
@Entity
data class ChunkFts(val topic: String, val text: String)

@Entity(indices = [Index("subject"), Index("source")])
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val grade: Int,
    val topic: String,
    val type: String,            // mcq | numeric | short | graph
    val stem: String,
    val optionsJson: String,     // JSON array of strings
    val answer: String,
    val numericAnswer: Double = 0.0,
    val tolerance: Double = 0.0,
    val unit: String = "",
    val explanation: String,
    val difficulty: String,
    val marks: Int,
    val source: String,          // past | ai
    val docId: Long? = null,
)

@Entity
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val topic: String,
    val title: String,
    val markdown: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Flashcard with SM-2 spaced-repetition state. */
@Entity(indices = [Index("due"), Index("questionId")])
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val topic: String,
    val front: String,
    val back: String,
    val ease: Double = 2.5,
    val intervalDays: Int = 0,
    val reps: Int = 0,
    val due: Long = System.currentTimeMillis(),
    val questionId: Long? = null,   // set for cards made automatically from a wrong exam answer
)

@Entity(indices = [Index("mockId")])
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String,
    val minutes: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    val score: Double = 0.0,
    val maxScore: Double = 0.0,
    val mockId: Long? = null,   // set when this exam is one paper of a full national mock
)

/** A full national mock: one paper per subject of the stream, each scored out of 100 (total 600). */
@Entity
data class Mock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stream: String,              // natural | social
    val questionsPerPaper: Int,
    val useAi: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Paper timing. EAES does not publish per-paper question counts or times; reports say 60-120
 * multiple-choice questions per paper. These per-question minutes are our estimate (more time
 * for calculation-heavy papers) - adjust here when official figures are known.
 */
val MINUTES_PER_QUESTION = mapOf(
    "Mathematics" to 2.0, "Physics" to 1.8, "Chemistry" to 1.6, "Economics" to 1.4,
    "Biology" to 1.2, "English" to 1.2, "Scholastic Aptitude" to 1.5, "Geography" to 1.2, "History" to 1.2,
)
fun paperMinutes(subject: String, questions: Int) = kotlin.math.ceil(questions * (MINUTES_PER_QUESTION[subject] ?: 1.3)).toInt()
fun streamSubjects(stream: String) = if (stream == "social") SOCIAL_STREAM else NATURAL_STREAM
fun Exam.percent() = if (maxScore <= 0) 0.0 else score / maxScore * 100

@Entity(primaryKeys = ["examId", "idx"], indices = [Index("questionId")])
data class ExamItem(
    val examId: Long,
    val idx: Int,
    val questionId: Long,
    val response: String = "",
    val imagePath: String = "",
    val earned: Double = 0.0,
    val feedback: String = "",
    val graded: Boolean = false,
)

data class ItemWithQuestion(
    val examId: Long, val idx: Int, val questionId: Long, val response: String,
    val imagePath: String, val earned: Double, val feedback: String, val graded: Boolean,
    val type: String, val stem: String, val optionsJson: String, val answer: String,
    val numericAnswer: Double, val tolerance: Double, val unit: String, val explanation: String,
    val marks: Int, val topic: String, val subject: String,
)

data class TopicStat(val subject: String, val topic: String, val attempted: Int, val earned: Double, val possible: Double) {
    val accuracy get() = if (possible == 0.0) 0.0 else earned / possible
}
