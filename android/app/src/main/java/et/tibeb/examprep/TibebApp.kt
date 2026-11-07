package et.tibeb.examprep

import android.app.Application
import android.content.Context
import et.tibeb.examprep.data.AppDb
import et.tibeb.examprep.data.Repository
import et.tibeb.examprep.net.Api
import et.tibeb.examprep.security.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    val serverUrl: String = "",
    val stream: String = "natural",   // natural | social
    val grade: Int = 12,
    val language: String = "en",      // en | am
    val hasToken: Boolean = false,
    val onboarded: Boolean = false,
    val examDate: Long = 0,           // 0 = not set
)

class Prefs(ctx: Context, private val secure: SecureStore) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _s = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _s

    private fun load() = Settings(
        serverUrl = p.getString("url", "") ?: "",
        stream = p.getString("stream", "natural") ?: "natural",
        grade = p.getInt("grade", 12),
        language = p.getString("lang", "en") ?: "en",
        hasToken = !secure.get("token").isNullOrBlank(),
        onboarded = p.getBoolean("onboarded", false),
        examDate = p.getLong("exam_date", 0),
    )

    fun save(s: Settings) {
        p.edit().putString("url", s.serverUrl.trim()).putString("stream", s.stream).putInt("grade", s.grade)
            .putString("lang", s.language).putBoolean("onboarded", s.onboarded).putLong("exam_date", s.examDate).apply()
        _s.value = load()
    }

    fun setUiLang(tag: String) { p.edit().putString(et.tibeb.examprep.util.Lang.KEY, tag).commit() }

    fun setToken(token: String) { secure.put("token", token.trim()); _s.value = load() }
    fun token(): String? = secure.get("token")
}

/** Manual dependency container: no DI framework, fewer classes, faster cold start. */
class TibebApp : Application() {
    /** Localised string for messages created outside the UI (errors from background jobs). */
    fun str(id: Int, vararg args: Any): String = et.tibeb.examprep.util.Lang.wrap(this).getString(id, *args)

    lateinit var prefs: Prefs; private set
    lateinit var repo: Repository; private set
    lateinit var updates: et.tibeb.examprep.util.Updates; private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this, SecureStore(this))
        val api = Api(baseUrl = { prefs.settings.value.serverUrl }, token = { prefs.token() }, str = ::str)
        repo = Repository(this, AppDb.create(this), api, ::str)
        updates = et.tibeb.examprep.util.Updates(this)
    }
}
