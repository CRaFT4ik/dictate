package ru.er_log.dictate.core.prefs

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Koin module that provides [SettingsStore] and [OverlayPositionStore] as singletons. */
public val prefsModule = module {
    single { SettingsStore(androidContext()) }
    single { OverlayPositionStore(androidContext()) }
}
