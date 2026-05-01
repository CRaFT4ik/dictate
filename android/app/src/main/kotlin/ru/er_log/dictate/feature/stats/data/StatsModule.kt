package ru.er_log.dictate.feature.stats.data

import org.koin.dsl.module
import ru.er_log.dictate.core.db.RecognitionEventDao
import ru.er_log.dictate.feature.stats.domain.GetWordsForPeriodUseCase
import ru.er_log.dictate.feature.stats.domain.StatsRepository

public val statsModule = module {
    single { RecognitionLogger(get<RecognitionEventDao>()) }
    single<StatsRepository> { StatsRepositoryImpl(get()) }
    factory { GetWordsForPeriodUseCase(get()) }
}
