package ru.neosvet.vestnewage

import android.app.Application
import android.content.Context
import ru.neosvet.vestnewage.network.OnlineObserver

class App : Application() {
    companion object {
        lateinit var context: Context
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        OnlineObserver.init(context)
    }

    override fun onTerminate() {
        OnlineObserver.onInactive()
        super.onTerminate()
    }
}