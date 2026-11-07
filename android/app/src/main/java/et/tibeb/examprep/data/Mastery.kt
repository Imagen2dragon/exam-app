package et.tibeb.examprep.data

/** Topic mastery levels for the map. Pure logic, unit-tested. */
object Mastery {
    enum class Level { NOT_STARTED, WEAK, DEVELOPING, STRONG }

    /** Needs at least this many answered questions on a topic before it can count as "strong". */
    const val MIN_FOR_STRONG = 3

    fun level(attempted: Int, accuracy: Double): Level = when {
        attempted == 0 -> Level.NOT_STARTED
        accuracy < 0.5 -> Level.WEAK
        accuracy >= 0.75 && attempted >= MIN_FOR_STRONG -> Level.STRONG
        else -> Level.DEVELOPING
    }

    data class Tile(val subject: String, val topic: String, val attempted: Int, val accuracy: Double) {
        val level get() = level(attempted, accuracy)
    }

    /**
     * Merges topics found in uploaded books (not practised yet) with exam results.
     * Topic names are matched case-insensitively so "Kinematics" and "kinematics" are one tile.
     */
    fun tiles(bookTopics: List<Pair<String, String>>, stats: List<TopicStat>): List<Tile> {
        val bySubjectTopic = LinkedHashMap<Pair<String, String>, Tile>()
        stats.forEach { s ->
            val key = s.subject to s.topic.trim().lowercase()
            val prev = bySubjectTopic[key]
            bySubjectTopic[key] = if (prev == null) Tile(s.subject, s.topic.trim(), s.attempted, s.accuracy)
            else {
                val attempted = prev.attempted + s.attempted
                Tile(s.subject, prev.topic, attempted, (prev.accuracy * prev.attempted + s.accuracy * s.attempted) / attempted)
            }
        }
        bookTopics.forEach { (subject, topic) ->
            val key = subject to topic.trim().lowercase()
            if (topic.isNotBlank() && key !in bySubjectTopic) bySubjectTopic[key] = Tile(subject, topic.trim(), 0, 0.0)
        }
        return bySubjectTopic.values.sortedWith(compareBy({ it.subject }, { it.level.ordinal }, { it.topic.lowercase() }))
    }

    /** Share of a subject's topics that are strong, 0..100. */
    fun subjectPercent(tiles: List<Tile>): Int =
        if (tiles.isEmpty()) 0 else tiles.count { it.level == Level.STRONG } * 100 / tiles.size
}
