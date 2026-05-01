package ru.er_log.dictate.feature.stats.domain

import kotlinx.coroutines.flow.Flow

/**
 * Use case that returns a reactive word-count stream for the given [Period].
 *
 * Delegates to [StatsRepository] without additional transformation; the repository is responsible
 * for coalescing null DAO values to `0L`.
 *
 * @property repo The stats repository providing the underlying [Flow].
 */
public class GetWordsForPeriodUseCase(private val repo: StatsRepository) {

    /**
     * Invokes the use case for [period] and returns a [Flow] emitting total word counts.
     *
     * The flow remains active and re-emits as new recognition events are inserted.
     */
    public operator fun invoke(period: Period): Flow<Long> = repo.wordsForPeriod(period)
}
