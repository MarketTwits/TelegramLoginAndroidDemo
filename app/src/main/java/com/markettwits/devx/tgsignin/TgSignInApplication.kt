package com.markettwits.devx.tgsignin

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import com.markettwits.devx.tgsignin.di.appModule

class TgSignInApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TgSignInApplication)
            modules(appModule)
        }
    }
}
