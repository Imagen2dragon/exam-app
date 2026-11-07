package et.tibeb.examprep.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Serializable data class BackupConfig(val url: String, val key: String, val bucket: String = "backups")
@Serializable data class ConfigRes(val backup: BackupConfig? = null)

data class Session(val accessToken: String, val refreshToken: String, val userId: String)

/** Why a Supabase call failed, so the UI can say something useful. */
class BackupException(val kind: Kind, message: String = kind.name) : Exception(message) {
    enum class Kind { BAD_LOGIN, SIGNED_OUT, OFFLINE, TOO_BIG, SERVER }
    val retryable get() = kind == Kind.OFFLINE || kind == Kind.SERVER
}

/**
 * Just the four Supabase calls the backup needs (password sign-in, token refresh, upload, download),
 * on the platform HttpURLConnection: no Supabase SDK, so the APK stays small. Only the PUBLIC key is
 * used; Storage rules let this user reach nothing but `<bucket>/<their id>/`.
 */
open class Supabase(private val cfg: BackupConfig, private val allowHttpForTests: Boolean = false) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class User(val id: String)
    @Serializable private data class TokenRes(val access_token: String, val refresh_token: String, val user: User)

    init {
        require(cfg.url.startsWith("https://") || (allowHttpForTests && cfg.url.startsWith("http://127.0.0.1"))) { "Supabase must use https" }
    }

    open suspend fun signIn(email: String, password: String): Session =
        token("password", buildJsonObject { put("email", email.trim()); put("password", password) }.toString())

    open suspend fun refresh(refreshToken: String): Session =
        token("refresh_token", buildJsonObject { put("refresh_token", refreshToken) }.toString())

    private suspend fun token(grant: String, body: String): Session = withContext(Dispatchers.IO) {
        val (code, text) = call("POST", "/auth/v1/token?grant_type=$grant", null, body.toByteArray(), "application/json")
        when {
            code in 200..299 -> json.decodeFromString<TokenRes>(String(text)).let { Session(it.access_token, it.refresh_token, it.user.id) }
            code == 400 || code == 401 -> throw BackupException(if (grant == "password") BackupException.Kind.BAD_LOGIN else BackupException.Kind.SIGNED_OUT)
            else -> throw BackupException(BackupException.Kind.SERVER, "auth HTTP $code")
        }
    }

    private fun path(s: Session) = "/storage/v1/object/${cfg.bucket}/${s.userId}/tibeb.backup"

    open suspend fun upload(s: Session, file: ByteArray): Unit = withContext(Dispatchers.IO) {
        val (code, _) = call("POST", path(s), s.accessToken, file, "application/octet-stream", upsert = true)
        when {
            code in 200..299 -> Unit
            code == 401 || code == 403 -> throw BackupException(BackupException.Kind.SIGNED_OUT)
            code == 413 -> throw BackupException(BackupException.Kind.TOO_BIG)
            else -> throw BackupException(BackupException.Kind.SERVER, "upload HTTP $code")
        }
    }

    /** The backup file, or null if this account has none yet. */
    open suspend fun download(s: Session): ByteArray? = withContext(Dispatchers.IO) {
        val (code, body) = call("GET", path(s), s.accessToken, null, null)
        val text = if (code in 200..299) "" else String(body)
        when {
            code in 200..299 -> body
            // Storage answers a missing object with 404, or 400 carrying "not_found" / "Object not found".
            code == 404 || (code == 400 && ("not_found" in text || "not found" in text.lowercase())) -> null
            code == 401 || code == 403 -> throw BackupException(BackupException.Kind.SIGNED_OUT)
            else -> throw BackupException(BackupException.Kind.SERVER, "download HTTP $code")
        }
    }

    private fun call(method: String, path: String, bearer: String?, body: ByteArray?, type: String?, upsert: Boolean = false): Pair<Int, ByteArray> {
        val c = URL(cfg.url.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 20_000; c.readTimeout = 120_000
            c.setRequestProperty("apikey", cfg.key)
            bearer?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (upsert) c.setRequestProperty("x-upsert", "true")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", type)
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val bytes = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
            return code to bytes
        } catch (e: IOException) {
            throw BackupException(BackupException.Kind.OFFLINE, e.message ?: "offline")
        } finally {
            c.disconnect()
        }
    }
}
