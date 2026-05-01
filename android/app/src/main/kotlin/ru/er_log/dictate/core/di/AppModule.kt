package ru.er_log.dictate.core.di

import org.koin.dsl.module
import ru.er_log.dictate.core.accessibility.accessibilityModule
import ru.er_log.dictate.core.audio.audioModule
import ru.er_log.dictate.core.db.dbModule
import ru.er_log.dictate.core.network.networkModule
import ru.er_log.dictate.core.overlay.overlayModule
import ru.er_log.dictate.core.prefs.prefsModule
import ru.er_log.dictate.core.transcribe.transcribeModule
import ru.er_log.dictate.feature.permissions.data.permissionsModule
import ru.er_log.dictate.feature.home.homeModule
import ru.er_log.dictate.feature.settings.settingsModule
import ru.er_log.dictate.feature.stats.data.statsModule

val appModule = module {
    includes(
        networkModule,
        audioModule,
        dbModule,
        prefsModule,
        accessibilityModule,
        overlayModule,
        transcribeModule,
        statsModule,
        permissionsModule,
        settingsModule,
        homeModule,
    )
}
