package ru.neosvet.vestnewage.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import ru.neosvet.vestnewage.R
import ru.neosvet.vestnewage.utils.Lib

object ConnectWatcher : ConnectivityManager.NetworkCallback() {
    var connected = false
        private set
    var observer: ConnectObserver? = null
    private var isRun = false
    private const val INTERVAL = 30000
    private var message = ""
    private var timeMessage = 0L

    fun start(context: Context) {
        if (isRun) return
        isRun = true
        if (message.isEmpty())
            message = context.getString(R.string.no_connected)
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder().build()
        connectivityManager.registerNetworkCallback(request, this)
    }

    fun stop(context: Context) {
        if (isRun.not()) return
        isRun = false
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(this)
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

    fun showMessage() {
        val now = System.currentTimeMillis()
        if(now - timeMessage < INTERVAL) return
        Lib.showToast(message)
        timeMessage = now
    }
}

interface ConnectObserver {
    fun connectChanged(connected: Boolean)
}