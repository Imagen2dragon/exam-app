package et.tibeb.examprep

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import et.tibeb.examprep.data.AppDb
import et.tibeb.examprep.data.ExamConfig
import et.tibeb.examprep.data.Pack
import et.tibeb.examprep.data.PackOutcome
import et.tibeb.examprep.data.Repository
import et.tibeb.examprep.net.Api
import et.tibeb.examprep.net.ApiException
import et.tibeb.examprep.net.ApiQuestion
import et.tibeb.examprep.net.PackRes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Packs are collected in the background and land in the offline question bank. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PackTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDb
    private lateinit var repo: Repository
    private var reply: () -> PackRes = { PackRes("p", status = "processing") }
    private val api = object : Api({ "https://x" }, { "t" }, { id, _ -> ctx.getString(id) }) {
        override suspend fun pack(id: String): PackRes = reply()
    }
    private val q = ApiQuestion(type = "mcq", stem = "Speed of light?", options = listOf("a", "b", "c", "d"), answer = "C", topic = "Optics")

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        db = Room.inMemoryDatabaseBuilder(ctx, AppDb::class.java).allowMainThreadQueries().build()
        repo = Repository(ctx, db, api) { id, a -> ctx.getString(id, *a) }
    }
    @After fun tearDown() = db.close()

    private fun pack(createdAt: Long = System.currentTimeMillis()) =
        runBlocking { repo.dao.insert(Pack(serverId = "p1", subject = "Physics", count = 50, topics = "Optics", createdAt = createdAt)) }

    @Test fun waitsThenImportsIntoTheOfflineBank() = runBlocking {
        val id = pack()
        assertEquals(PackOutcome.WAIT, repo.collectPack(id))
        reply = { PackRes("p1", "Physics", "ready", List(30) { q.copy(stem = "Q$it") }) }
        assertEquals(PackOutcome.DONE, repo.collectPack(id))
        val p = repo.dao.pack(id)!!
        assertEquals("imported", p.status); assertEquals(30, p.received)
        assertEquals(30, repo.dao.pastCount("Physics").first())
        // offline exam now draws from the pack
        val exam = repo.createExam(ExamConfig("Physics", "", 12, 20, "standard", listOf("mcq"), useAi = false))
        assertEquals(20, repo.dao.items(exam).first().size)
        assertEquals(PackOutcome.DONE, repo.collectPack(id))      // already imported: no duplicates
        assertEquals(30, repo.dao.pastCount("Physics").first())
    }

    @Test fun networkErrorsWaitRealErrorsFail() = runBlocking {
        val id = pack()
        reply = { throw ApiException("offline", retryable = true) }
        assertEquals(PackOutcome.WAIT, repo.collectPack(id))
        reply = { throw ApiException("Pack not found") }
        assertEquals(PackOutcome.FAILED, repo.collectPack(id))
        assertEquals("failed", repo.dao.pack(id)!!.status)
    }

    @Test fun serverFailureAndExpiry() = runBlocking {
        reply = { PackRes("p1", status = "failed") }
        val failed = pack()
        assertEquals(PackOutcome.FAILED, repo.collectPack(failed))
        val old = pack(createdAt = System.currentTimeMillis() - 49L * 60 * 60 * 1000)
        assertEquals(PackOutcome.FAILED, repo.collectPack(old))
        assertEquals(ctx.getString(R.string.pack_expired), repo.dao.pack(old)!!.error)
    }
}
