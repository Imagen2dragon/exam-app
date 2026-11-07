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
@Entity(indices = [Index("due")])
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
)

@Entity
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
)

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
