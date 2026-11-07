package et.tibeb.examprep

import et.tibeb.examprep.util.Updates.Companion.isNewer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun comparesNumericallyNotAlphabetically() {
        assertTrue(isNewer("0.10.0", "0.9.0"))
        assertTrue(isNewer("1.0.0", "0.99.99"))
        assertTrue(isNewer("0.3.1", "0.3.0"))
    }

    @Test fun sameOrOlderIsNotAnUpdate() {
        assertFalse(isNewer("0.3.0", "0.3.0"))
        assertFalse(isNewer("0.2.9", "0.3.0"))
    }

    @Test fun garbageNeverTriggersAnUpdate() {
        assertFalse(isNewer("latest", "0.3.0"))
        assertFalse(isNewer("", "0.3.0"))
    }
}
