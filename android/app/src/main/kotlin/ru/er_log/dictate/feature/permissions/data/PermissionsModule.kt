package ru.er_log.dictate.feature.permissions.data

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import ru.er_log.dictate.feature.permissions.domain.PermissionsRepository

public val permissionsModule = module {
    single<PermissionsRepository> { PermissionsRepositoryImpl(androidContext()) }
}
