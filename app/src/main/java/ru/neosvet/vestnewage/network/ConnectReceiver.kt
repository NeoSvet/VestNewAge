package ru.neosvet.vestnewage.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager

class ConnectReceiver : BroadcastReceiver() {
    companion object {
        var connected = false
            private set
        var observer: ConnectObserver? = null

        fun subscribe(observer: ConnectObserver) {
            ConnectReceiver.observer = observer
        }

        fun unSubscribe() {
            observer = null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        connected = !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)
        observer?.connectChanged(connected)
    }
}

interface ConnectObserver {
    fun connectChanged(connected: Boolean)
}