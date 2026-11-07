package et.tibeb.examprep

import android.content.Context
import et.tibeb.examprep.backup.Backup
import et.tibeb.examprep.backup.BackupConfig
import et.tibeb.examprep.backup.BackupCrypto
import et.tibeb.examprep.backup.BackupError
import et.tibeb.examprep.backup.BackupState
import et.tibeb.examprep.backup.Supabase
import et.tibeb.examprep.data.AppDb
import et.tibeb.examprep.data.Card
import et.tibeb.examprep.security.SecureStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import kotlin.concurrent.thread

/** The Keystore isn't available under Robolectric; the backup logic only needs get/put/remove. */
class MemorySecureStore(ctx: Context) : SecureStore(ctx) {
    private val m = mutableMapOf<String, String>()
    override fun put(name: String, value: String) { m[name] = value }
    override fun get(name: String): String? = m[name]
    override fun remove(name: String) { m.remove(name) }
}

/**
 * A stand-in for Supabase Auth + Storage with the same rules as supabase/setup.sql: a user can only
 * reach `backups/<their id>/`, refresh tokens are single-use, the public key must be sent.
 */
class FakeSupabase(val key: String = "sb_publishable_test") {
    val users = mutableMapOf("brother@example.com" to ("correct horse 9" to "11111111-aaaa-bbbb-cccc-000000000001"))
    val files = mutableMapOf<String, ByteArray>()
    private val access = mutableMapOf<String, String>()     // token -> user id
    private val refresh = mutableMapOf<String, String>()
    private var n = 0
    var uploads = 0
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val url get() = "http://127.0.0.1:${server.localPort}"

