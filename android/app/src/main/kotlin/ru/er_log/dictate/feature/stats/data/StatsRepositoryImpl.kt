package ru.er_log.dictate.feature.stats.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.er_log.dictate.core.db.RecognitionEventDao
import ru.er_log.dictate.feature.stats.domain.Period
import ru.er_log.dictate.feature.stats.domain.StatsRepository

/**
 * Room-backed implementation of [StatsRepository].
 *
 * Computes the UTC millisecond range for the requested [Period] at subscription time and
 * delegates the live aggregation query to [RecognitionEventDao.wordsBetween]. The DAO returns
 * `null` when no events exist in the range; this implementation coalesces `null` to `0L`.
 *
 * @property dao The Room DAO used for all database reads.
 */
public class StatsRepositoryImpl(private val dao: RecognitionEventDao) : StatsRepository {

    override fun wordsForPeriod(period: Period): Flow<Long> {
        val (from, to) = period.range()
        return dao.wordsBetween(from, to).map { it ?: 0L }
    }
}
