package et.tibeb.examprep.util

import android.content.Context
import et.tibeb.examprep.BuildConfig
import et.tibeb.examprep.net.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases at most once a day for a newer version. Sends no personal data: it is an
 * anonymous request for the latest release's version number. The download goes through the
 * browser, so the app needs no install permission.
 */
class Updates(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _available = MutableStateFlow(cachedNewer())
    val available: StateFlow<String?> = _available

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(v) { prefs.edit().putBoolean("enabled", v).apply(); if (!v) _available.value = null }

    suspend fun check(force: Boolean = false) {
        if (!enabled) return
        val last = prefs.getLong("last_check", 0)
        if (!force && System.currentTimeMillis() - last < DAY) return
        val latest = withContext(Dispatchers.IO) { runCatching { fetchLatest() }.getOrNull() } ?: return
        prefs.edit().putLong("last_check", System.currentTimeMillis()).putString("latest", latest).apply()
        _available.value = latest.takeIf { isNewer(it, BuildConfig.VERSION_NAME) }
    }

    private fun cachedNewer(): String? =
        prefs.getString("latest", null)?.takeIf { enabled && isNewer(it, BuildConfig.VERSION_NAME) }

    private fun fetchLatest(): String? {
        val c = URL(RELEASES_API).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return null
            val tag = json.parseToJsonElement(c.inputStream.bufferedReader().use { it.readText() })
                .jsonObject["tag_name"]?.jsonPrimitive?.content ?: return null
            tag.removePrefix("v").takeIf { VERSION.matches(it) }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val RELEASES_API = "https://api.github.com/repos/Imagen2dragon/exam-app/releases/latest"
        const val DOWNLOAD_URL = "https://github.com/Imagen2dragon/exam-app/releases/latest/download/tibeb.apk"
        private const val DAY = 24L * 60 * 60 * 1000
        private val VERSION = Regex("""\d+\.\d+\.\d+""")

        /** True if `latest` (e.g. "0.10.0") is a higher semantic version than `current` (e.g. "0.9.2"). */
        fun isNewer(latest: String, current: String): Boolean {
            val a = latest.split('.').map { it.toIntOrNull() ?: return false }
            val b = current.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
