package et.tibeb.examprep.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import et.tibeb.examprep.Prefs
import et.tibeb.examprep.TibebApp
import et.tibeb.examprep.data.AppDb
import et.tibeb.examprep.security.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** What the Settings card shows. */
sealed interface BackupState {
    /** The server has no backup set up (or the phone isn't connected to the server yet). */
    data object Unavailable : BackupState
    data object SignedOut : BackupState
    /** Signed in, new recovery code shown, waiting for "I wrote it down". */
    data class NewCode(val code: String) : BackupState
    /** Signed in on a phone without a code, and this account already has a backup: restore it or start over. */
    data class Found(val createdAt: Long, val size: Int) : BackupState
    data class Active(val lastBackup: Long, val lastSize: Int, val wifiOnly: Boolean, val needsSignIn: Boolean) : BackupState
}

/** A failure the UI can explain. */
class BackupError(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NOT_CONFIGURED, BAD_LOGIN, SIGNED_OUT, OFFLINE, TOO_BIG, SERVER, BAD_CODE, WRONG_CODE, NO_BACKUP, NEWER_APP, DAMAGED }
}

/** The study settings that travel with a backup (never the server address, tokens or keys). */
@Serializable data class BackupSettings(val stream: String, val grade: Int, val language: String, val examDate: Long)

/**
 * Encrypted cloud backup. Once a day the study database is copied, compressed, encrypted with the
 * key derived from the student's recovery code (see [BackupCrypto]) and uploaded to Supabase Storage.
 * Only the student's own account can reach the file; only the recovery code can open it.
 */
