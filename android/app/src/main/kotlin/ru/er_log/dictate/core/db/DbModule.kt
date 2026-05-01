package ru.er_log.dictate.core.db

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module that provides the [DictateDatabase] singleton and the [RecognitionEventDao].
 */
public val dbModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            DictateDatabase::class.java,
            "dictate.db",
        ).build()
    }

    single { get<DictateDatabase>().recognitionEventDao() }
}
