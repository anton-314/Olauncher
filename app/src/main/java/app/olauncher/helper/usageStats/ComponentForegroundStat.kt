package app.olauncher.helper.usageStats

import java.util.Date

/**
 * Object representing a timespan that an application was in the foreground.
 *
 * @property beginTime The start of the timespan, in milliseconds since the epoch.
 * @property endTime The end of the timespan, in milliseconds since the epoch.
 * @property packageName The package name of the application.
 */
data class ComponentForegroundStat(
    val beginTime: Long,
    val endTime: Long,
    val packageName: String
) {
    /**
     * Overriding toString for a more readable log output, similar to the original Java class.
     * The default data class toString() would also work, but this one is more explicit
     * about formatting the timestamps as Instants.
     */
    override fun toString(): String {
        return "ComponentForegroundStat(" +
                "beginTime=${Date(beginTime)}, " +
                "endTime=${Date(endTime)}, " +
                "packageName='$packageName')"
    }
}

/**
 * Merges overlapping or adjacent time intervals to prevent double-counting
 * when multiple components of the same package have overlapping foreground times.
 * Empty and reversed intervals are dropped: swapped events in the system log
 * can produce an end before the begin, which would otherwise subtract time.
 */
internal fun mergeOverlappingIntervals(stats: List<ComponentForegroundStat>): List<ComponentForegroundStat> {
    val sorted = stats.filter { it.endTime > it.beginTime }.sortedBy { it.beginTime }
    if (sorted.size <= 1) return sorted

    val merged = mutableListOf<ComponentForegroundStat>()
    var current = sorted[0]

    for (i in 1 until sorted.size) {
        val next = sorted[i]
        if (next.beginTime <= current.endTime) {
            current = ComponentForegroundStat(
                current.beginTime,
                maxOf(current.endTime, next.endTime),
                current.packageName
            )
        } else {
            merged.add(current)
            current = next
        }
    }
    merged.add(current)
    return merged
}

/** Total time covered by [stats], counting overlapping periods once. */
internal fun totalForegroundTime(stats: List<ComponentForegroundStat>): Long =
    mergeOverlappingIntervals(stats).sumOf { it.endTime - it.beginTime }