open class Backup(
    private val ctx: Context,
    private val prefs: Prefs,
    private val secure: SecureStore,
    private val db: () -> AppDb,
    /** Asks the Tibeb server for the Supabase address + public key (null = not set up there). */
    private val fetchConfig: suspend () -> BackupConfig?,
    private val client: (BackupConfig) -> Supabase = { Supabase(it) },
    private val newCode: () -> String = BackupCrypto::newRecoveryCode,
) {
    private val p = ctx.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(computeState())
    val state: StateFlow<BackupState> = _state

    private fun computeState(): BackupState = when {
        p.getBoolean("confirmed", false) && secure.get("bk_key") != null ->
            BackupState.Active(p.getLong("last", 0), p.getInt("last_size", 0), p.getBoolean("wifi_only", false), p.getBoolean("needs_login", false))
        secure.get("bk_refresh") != null && secure.get("bk_code") != null -> BackupState.NewCode(secure.get("bk_code")!!)
        secure.get("bk_refresh") != null && foundFile().exists() -> BackupState.Found(p.getLong("found_at", 0), foundFile().length().toInt())
        cachedConfig() == null -> BackupState.Unavailable
        else -> BackupState.SignedOut
    }

    private fun refreshState() { _state.value = computeState() }

    /** When the last backup was uploaded (for the admin's progress summary); null if backup is off. */
    fun lastBackup(): Long? = (state.value as? BackupState.Active)?.lastBackup?.takeIf { it > 0 }

    fun recoveryCode(): String? = secure.get("bk_code")

    // ---------- configuration ----------

    private fun cachedConfig(): BackupConfig? = p.getString("config", null)?.let { runCatching { json.decodeFromString<BackupConfig>(it) }.getOrNull() }

    /** Asks the server (it may have been set up since); falls back to the copy saved last time. */
    suspend fun config(): BackupConfig? {
        val fresh = runCatching { fetchConfig() }
        fresh.getOrNull()?.let { p.edit().putString("config", json.encodeToString(BackupConfig.serializer(), it)).apply() }
        if (fresh.isSuccess && fresh.getOrNull() == null) p.edit().remove("config").apply()   // switched off on the server
        refreshState()
        return cachedConfig()
    }

    private fun sb(): Supabase = client(cachedConfig() ?: throw BackupError(BackupError.Reason.NOT_CONFIGURED))

    private suspend fun session(): Session {
        val token = secure.get("bk_refresh") ?: throw BackupError(BackupError.Reason.SIGNED_OUT)
        val s = mapped { sb().refresh(token) }
        secure.put("bk_refresh", s.refreshToken)          // refresh tokens are single-use
        return s
    }

    private suspend fun <T> mapped(block: suspend () -> T): T = try { block() } catch (e: BackupException) {
        if (e.kind == BackupException.Kind.SIGNED_OUT) { p.edit().putBoolean("needs_login", true).apply(); refreshState() }
        throw BackupError(when (e.kind) {
            BackupException.Kind.BAD_LOGIN -> BackupError.Reason.BAD_LOGIN
            BackupException.Kind.SIGNED_OUT -> BackupError.Reason.SIGNED_OUT
            BackupException.Kind.OFFLINE -> BackupError.Reason.OFFLINE
            BackupException.Kind.TOO_BIG -> BackupError.Reason.TOO_BIG
            BackupException.Kind.SERVER -> BackupError.Reason.SERVER
        })
    }

    // ---------- setting up ----------

    /** Signs in to backup. Afterwards the state is NewCode (first time), Found (a backup exists) or Active (signing in again). */
    suspend fun signIn(email: String, password: String) {
        config() ?: throw BackupError(BackupError.Reason.NOT_CONFIGURED)
        val s = mapped { sb().signIn(email, password) }
        val sameAccount = p.getString("user", null) == s.userId
        secure.put("bk_refresh", s.refreshToken)
        p.edit().putString("user", s.userId).putString("email", email.trim()).putBoolean("needs_login", false).apply()
        if (sameAccount && p.getBoolean("confirmed", false) && secure.get("bk_key") != null) { refreshState(); return }
        forgetCode()
        val existing = mapped { sb().download(s) }
        if (existing != null) {
            val header = runCatching { BackupCrypto.readHeader(existing) }.getOrNull()
            if (header != null) {
                foundFile().writeBytes(existing)
                p.edit().putLong("found_at", header.createdAt).apply()
                _state.value = BackupState.Found(header.createdAt, existing.size)
                return
            }
        }
        startNew()
    }

    /** Makes a new recovery code (replacing any old backup at the next upload). */
    suspend fun startNew() {
        foundFile().delete()
        val code = newCode()
        val salt = BackupCrypto.newSalt()
        val key = withContext(Dispatchers.Default) { BackupCrypto.deriveKey(code, salt) }
        secure.put("bk_code", code)
        secure.put("bk_salt", b64(salt)); secure.put("bk_key", b64(key))
        p.edit().putInt("iterations", BackupCrypto.ITERATIONS).putBoolean("confirmed", false).apply()
        _state.value = BackupState.NewCode(code)
    }

    /** "I wrote it down": backups start. */
    suspend fun confirmCode() {
        p.edit().putBoolean("confirmed", true).apply()
        refreshState()
        BackupWorker.schedule(ctx, wifiOnly())
        runCatching { backupNow(force = true) }
    }

    fun setWifiOnly(on: Boolean) {
        p.edit().putBoolean("wifi_only", on).apply()
        BackupWorker.schedule(ctx, on, ExistingPeriodicWorkPolicy.UPDATE)
        refreshState()
    }

    private fun wifiOnly() = p.getBoolean("wifi_only", false)

    fun signOut() {
        val config = p.getString("config", null)
        listOf("bk_refresh", "bk_code", "bk_salt", "bk_key").forEach(secure::remove)
        p.edit().clear().putString("config", config).apply()
        foundFile().delete()
        BackupWorker.cancel(ctx)
        refreshState()
    }

    private fun forgetCode() {
        listOf("bk_code", "bk_salt", "bk_key").forEach { secure.remove(it) }
        p.edit().putBoolean("confirmed", false).remove("last_hash").apply()
    }

    // ---------- backing up ----------

    /** Uploads a new backup. Skipped when nothing changed since the last one, unless [force]. Returns true if uploaded. */
    open suspend fun backupNow(force: Boolean = false): Boolean {
        if (!p.getBoolean("confirmed", false)) return false
        val key = secure.get("bk_key")?.let(::unb64) ?: throw BackupError(BackupError.Reason.SIGNED_OUT)
        val salt = secure.get("bk_salt")?.let(::unb64) ?: throw BackupError(BackupError.Reason.SIGNED_OUT)
        val plain = withContext(Dispatchers.IO) { payload() }
        val hash = b64(MessageDigest.getInstance("SHA-256").digest(plain))
        if (!force && hash == p.getString("last_hash", null)) return false
        val file = withContext(Dispatchers.Default) {
            BackupCrypto.encrypt(plain, key, salt, schema(), System.currentTimeMillis(), p.getInt("iterations", BackupCrypto.ITERATIONS))
        }
        if (file.size > MAX_BYTES) throw BackupError(BackupError.Reason.TOO_BIG)
        val s = session()
        mapped { sb().upload(s, file) }
        p.edit().putLong("last", System.currentTimeMillis()).putInt("last_size", file.size).putString("last_hash", hash)
            .putBoolean("needs_login", false).apply()
        refreshState()
        return true
    }

    private fun schema(): Int = db().openHelper.readableDatabase.version

    /** gzip( settings length | settings JSON | database file ). */
    internal fun payload(): ByteArray {
        val s = prefs.settings.value
        val settings = json.encodeToString(BackupSettings.serializer(), BackupSettings(s.stream, s.grade, s.language, s.examDate)).toByteArray()
        val dbBytes = snapshot()
        return ByteArrayOutputStream().also { b ->
            DataOutputStream(GZIPOutputStream(b)).use { out -> out.writeInt(settings.size); out.write(settings); out.write(dbBytes) }
        }.toByteArray()
    }

    /**
     * A consistent copy of the live database. Room uses WAL: while we hold the write lock nothing can
     * commit or checkpoint, so the main file + WAL copied together are one consistent state. The copy is
     * then folded into a single file and integrity-checked before it is used.
     */
    private fun snapshot(): ByteArray {
        val live = db().openHelper.writableDatabase
        live.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val dir = File(ctx.cacheDir, "backup-snapshot").apply { deleteRecursively(); mkdirs() }
        val src = ctx.getDatabasePath(DB_NAME)
        val copy = File(dir, DB_NAME)
        live.beginTransaction()
        try {
            src.copyTo(copy, overwrite = true)
            File(src.path + "-wal").takeIf { it.exists() && it.length() > 0 }?.copyTo(File(copy.path + "-wal"), overwrite = true)
        } finally {
            live.endTransaction()
        }
        try {
            SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE).use { d ->
                d.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                d.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
                d.rawQuery("PRAGMA quick_check", null).use { check(it.moveToFirst() && it.getString(0) == "ok") { "snapshot failed its integrity check" } }
            }
            return copy.readBytes()
        } finally {
            dir.deleteRecursively()
        }
    }

    // ---------- restoring ----------

    /**
     * Opens the backup found at sign-in with the typed recovery code and stages it. The app then has to
     * close and reopen: [applyPendingRestore] swaps the database in before Room opens it.
     */
    suspend fun restore(typedCode: String) {
        val code = BackupCrypto.normalize(typedCode) ?: throw BackupError(BackupError.Reason.BAD_CODE)
        val file = foundFile().takeIf { it.exists() }?.readBytes()
            ?: mapped { sb().download(session()) } ?: throw BackupError(BackupError.Reason.NO_BACKUP)
        val header = try { BackupCrypto.readHeader(file) } catch (e: BackupCrypto.NotABackupException) { throw BackupError(BackupError.Reason.DAMAGED) }
        if (header.schema > withContext(Dispatchers.IO) { schema() }) throw BackupError(BackupError.Reason.NEWER_APP)
        val key = withContext(Dispatchers.Default) { BackupCrypto.deriveKey(code, header.salt, header.iterations) }
        val plain = try { BackupCrypto.decrypt(file, key) } catch (e: BackupCrypto.WrongCodeException) { throw BackupError(BackupError.Reason.WRONG_CODE) }
        val (settings, dbBytes) = try { unpack(plain) } catch (e: Exception) { throw BackupError(BackupError.Reason.DAMAGED) }
        withContext(Dispatchers.IO) {
            val dir = pendingDir().apply { deleteRecursively(); mkdirs() }
            File(dir, "settings.json").writeText(json.encodeToString(BackupSettings.serializer(), settings))
            File(dir, "$DB_NAME.part").writeBytes(dbBytes)
            File(dir, "$DB_NAME.part").renameTo(File(dir, DB_NAME))      // the swap only happens for a complete file
        }
        // Keep backing up with the same code, so the written-down code stays valid.
        secure.put("bk_code", code); secure.put("bk_salt", b64(header.salt)); secure.put("bk_key", b64(key))
        p.edit().putInt("iterations", header.iterations).putBoolean("confirmed", true).putLong("last", header.createdAt)
            .putInt("last_size", file.size).remove("last_hash").apply()
        foundFile().delete()
        BackupWorker.schedule(ctx, wifiOnly())
        refreshState()
    }

    private fun unpack(plain: ByteArray): Pair<BackupSettings, ByteArray> =
        DataInputStream(GZIPInputStream(ByteArrayInputStream(plain))).use { input ->
            val n = input.readInt()
            require(n in 2..10_000)
            val settings = json.decodeFromString(BackupSettings.serializer(), String(ByteArray(n).also(input::readFully)))
            val dbBytes = input.readBytes()
            require(dbBytes.size > 100 && String(dbBytes, 0, 15) == "SQLite format 3")
            settings to dbBytes
        }

    private fun foundFile() = File(ctx.cacheDir, "found.backup")
    private fun pendingDir() = File(ctx.filesDir, "restore")

    companion object {
        const val DB_NAME = "tibeb.db"
        const val MAX_BYTES = 45 * 1024 * 1024      // under the bucket's 50 MB limit

        /** Called at startup before the database opens: puts a restored database in place. */
        fun applyPendingRestore(ctx: Context, prefs: Prefs) {
            val dir = File(ctx.filesDir, "restore")
            val restored = File(dir, DB_NAME)
            if (!restored.exists()) { dir.deleteRecursively(); return }
            val target = ctx.getDatabasePath(DB_NAME)
            target.parentFile?.mkdirs()
            listOf("", "-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
            restored.copyTo(target, overwrite = true)
            runCatching { Json.decodeFromString(BackupSettings.serializer(), File(dir, "settings.json").readText()) }.getOrNull()?.let { b ->
                prefs.save(prefs.settings.value.copy(stream = b.stream, grade = b.grade, language = b.language, examDate = b.examDate, onboarded = true))
            }
            dir.deleteRecursively()
        }

        private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
        private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
    }
}

/** Daily backup while online (optionally Wi-Fi only), skipped when nothing changed. */
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val backup = (applicationContext as TibebApp).backup
        return try {
            backup.config()
            backup.backupNow()
            Result.success()
        } catch (e: BackupError) {
            if (e.reason == BackupError.Reason.OFFLINE || e.reason == BackupError.Reason.SERVER) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val NAME = "backup"

        fun schedule(ctx: Context, wifiOnly: Boolean, policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP) = runCatching {
            val c = Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, policy,
                PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).setConstraints(c).build())
        }

        fun cancel(ctx: Context) = runCatching { WorkManager.getInstance(ctx).cancelUniqueWork(NAME) }
    }
}
