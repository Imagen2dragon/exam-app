package et.tibeb.admin

import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64

/**
 * Remembers the server address and the admin key. On Windows the key is encrypted with DPAPI, so
 * only this Windows account on this PC can decrypt it; a copied config file is useless elsewhere.
 */
class Store(private val dir: File = defaultDir()) {
    @Serializable private data class Config(val serverUrl: String = "", val key: String = "", val protected: Boolean = false)

    private val file get() = File(dir, "config.json")
    private val windows = System.getProperty("os.name").lowercase().contains("win")

    fun load(): Pair<String, String>? = runCatching {
        val c = json.decodeFromString<Config>(file.readText())
        if (c.serverUrl.isBlank() || c.key.isBlank()) return null
        val raw = Base64.getDecoder().decode(c.key)
        val key = if (c.protected) String(Crypt32Util.cryptUnprotectData(raw)) else String(raw)
        c.serverUrl to key
    }.getOrNull()

    fun save(serverUrl: String, key: String) {
        dir.mkdirs()
        val bytes = key.toByteArray()
        val stored = if (windows) Crypt32Util.cryptProtectData(bytes) else bytes
        file.writeText(json.encodeToString(Config.serializer(), Config(serverUrl, Base64.getEncoder().encodeToString(stored), windows)))
        if (!windows) runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
    }

    fun forget() { file.delete() }

    companion object {
        fun defaultDir(): File {
            val appData = System.getenv("APPDATA")
            return if (appData != null) File(appData, "TibebAdmin") else File(System.getProperty("user.home"), ".config/tibeb-admin")
        }
    }
}
