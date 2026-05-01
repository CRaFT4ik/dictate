package ru.er_log.dictate.core.transcribe

import org.koin.dsl.module

public val transcribeModule = module {
    single<DictateRepository> { DictateRepositoryImpl(get()) }
}
