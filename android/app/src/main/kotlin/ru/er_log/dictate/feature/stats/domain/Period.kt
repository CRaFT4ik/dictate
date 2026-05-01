package ru.er_log.dictate.feature.stats.domain

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Represents a stats aggregation period with support for computing UTC millisecond boundaries.
 *
 * Each variant computes its [range] in the device's local timezone and converts the boundaries
 * to UTC epoch milliseconds. The range is **inclusive start, exclusive end**.
 */
public sealed class Period {

    /**
     * Returns a pair of UTC epoch milliseconds `(inclusiveStart, exclusiveEnd)` for this period,
     * relative to [now].
     */
    public abstract fun range(now: Instant = Clock.System.now()): Pair<Long, Long>

    /**
     * The current ISO week: Monday 00:00 (inclusive) → next Monday 00:00 (exclusive).
     *
     * Uses [kotlinx.datetime.isoDayNumber] (Monday=1, Sunday=7) to shift back to the
     * week's Monday boundary in the device's local timezone.
     */
    public data object Week : Period() {
        override fun range(now: Instant): Pair<Long, Long> {
            val tz = TimeZone.currentSystemDefault()
            val today = now.toLocalDateTime(tz).date
            // isoDayNumber: Mon=1, Tue=2, …, Sun=7; shift back to Monday of this week.
            val daysFromMonday = today.dayOfWeek.isoDayNumber - 1
            val monday = today.minus(daysFromMonday, DateTimeUnit.DAY)
            val nextMonday = monday.plus(1, DateTimeUnit.WEEK)
            return monday.atStartOfDayIn(tz).toEpochMilliseconds() to
                nextMonday.atStartOfDayIn(tz).toEpochMilliseconds()
        }
    }

    /**
     * The current calendar month: 1st at 00:00 (inclusive) → 1st of next month at 00:00 (exclusive).
     */
    public data object Month : Period() {
        override fun range(now: Instant): Pair<Long, Long> {
            val tz = TimeZone.currentSystemDefault()
            val today = now.toLocalDateTime(tz).date
            val firstOfMonth = today.minus(today.dayOfMonth - 1, DateTimeUnit.DAY)
            val firstOfNextMonth = firstOfMonth.plus(1, DateTimeUnit.MONTH)
            return firstOfMonth.atStartOfDayIn(tz).toEpochMilliseconds() to
                firstOfNextMonth.atStartOfDayIn(tz).toEpochMilliseconds()
        }
    }

    /**
     * The current calendar year: Jan 1 at 00:00 (inclusive) → Jan 1 of next year at 00:00 (exclusive).
     */
    public data object Year : Period() {
        override fun range(now: Instant): Pair<Long, Long> {
            val tz = TimeZone.currentSystemDefault()
            val today = now.toLocalDateTime(tz).date
            val jan1 = today.minus(today.dayOfYear - 1, DateTimeUnit.DAY)
            val jan1NextYear = jan1.plus(1, DateTimeUnit.YEAR)
            return jan1.atStartOfDayIn(tz).toEpochMilliseconds() to
                jan1NextYear.atStartOfDayIn(tz).toEpochMilliseconds()
        }
    }
}
