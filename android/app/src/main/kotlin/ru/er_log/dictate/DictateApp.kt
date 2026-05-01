package ru.er_log.dictate

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import ru.er_log.dictate.core.di.appModule

class DictateApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DictateApp)
            modules(appModule)
        }
    }
}
