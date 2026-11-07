package et.tibeb.examprep

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import et.tibeb.examprep.data.AppDb
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Upgrading from v0.1 must keep every exam result and add the mock tables. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDb::class.java)

    @Test fun migrate1To2KeepsData() {
        helper.createDatabase("m.db", 1).use {
            it.execSQL("INSERT INTO Exam (id, title, subject, minutes, createdAt, startedAt, finishedAt, score, maxScore) VALUES (1, 'Old', 'Physics', 20, 0, 0, 1, 7, 10)")
        }
        val db = helper.runMigrationsAndValidate("m.db", 2, true, AppDb.MIGRATION_1_2)
        db.query("SELECT title, mockId FROM Exam").use { c ->
            c.moveToFirst(); assertEquals("Old", c.getString(0)); assertEquals(true, c.isNull(1))
        }
        db.execSQL("INSERT INTO Mock (stream, questionsPerPaper, useAi, createdAt) VALUES ('natural', 60, 1, 0)")
    }
}
