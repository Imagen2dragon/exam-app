package et.tibeb.examprep

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import et.tibeb.examprep.data.AppDb
import et.tibeb.examprep.data.ImportOutcome
import et.tibeb.examprep.data.Repository
import et.tibeb.examprep.net.Api
import et.tibeb.examprep.net.ApiChunk
import et.tibeb.examprep.net.ApiException
import et.tibeb.examprep.net.ExtractReq
import et.tibeb.examprep.net.ExtractRes
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Uploads must survive bad signal: queue first, retry on network errors, fail clearly on real errors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundImportTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDb
    private lateinit var repo: Repository
    private var behaviour: () -> ExtractRes = { error("not set") }
    private val sent = mutableListOf<ExtractReq>()

    private val fakeApi = object : Api({ "https://x" }, { "t" }, { id, _ -> ctx.getString(id) }) {
        override suspend fun extract(r: ExtractReq): ExtractRes { sent += r; return behaviour() }
    }

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        db = Room.inMemoryDatabaseBuilder(ctx, AppDb::class.java).allowMainThreadQueries().build()
        repo = Repository(ctx, db, fakeApi) { id, a -> ctx.getString(id, *a) }
    }

    @After fun tearDown() { db.close(); File(ctx.filesDir, "pending").deleteRecursively() }

    private fun pdf(): Uri = Uri.fromFile(File(ctx.cacheDir, "unit3.pdf").apply { writeBytes("%PDF-1.4 test".toByteArray()) })
    private fun pending(id: Long) = File(ctx.filesDir, "pending/$id")
    private fun queue(): Long = runBlocking { repo.queueImport(pdf(), "application/pdf", "Unit 3", "Mathematics", 12, "textbook") }

    @Test fun queueingCopiesTheFileAndSchedulesWork() {
        val id = queue()
        assertEquals("queued", runBlocking { repo.dao.document(id)!!.status })
        assertTrue(File(pending(id), "book.pdf").exists())
        val work = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork("import-$id").get()
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
    }

    @Test fun successStoresTextAndDeletesTheCopy() = runBlocking {
        val id = queue()
        behaviour = { ExtractRes("Limits", listOf(ApiChunk("Unit 3", "Limits", "text")), emptyList()) }
        assertEquals(ImportOutcome.DONE, repo.processImport(id, giveUp = false))
        assertEquals("ready", repo.dao.document(id)!!.status)
        assertEquals("application/pdf", sent.single().mime)
        assertFalse(pending(id).exists())
    }

    @Test fun noSignalRetriesLaterAndKeepsTheFile() = runBlocking {
        val id = queue()
        behaviour = { throw ApiException("No connection", retryable = true) }
        assertEquals(ImportOutcome.RETRY, repo.processImport(id, giveUp = false))
        val doc = repo.dao.document(id)!!
        assertEquals("queued", doc.status); assertEquals("No connection", doc.error)
        assertTrue(pending(id).exists())
        // after many attempts it stops and asks the student
        assertEquals(ImportOutcome.FAILED, repo.processImport(id, giveUp = true))
    }

    @Test fun realErrorFailsButCanBeRetried() = runBlocking {
        val id = queue()
        behaviour = { throw ApiException("Device token rejected") }
        assertEquals(ImportOutcome.FAILED, repo.processImport(id, giveUp = false))
        assertEquals("failed", repo.dao.document(id)!!.status)
        assertTrue("file kept for Retry", pending(id).exists())
        behaviour = { ExtractRes("ok", emptyList(), emptyList()) }
        assertEquals(ImportOutcome.DONE, repo.processImport(id, giveUp = false))
    }

    @Test fun deletingCancelsAndRemovesTheCopy() = runBlocking {
        val id = queue()
        repo.deleteDocument(id)
        assertFalse(pending(id).exists())
        assertEquals(null, repo.dao.document(id))
        val work = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork("import-$id").get()
        assertEquals(WorkInfo.State.CANCELLED, work.single().state)
    }

    @Test fun startupRecoveryOnlyTouchesOldInterruptedUploads() = runBlocking {
        val old = repo.dao.insert(et.tibeb.examprep.data.Document(title = "old", subject = "Physics", grade = 12,
            docType = "textbook", status = "processing", addedAt = 0))
        val fresh = queue()
        Repository(ctx, db, fakeApi) { id, a -> ctx.getString(id, *a) } // simulates the next app start
        var status = ""
        repeat(50) { status = repo.dao.document(old)!!.status; if (status == "failed") return@repeat; Thread.sleep(20) }
        assertEquals("failed", status)
        assertEquals("queued", repo.dao.document(fresh)!!.status)
    }

    @Test fun chapterPhotosAreSentAsOrderedPages() = runBlocking {
        val staged = (1..3).map { n -> File(ctx.cacheDir, "s$n.jpg").apply { writeBytes("page-$n".toByteArray()) } }
        val id = repo.queuePages(staged, "Biology Unit 2", "Biology", 11, "textbook")
        assertEquals(listOf("page_001.jpg", "page_002.jpg", "page_003.jpg"), pending(id).list()!!.sorted())
        assertTrue("staged files moved, not duplicated", staged.none { it.exists() })
        behaviour = { ExtractRes("ok", emptyList(), emptyList()) }
        assertEquals(ImportOutcome.DONE, repo.processImport(id, giveUp = false))
        val req = sent.single()
        assertEquals("", req.data_b64)
        val decoded = req.pages.map { String(android.util.Base64.decode(it.data_b64, 0)) }
        assertEquals(listOf("page-1", "page-2", "page-3"), decoded)
    }
}
