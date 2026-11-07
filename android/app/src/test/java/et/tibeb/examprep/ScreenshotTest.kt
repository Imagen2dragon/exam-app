package et.tibeb.examprep

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import et.tibeb.examprep.data.Card
import et.tibeb.examprep.data.Chunk
import et.tibeb.examprep.data.Document
import et.tibeb.examprep.data.Exam
import et.tibeb.examprep.data.ExamItem
import et.tibeb.examprep.data.Mock
import et.tibeb.examprep.data.Note
import et.tibeb.examprep.data.Question
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the real app (real Room DB, real screens) with sample data and saves PNGs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
open class ScreenshotTest {
    open val dir = "../../docs/screenshots"
    private fun str(id: Int) = rule.activity.getString(id)
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) { rule.waitForIdle(); Thread.sleep(400); rule.waitForIdle(); rule.onRoot().captureRoboImage("$dir/$name.png") }

    private fun seed(): Long = runBlocking {
        val app = rule.activity.application as TibebApp
        val day = 86_400_000L
        app.prefs.save(app.prefs.settings.value.copy(serverUrl = "https://tibeb.example.com",
            examDate = System.currentTimeMillis() + 243 * day))
        val today = et.tibeb.examprep.data.Planner.startOfDay(System.currentTimeMillis())
        app.getSharedPreferences("activity", 0).edit()
            .putStringSet("days", (1..5).map { (et.tibeb.examprep.data.Planner.startOfDay(today - it * day + day / 2)).toString() }.toSet()).commit()
        val dao = app.repo.dao
        val doc = dao.insert(Document(title = "Grade 12 Mathematics Student Textbook – Unit 3", subject = "Mathematics", grade = 12,
            docType = "textbook", status = "ready", summary = "Introduction to limits and continuity: limits of sequences, limits of functions, continuity and the intermediate value theorem."))
        dao.insert(Document(title = "ESSLCE Physics 2016 E.C.", subject = "Physics", grade = 12, docType = "past_exam", status = "ready",
            summary = "Past national exam paper, 60 multiple-choice questions with answers."))
        dao.insert(Document(title = "Biology Grade 11 – Unit 2 (photos)", subject = "Biology", grade = 11, docType = "textbook",
            status = "queued", error = "No connection to the server. Offline features still work."))
        dao.insert(Document(title = "Chemistry 2015 E.C. national exam", subject = "Chemistry", grade = 12, docType = "past_exam",
            status = "failed", error = "Device token rejected. Check it in Settings."))
        dao.insertChunks(listOf("Kinematics", "Waves", "Optics", "Electrostatics", "Thermodynamics").map {
            Chunk(docId = doc, subject = "Physics", grade = 12, unit = "Unit", topic = it, text = "…") })
        dao.insertChunks(List(42) { Chunk(docId = doc, subject = "Mathematics", grade = 12, unit = "Unit 3", topic = "Limits", text = "…") })
        dao.insert(et.tibeb.examprep.data.Pack(serverId = "a", subject = "Chemistry", count = 100, topics = "Equilibrium, Acids and bases",
            status = "processing"))
        dao.insert(et.tibeb.examprep.data.Pack(serverId = "b", subject = "Physics", count = 200, topics = "", status = "imported", received = 196,
            createdAt = System.currentTimeMillis() - 86_400_000L))
        dao.insert(Note(subject = "Physics", topic = "Projectile motion", title = "Projectile motion – revision notes",
            markdown = "# Projectile motion\n## Key ideas\n- Horizontal velocity is **constant**: `vx = v0 cos θ`\n- Vertical motion has acceleration **g = 9.8 m/s²** downward\n## Exam traps\n- Time of flight depends only on the **vertical** component\n- At the top, speed is **not zero** – only vy is zero"))
        dao.insertCards(listOf(Card(subject = "Chemistry", topic = "Equilibrium", front = "What does Le Chatelier's principle state?",
            back = "A system at equilibrium shifts to oppose any change in concentration, temperature or pressure.", due = 0)))
        val qs = dao.insertQuestions(listOf(
            Question(subject = "Mathematics", grade = 12, topic = "Limits", type = "mcq",
                stem = "Evaluate \$\\lim_{x \\to 3} \\frac{x^2 - 9}{x - 3}\$", optionsJson = "[\"0\",\"3\",\"6\",\"\$\\\\infty\$\"]",
                answer = "C", explanation = "Factor: (x−3)(x+3)/(x−3) = x+3 → 6.", difficulty = "hard", marks = 1, source = "ai"),
            Question(subject = "Mathematics", grade = 12, topic = "Functions", type = "graph",
                stem = "Sketch y = 1/(x − 2) + 1. Show both asymptotes and intercepts.", optionsJson = "[]",
                answer = "Vertical asymptote x=2, horizontal y=1, x-intercept (1,0), y-intercept (0, 1/2).", explanation = "", difficulty = "hard", marks = 4, source = "ai"),
            Question(subject = "Mathematics", grade = 12, topic = "Derivatives", type = "numeric",
                stem = "f(x) = 3x³ − 2x. Find f′(2).", optionsJson = "[]", answer = "34", numericAnswer = 34.0, explanation = "f′(x)=9x²−2 → 36−2=34.",
                difficulty = "standard", marks = 2, source = "ai"),
        ))
        val exam = dao.insert(Exam(title = "Calculus – hard practice", subject = "Mathematics", minutes = 25, maxScore = 7.0))
        dao.insertItems(qs.mapIndexed { i, q -> ExamItem(examId = exam, idx = i, questionId = q, response = if (i == 0) "C" else "") })
        val done = dao.insert(Exam(title = "Physics mock – Mechanics", subject = "Physics", minutes = 30, maxScore = 7.0,
            startedAt = 1, finishedAt = System.currentTimeMillis(), score = 5.0))
        dao.insertItems(qs.mapIndexed { i, q -> ExamItem(examId = done, idx = i, questionId = q, response = listOf("C", "", "30")[i],
            earned = listOf(1.0, 4.0, 0.0)[i], graded = true,
            feedback = listOf("", "Both asymptotes and intercepts are correct and labelled.", "")[i]) })
        val mock = dao.insert(Mock(stream = "natural", questionsPerPaper = 60, useAi = true))
        listOf("English" to 41.0, "Mathematics" to 38.0, "Physics" to 52.0, "Chemistry" to 47.0).forEach { (sub, sc) ->
            dao.insert(Exam(title = "$sub – national mock", subject = sub, minutes = 72, maxScore = 60.0, score = sc,
                startedAt = 1, finishedAt = 2, mockId = mock))
        }
        dao.insert(Exam(title = "Biology – national mock", subject = "Biology", minutes = 72, maxScore = 60.0, mockId = mock))
        mockIdForTest = mock
        exam
    }

    private var mockIdForTest = 0L

    /** A plain "textbook page" image for the chapter-photos screenshot. */
    private fun samplePage(n: Int): String {
        val bmp = android.graphics.Bitmap.createBitmap(300, 400, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp); c.drawColor(0xFFFDFBF5.toInt())
        val p = android.graphics.Paint().apply { color = 0xFF9AA39D.toInt(); strokeWidth = 6f }
        for (y in 60 until 380 step 22) c.drawLine(30f, y.toFloat(), if (y % 5 == 0) 200f else 270f, y.toFloat(), p)
        p.color = 0xFF0E6B4E.toInt(); p.textSize = 34f; c.drawText("Unit 2 · p.$n", 30f, 44f, p)
        val f = java.io.File(rule.activity.filesDir, "staging/test/page$n.jpg").apply { parentFile!!.mkdirs() }
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        return f.absolutePath
    }

    @Test fun screens() {
        val examId = seed()
        shot("01_home")
        rule.onNodeWithText(str(R.string.tab_library)).performClick(); shot("02_library")
        rule.onNodeWithText(str(R.string.tab_study)).performClick(); shot("03_study")
        rule.onNodeWithText(str(R.string.tab_exams)).performClick(); shot("04_exams")
        rule.runOnUiThread { rule.activity.navForTest?.navigate("newexam?subject=Mathematics") }; shot("05_new_exam")
        rule.runOnUiThread { rule.activity.navForTest?.navigate("exam/$examId") }; shot("06_question_mcq")
        rule.onNodeWithText(str(R.string.ex_next)).performClick(); shot("07_question_graph")
        rule.runOnUiThread { rule.activity.navForTest?.navigate("result/${examId + 1}") }; shot("08_result")
        rule.runOnUiThread { rule.activity.navForTest?.navigate("review") }; shot("09_flashcard")
        rule.runOnUiThread { rule.activity.navForTest?.navigate("mocksetup") }; shot("10_mock_setup")
        rule.runOnUiThread { rule.activity.navForTest?.navigate("mock/$mockIdForTest") }; shot("11_mock_hub")
        rule.runOnUiThread { rule.activity.navForTest?.navigate("settings") }; shot("12_settings")
        rule.runOnUiThread { rule.activity.navForTest?.navigate("mistakes") }; shot("14_mistakes")
        rule.runOnUiThread { rule.activity.navForTest?.navigate("mastery") }; shot("15_mastery")
        rule.runOnUiThread { rule.activity.navForTest?.navigate("packs") }; shot("18_packs")
        rule.runOnUiThread { rule.activity.navForTest?.navigate("newexam?subject=${android.net.Uri.encode("Scholastic Aptitude")}&topic=${android.net.Uri.encode("Number series")}") }
        shot("16_new_exam_topic")
        // Chapter photos screen with three sample "pages"
        val app = rule.activity.application as TibebApp
        val pages = (1..3).map { n -> samplePage(n) }
        rule.runOnUiThread {
            rule.activity.setContent {
                et.tibeb.examprep.ui.theme.TibebTheme {
                    androidx.compose.runtime.CompositionLocalProvider(et.tibeb.examprep.ui.LocalApp provides app) {
                        et.tibeb.examprep.ui.screens.PagesScreen(androidx.navigation.compose.rememberNavController(), initialPages = pages)
                    }
                }
            }
        }
        shot("13_chapter_photos")
        // Explain chat with a short sample conversation
        val qid = runBlocking { app.repo.dao.mistakes().first() }.first().id
        val turns = listOf(
            et.tibeb.examprep.data.Repository.Turn(true, rule.activity.getString(R.string.explain_q_mine)),
            et.tibeb.examprep.data.Repository.Turn(false, "You probably differentiated **2x** as 2 but forgot the power rule on **3x³**.\n\n- f′(x) = 9x² − 2\n- f′(2) = 36 − 2 = **34**\n\nQuick check: what is f′(1)?"),
        )
        rule.runOnUiThread {
            rule.activity.setContent {
                et.tibeb.examprep.ui.theme.TibebTheme {
                    androidx.compose.runtime.CompositionLocalProvider(et.tibeb.examprep.ui.LocalApp provides app) {
                        et.tibeb.examprep.ui.screens.ExplainScreen(androidx.navigation.compose.rememberNavController(), qid, initialTurns = turns)
                    }
                }
            }
        }
        shot("17_explain")
    }
}

/** Same screens with the phone set to Amharic. */
@Config(sdk = [35], qualifiers = "am-w393dp-h852dp-xxhdpi")
class AmharicScreenshotTest : ScreenshotTest() {
    override val dir = "../../docs/screenshots/am"
}
