package ru.neosvet.vestnewage

import android.app.Application
import android.content.Context
import android.os.Build
import ru.neosvet.vestnewage.network.OnlineObserver
import ru.neosvet.vestnewage.utils.Files

class App : Application() {
    companion object {
        private const val UNSAFE_FILE = "unsafe"
        var unsafeClient = false
        lateinit var context: Context
        val version: Int
            get() = context.packageManager.getPackageInfo(context.packageName, 0).versionCode

        fun needUnsafeClient() {
            unsafeClient = true
            val file = Files.slash(UNSAFE_FILE)
            if (!file.exists()) file.createNewFile()
        }
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        OnlineObserver.init(context)
        unsafeClient = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        if (!unsafeClient) {
            val file = Files.slash(UNSAFE_FILE)
            unsafeClient = file.exists()
        }
    }

    override fun onTerminate() {
        OnlineObserver.onInactive()
        super.onTerminate()
    }
}