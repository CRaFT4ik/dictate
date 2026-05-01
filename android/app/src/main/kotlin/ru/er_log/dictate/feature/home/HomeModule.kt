package ru.er_log.dictate.feature.home

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.er_log.dictate.feature.home.data.OverlayRunningSource
import ru.er_log.dictate.feature.home.data.OverlayRunningSourceImpl
import ru.er_log.dictate.feature.home.ui.HomeViewModel

/** Koin module for the home feature. */
public val homeModule = module {
    single<OverlayRunningSource> { OverlayRunningSourceImpl() }
    viewModel { HomeViewModel(get(), get(), get()) }
}