    /** One request per connection is all HttpURLConnection needs here. */
    class Exchange(val method: String, val uri: URI, val headers: Map<String, String>, val body: ByteArray) {
        var code = 0; var out = ByteArray(0)
        fun header(name: String) = headers[name.lowercase()]
    }

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                runCatching { serve(sock) }
                runCatching { sock.close() }
            }
        }
    }

    private fun serve(sock: Socket) {
        val input = BufferedInputStream(sock.getInputStream())
        fun line(): String { val b = StringBuilder(); while (true) { val c = input.read(); if (c < 0 || c == '\n'.code) break; if (c != '\r'.code) b.append(c.toChar()) }; return b.toString() }
        val (method, target) = line().split(" ").let { it[0] to it[1] }
        val headers = generateSequence { line().takeIf { it.isNotEmpty() } }.associate { h -> h.substringBefore(":").trim().lowercase() to h.substringAfter(":").trim() }
        val body = ByteArray(headers["content-length"]?.toInt() ?: 0).also { b -> var r = 0; while (r < b.size) { val k = input.read(b, r, b.size - r); if (k < 0) break; r += k } }
        val ex = Exchange(method, URI(target), headers, body)
        runCatching { handle(ex) }.onFailure { ex.code = 500; ex.out = "{}".toByteArray() }
        val out = sock.getOutputStream()
        out.write("HTTP/1.1 ${ex.code} X\r\nContent-Length: ${ex.out.size}\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(ex.out); out.flush()
    }

    fun stop() = server.close()
    fun revokeAll() { refresh.clear(); access.clear() }

    private fun issue(user: String): String {
        n++; access["acc$n"] = user; refresh["ref$n"] = user
        return """{"access_token":"acc$n","refresh_token":"ref$n","token_type":"bearer","user":{"id":"$user","aud":"authenticated"}}"""
    }

    private fun handle(ex: Exchange) {
        if (ex.header("apikey") != key) return reply(ex, 401, """{"message":"Invalid API key"}""".toByteArray())
        val body = ex.body
        val path = ex.uri.path
        if (path == "/auth/v1/token") {
            val text = String(body)
            fun field(f: String) = Regex("\"$f\":\"([^\"]*)\"").find(text)?.groupValues?.get(1)
            val user = when (ex.uri.query) {
                "grant_type=password" -> users[field("email")]?.takeIf { it.first == field("password") }?.second
                "grant_type=refresh_token" -> refresh.remove(field("refresh_token"))      // single use
                else -> null
            } ?: return reply(ex, 400, """{"error":"invalid_grant"}""".toByteArray())
            return reply(ex, 200, issue(user).toByteArray())
        }
        val m = Regex("/storage/v1/object/backups/([^/]+)/tibeb.backup").matchEntire(path) ?: return reply(ex, 404, "{}".toByteArray())
        val user = access[ex.header("Authorization")?.removePrefix("Bearer ")]
            ?: return reply(ex, 403, """{"statusCode":"403","error":"Unauthorized"}""".toByteArray())
        if (m.groupValues[1] != user) return reply(ex, 403, """{"statusCode":"403","error":"Unauthorized","message":"new row violates row-level security policy"}""".toByteArray())
        when (ex.method) {
            "POST" -> {
                if (files.containsKey(user) && ex.header("x-upsert") != "true") return reply(ex, 409, "{}".toByteArray())
                files[user] = body; uploads++
                reply(ex, 200, """{"Key":"backups/$user/tibeb.backup"}""".toByteArray())
            }
            "GET" -> files[user]?.let { reply(ex, 200, it) }
                ?: reply(ex, 400, """{"statusCode":"404","error":"not_found","message":"Object not found"}""".toByteArray())
            else -> reply(ex, 405, "{}".toByteArray())
        }
    }

    private fun reply(ex: Exchange, code: Int, bytes: ByteArray) { ex.code = code; ex.out = bytes }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudBackupTest {
    private val app = RuntimeEnvironment.getApplication() as TibebApp
    private val fake = FakeSupabase()
    private val email = "brother@example.com"
    private val password = "correct horse 9"

    private fun phone(secure: SecureStore = MemorySecureStore(app)) =
        Backup(app, app.prefs, secure, { app.repo.db }, fetchConfig = { BackupConfig(fake.url, fake.key) },
            client = { Supabase(it, allowHttpForTests = true) })

    @Before fun clean() {
        app.getSharedPreferences("backup", 0).edit().clear().commit()
        File(app.filesDir, "restore").deleteRecursively()
    }

    @After fun stop() = fake.stop()

    private fun addCard(front: String) = runBlocking { app.repo.dao.insertCards(listOf(Card(subject = "Physics", topic = "Electricity", front = front, back = "V = I R"))) }
    private fun fronts(db: AppDb = app.repo.db) = db.openHelper.readableDatabase.query("SELECT front FROM Card").use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    @Test fun backUpThenRestoreOnANewPhone() = runBlocking {
        addCard("Ohm's law - marker 7Q")
        app.prefs.save(app.prefs.settings.value.copy(examDate = 1_814_000_000_000, grade = 12, stream = "natural"))

        // Phone 1: sign in, get a recovery code, confirm -> first backup uploaded.
        val p1 = phone()
        p1.config()
        assertEquals(BackupState.SignedOut, p1.state.value)
        p1.signIn(email, password)
        val code = (p1.state.value as BackupState.NewCode).code
        p1.confirmCode()
        val active = p1.state.value as BackupState.Active
        assertTrue(active.lastBackup > 0 && active.lastSize > 0)
        val stored = fake.files.values.single()
        assertFalse("the upload must be encrypted", String(stored, Charsets.ISO_8859_1).contains("marker 7Q"))
        assertEquals(app.repo.db.openHelper.readableDatabase.version, BackupCrypto.readHeader(stored).schema)

        // Nothing changed -> the daily job skips the upload; a change -> uploads (with a rotated refresh token).
        assertFalse(p1.backupNow())
        addCard("Kirchhoff - marker 9X")
        assertTrue(p1.backupNow())
        assertEquals(2, fake.uploads)
        assertEquals(p1.lastBackup(), (p1.state.value as BackupState.Active).lastBackup)

        // Phone 2 (fresh secure store, no backup prefs): sign in -> the backup is found.
        app.getSharedPreferences("backup", 0).edit().clear().commit()
        val p2 = phone()
        p2.signIn(email, password)
        assertTrue(p2.state.value is BackupState.Found)
        assertEquals(BackupError.Reason.BAD_CODE, runCatching { p2.restore("12345") }.exceptionOrNull().let { (it as BackupError).reason })
        assertEquals(BackupError.Reason.WRONG_CODE, runCatching { p2.restore(BackupCrypto.newRecoveryCode()) }.exceptionOrNull().let { (it as BackupError).reason })
        p2.restore(code.lowercase().replace("-", " "))                 // typed loosely: still accepted
        assertTrue(p2.state.value is BackupState.Active)
        assertEquals(code, p2.recoveryCode())                          // keeps backing up with the written-down code

        // The phone is "wiped", the app restarts: the restored database goes in before Room opens it.
        app.repo.db.openHelper.writableDatabase.execSQL("DELETE FROM Card")
        app.prefs.save(app.prefs.settings.value.copy(examDate = 0, grade = 9))
        app.repo.db.close()
        Backup.applyPendingRestore(app, app.prefs)
        val reopened = AppDb.create(app)
        assertEquals(setOf("Ohm's law - marker 7Q", "Kirchhoff - marker 9X"), fronts(reopened).toSet())
        assertEquals(1_814_000_000_000, app.prefs.settings.value.examDate)
        assertEquals(12, app.prefs.settings.value.grade)
        assertFalse(File(app.filesDir, "restore").exists())
        reopened.close()
    }

    @Test fun wrongPasswordAndBlockedAccountsAreExplained() = runBlocking {
        val p = phone()
        assertEquals(BackupError.Reason.BAD_LOGIN, (runCatching { p.signIn(email, "nope") }.exceptionOrNull() as BackupError).reason)
        p.signIn(email, password); p.confirmCode()
        fake.revokeAll()                                               // e.g. password changed in the dashboard
        addCard("after revoke")
        assertEquals(BackupError.Reason.SIGNED_OUT, (runCatching { p.backupNow() }.exceptionOrNull() as BackupError).reason)
        assertTrue((p.state.value as BackupState.Active).needsSignIn)
        p.signIn(email, password)                                      // same account: no new code
        val s = p.state.value as BackupState.Active
        assertFalse(s.needsSignIn)
        assertTrue(p.backupNow())
    }

    @Test fun aBackupFromANewerAppVersionIsRefused() = runBlocking {
        val user = fake.users.getValue(email).second
        val salt = BackupCrypto.newSalt(); val code = BackupCrypto.newRecoveryCode()
        fake.files[user] = BackupCrypto.encrypt(ByteArray(200), BackupCrypto.deriveKey(code, salt, 1_000), salt, schema = 99,
            createdAt = 1L, iterations = 1_000)
        val p = phone()
        p.signIn(email, password)
        assertEquals(BackupError.Reason.NEWER_APP, (runCatching { p.restore(code) }.exceptionOrNull() as BackupError).reason)
    }

    @Test fun startingOverReplacesTheOldBackup() = runBlocking {
        val p1 = phone(); p1.signIn(email, password); p1.confirmCode()
        val oldCode = p1.recoveryCode()
        app.getSharedPreferences("backup", 0).edit().clear().commit()
        val p2 = phone(); p2.signIn(email, password)
        assertTrue(p2.state.value is BackupState.Found)
        p2.startNew(); p2.confirmCode()
        assertTrue(p2.recoveryCode() != oldCode)
        assertEquals(BackupError.Reason.WRONG_CODE, runCatching {
            val p3 = phone().also { app.getSharedPreferences("backup", 0).edit().clear().commit(); it.signIn(email, password) }
            p3.restore(oldCode!!)
        }.exceptionOrNull().let { (it as BackupError).reason })
    }

    @Test fun signingOutForgetsTheCodeButKeepsTheServerSetting() = runBlocking {
        val p = phone(); p.signIn(email, password); p.confirmCode()
        p.signOut()
        assertEquals(BackupState.SignedOut, p.state.value)
        assertEquals(null, p.recoveryCode())
        assertEquals(null, p.lastBackup())
    }

    @Test fun progressSummaryCarriesTheLastBackupTime() = runBlocking {
        val p = phone(); p.signIn(email, password); p.confirmCode()
        val summary = et.tibeb.examprep.data.buildProgress(app.repo.dao, "natural", 12, null, emptySet(), System.currentTimeMillis(), p.lastBackup())
        assertNotNull(summary.last_backup)
        assertTrue(et.tibeb.examprep.net.json.encodeToString(et.tibeb.examprep.data.ProgressSummary.serializer(), summary).contains("\"last_backup\":"))
    }

    @Test fun onlyHttpsSupabaseIsAccepted() {
        assertTrue(runCatching { Supabase(BackupConfig("http://abc.supabase.co", "k")) }.isFailure)
        assertTrue(runCatching { Supabase(BackupConfig("https://abc.supabase.co", "k")) }.isSuccess)
    }
}
