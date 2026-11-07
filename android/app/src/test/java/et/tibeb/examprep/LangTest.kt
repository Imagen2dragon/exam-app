package et.tibeb.examprep

import androidx.test.core.app.ApplicationProvider
import et.tibeb.examprep.util.Lang
import et.tibeb.examprep.util.formatDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LangTest {
    private val app = ApplicationProvider.getApplicationContext<TibebApp>()
    // 7 Nov 2026 (Gregorian) = 28 Tikimt 2019 in the Ethiopian calendar
    private val nov7 = Calendar.getInstance(TimeZone.getDefault()).apply { set(2026, Calendar.NOVEMBER, 7, 12, 0) }.timeInMillis

    @Test fun amharicUsesEthiopianCalendar() {
        val text = Lang.wrap(app, "am").formatDate(nov7)
        assertTrue(text, text.contains("2019") && text.contains("28"))
        assertEquals("28 ጥቅምት 2019 ዓ.ም.", text) // Tikimt, Ethiopian era
    }

    @Test fun englishKeepsGregorian() {
        assertTrue(Lang.wrap(app, "en").formatDate(nov7).contains("2026"))
    }

    @Test fun backgroundErrorsFollowTheChosenLanguage() {
        app.getSharedPreferences("settings", 0).edit().putString(Lang.KEY, "am").commit()
        assertEquals("የሆነ ችግር ተፈጥሯል", app.str(R.string.err_generic))
        assertEquals("ጥያቄ 3 ከ10", app.str(R.string.ex_q_of, 3, 10))
        app.getSharedPreferences("settings", 0).edit().putString(Lang.KEY, "en").commit()
        assertEquals("Question 3 of 10", app.str(R.string.ex_q_of, 3, 10))
    }
}
