package et.tibeb.examprep

import et.tibeb.examprep.data.Mastery
import et.tibeb.examprep.data.Mastery.Level
import et.tibeb.examprep.data.TopicStat
import org.junit.Assert.assertEquals
import org.junit.Test

class MasteryTest {
    @Test fun levels() {
        assertEquals(Level.NOT_STARTED, Mastery.level(0, 0.0))
        assertEquals(Level.WEAK, Mastery.level(4, 0.49))
        assertEquals(Level.DEVELOPING, Mastery.level(4, 0.6))
        assertEquals(Level.STRONG, Mastery.level(3, 0.75))
        assertEquals("one lucky answer is not mastery", Level.DEVELOPING, Mastery.level(1, 1.0))
    }

    @Test fun mergesBooksAndResultsWithoutDuplicates() {
        val stats = listOf(
            TopicStat("Physics", "Kinematics", attempted = 2, earned = 2.0, possible = 2.0),
            TopicStat("Physics", "kinematics ", attempted = 2, earned = 0.0, possible = 2.0),   // same topic, other casing
            TopicStat("Physics", "Waves", attempted = 4, earned = 4.0, possible = 4.0),
        )
        val books = listOf("Physics" to "Waves", "Physics" to "Optics", "Physics" to "")
        val tiles = Mastery.tiles(books, stats)
        assertEquals(listOf("Optics", "Kinematics", "Waves"), tiles.map { it.topic })   // not started, then by level
        val kin = tiles.first { it.topic == "Kinematics" }
        assertEquals(4, kin.attempted); assertEquals(0.5, kin.accuracy, 1e-9)
        assertEquals(33, Mastery.subjectPercent(tiles))
    }
}
