package et.tibeb.examprep.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.icu.util.ULocale
import java.util.Date
import java.util.Locale

/**
 * In-app interface language: "system" (follow the phone, incl. Android 13+ per-app language
 * settings), "en" or "am". Works from Android 7 with no AppCompat dependency: the chosen locale
 * is applied to the Activity's base context, and to the app context for background messages.
 */
object Lang {
    const val KEY = "ui_lang"
    val OPTIONS = listOf("system", "en", "am")

    fun saved(ctx: Context): String =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString(KEY, "system") ?: "system"

    fun wrap(base: Context, tag: String = saved(base)): Context {
        if (tag == "system") return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) }
        return base.createConfigurationContext(config)
    }

    fun isAmharic(ctx: Context) = ctx.resources.configuration.locales[0].language == "am"
}

/** Dates in the Ethiopian calendar when the interface is Amharic (e.g. "28 ጥቅምት 2019 ዓ.ም."). */
fun Context.formatDate(millis: Long): String =
    if (Lang.isAmharic(this))
        // ICU has no Amharic era name (it prints "ERA1"), so use our own pattern + ዓ.ም. (ዓመተ ምሕረት)
        android.icu.text.SimpleDateFormat("d MMMM y", ULocale("am_ET@calendar=ethiopic")).format(Date(millis)) + " ዓ.ም."
    else
        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, resources.configuration.locales[0]).format(Date(millis))

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
