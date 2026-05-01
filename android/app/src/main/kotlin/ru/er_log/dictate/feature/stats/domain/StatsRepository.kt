package ru.er_log.dictate.feature.stats.domain

import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer contract for querying aggregated word counts from persisted recognition events.
 *
 * Implementations live in the `data` layer and must not be imported by the UI or domain directly.
 */
public interface StatsRepository {

    /**
     * Returns a [Flow] that emits the total number of recognised words within the given [period].
     *
     * The flow re-emits whenever the underlying data changes (e.g., after a new dictation session).
     * Never emits negative values; emits `0L` when there are no events for the period.
     */
    public fun wordsForPeriod(period: Period): Flow<Long>
}
