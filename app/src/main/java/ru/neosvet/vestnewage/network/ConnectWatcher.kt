package ru.neosvet.vestnewage.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest

object ConnectWatcher : ConnectivityManager.NetworkCallback() {
    var connected = false
        private set
    var observer: ConnectObserver? = null
    private var isRun = false
    private const val INTERVAL = 30000
    private var timeMessage = 0L

    fun start(context: Context) {
        if (isRun) return
        isRun = true
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder().build()
        manager.registerNetworkCallback(request, this)
        connected = manager.activeNetwork != null
    }

    fun stop(context: Context) {
        if (isRun.not()) return
        isRun = false
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.unregisterNetworkCallback(this)
    }

    fun subscribe(observer: ConnectObserver) {
        this.observer = observer
    }

    fun unSubscribe() {
        observer = null
    }

    override fun onAvailable(network: Network) {
        connected = true
        observer?.connectChanged(connected)
    }

    override fun onUnavailable() {
        connected = false
        observer?.connectChanged(connected)
    }

    override fun onLost(network: Network) {
        connected = false
        observer?.connectChanged(connected)
    }

    fun needShowMessage(): Boolean {
        val now = System.currentTimeMillis()
        if (now - timeMessage < INTERVAL) return false
        timeMessage = now
        return true
    }
}

interface ConnectObserver {
    fun connectChanged(connected: Boolean)
}