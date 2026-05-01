package ru.er_log.dictate.core.accessibility

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

public val accessibilityModule = module {
    single<PasteController> { PasteControllerImpl(androidContext()) }
}
