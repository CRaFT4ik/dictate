package ru.er_log.dictate.feature.settings

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.er_log.dictate.feature.settings.data.SettingsRepositoryImpl
import ru.er_log.dictate.feature.settings.domain.SettingsRepository
import ru.er_log.dictate.feature.settings.ui.SettingsViewModel

public val settingsModule = module {
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    viewModel { SettingsViewModel(get()) }
}
