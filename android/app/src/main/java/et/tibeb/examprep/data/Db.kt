package et.tibeb.examprep.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    // Library
    @Query("SELECT * FROM Document ORDER BY addedAt DESC") fun documents(): Flow<List<Document>>
    @Insert suspend fun insert(d: Document): Long
    @Update suspend fun update(d: Document)
    @Query("SELECT * FROM Document WHERE id = :id") suspend fun document(id: Long): Document?
    @Query("DELETE FROM Document WHERE id = :id") suspend fun deleteDocument(id: Long)
    @Query("DELETE FROM Chunk WHERE docId = :id") suspend fun deleteChunks(id: Long)
    @Query("DELETE FROM Question WHERE docId = :id AND source = 'past'") suspend fun deletePastQuestions(id: Long)
    @Insert suspend fun insertChunks(c: List<Chunk>)
    @Query("SELECT COUNT(*) FROM Chunk WHERE docId = :id") fun chunkCount(id: Long): Flow<Int>

    /** Offline retrieval: FTS match on the topic words, best chunks first. */
    @Query("""SELECT Chunk.* FROM Chunk JOIN ChunkFts ON Chunk.rowid = ChunkFts.rowid
              WHERE ChunkFts MATCH :match AND Chunk.subject = :subject LIMIT :limit""")
    suspend fun searchChunks(subject: String, match: String, limit: Int): List<Chunk>
    @Query("SELECT * FROM Chunk WHERE subject = :subject ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomChunks(subject: String, limit: Int): List<Chunk>
    @Query("SELECT DISTINCT topic FROM Chunk WHERE subject = :subject ORDER BY topic")
    fun topics(subject: String): Flow<List<String>>

    // Question bank
    @Insert suspend fun insertQuestions(q: List<Question>): List<Long>
    @Query("SELECT COUNT(*) FROM Question WHERE subject = :subject AND source = 'past'")
    fun pastCount(subject: String): Flow<Int>
    @Query("""SELECT * FROM Question WHERE subject = :subject AND source = 'past' AND type IN (:types)
              ORDER BY RANDOM() LIMIT :limit""")
    suspend fun randomPast(subject: String, types: List<String>, limit: Int): List<Question>

    // Notes
    @Query("SELECT * FROM Note ORDER BY createdAt DESC") fun notes(): Flow<List<Note>>
    @Query("SELECT * FROM Note WHERE id = :id") fun note(id: Long): Flow<Note?>
    @Insert suspend fun insert(n: Note): Long
    @Query("DELETE FROM Note WHERE id = :id") suspend fun deleteNote(id: Long)

    // Flashcards
    @Insert suspend fun insertCards(c: List<Card>)
    @Update suspend fun update(c: Card)
    @Query("SELECT * FROM Card WHERE due <= :now ORDER BY due LIMIT 50") fun dueCards(now: Long): Flow<List<Card>>
    @Query("SELECT COUNT(*) FROM Card WHERE due <= :now") fun dueCount(now: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM Card") fun cardCount(): Flow<Int>

    // Exams
    @Insert suspend fun insert(e: Exam): Long
    @Update suspend fun update(e: Exam)
    @Insert suspend fun insertItems(i: List<ExamItem>)
    @Update suspend fun update(i: ExamItem)
    @Query("SELECT * FROM Exam WHERE mockId IS NULL ORDER BY createdAt DESC") fun exams(): Flow<List<Exam>>
    @Query("SELECT * FROM Exam ORDER BY createdAt DESC") fun allExams(): Flow<List<Exam>>

    // National mocks
    @Insert suspend fun insert(m: Mock): Long
    @Query("SELECT * FROM Mock ORDER BY createdAt DESC") fun mocks(): Flow<List<Mock>>
    @Query("SELECT * FROM Mock WHERE id = :id") fun mock(id: Long): Flow<Mock?>
    @Query("SELECT * FROM Mock WHERE id = :id") suspend fun mockNow(id: Long): Mock?
    @Query("SELECT * FROM Exam WHERE mockId = :id") fun mockPapers(id: Long): Flow<List<Exam>>
    @Query("SELECT * FROM Exam WHERE mockId = :id AND subject = :subject LIMIT 1") suspend fun mockPaper(id: Long, subject: String): Exam?
    @Query("SELECT COUNT(*) FROM Question WHERE docId = :id") fun questionCount(id: Long): Flow<Int>
    @Query("SELECT * FROM Exam WHERE id = :id") fun exam(id: Long): Flow<Exam?>
    @Query("SELECT * FROM Exam WHERE id = :id") suspend fun examNow(id: Long): Exam?
    @Query("SELECT * FROM ExamItem WHERE examId = :examId AND idx = :idx") suspend fun item(examId: Long, idx: Int): ExamItem?
    @Query("""SELECT i.examId, i.idx, i.questionId, i.response, i.imagePath, i.earned, i.feedback, i.graded,
              q.type, q.stem, q.optionsJson, q.answer, q.numericAnswer, q.tolerance, q.unit, q.explanation,
              q.marks, q.topic, q.subject
              FROM ExamItem i JOIN Question q ON q.id = i.questionId WHERE i.examId = :examId ORDER BY i.idx""")
    fun items(examId: Long): Flow<List<ItemWithQuestion>>

    /** Accuracy per topic across all finished exams - drives "weak topics". */
    @Query("""SELECT q.subject AS subject, q.topic AS topic, COUNT(*) AS attempted,
              SUM(i.earned) AS earned, SUM(q.marks) AS possible
              FROM ExamItem i JOIN Question q ON q.id = i.questionId JOIN Exam e ON e.id = i.examId
              WHERE e.finishedAt > 0 AND i.graded = 1 GROUP BY q.subject, q.topic""")
    fun topicStats(): Flow<List<TopicStat>>

    @Transaction
    suspend fun replaceDocumentContent(doc: Document, chunks: List<Chunk>, questions: List<Question>) {
        deleteChunks(doc.id); deletePastQuestions(doc.id)
        insertChunks(chunks); insertQuestions(questions); update(doc)
    }

    @Transaction
    suspend fun deleteDocumentFully(id: Long) { deleteChunks(id); deletePastQuestions(id); deleteDocument(id) }
}

@Database(
    entities = [Document::class, Chunk::class, ChunkFts::class, Question::class, Note::class,
        Card::class, Exam::class, ExamItem::class, Mock::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): StudyDao

    companion object {
        fun create(ctx: Context): AppDb =
            Room.databaseBuilder(ctx, AppDb::class.java, "tibeb.db").addMigrations(MIGRATION_1_2).build()

        /** v0.1 -> v0.2: national mocks. Keeps all existing books, notes, cards and results. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `Mock` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `stream` TEXT NOT NULL, `questionsPerPaper` INTEGER NOT NULL, `useAi` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("ALTER TABLE `Exam` ADD COLUMN `mockId` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Exam_mockId` ON `Exam` (`mockId`)")
            }
        }
    }
}
