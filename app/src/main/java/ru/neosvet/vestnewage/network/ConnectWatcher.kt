package ru.neosvet.vestnewage.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest

object ConnectWatcher : ConnectivityManager.NetworkCallback() {
    var connected = false
        private set
    var observer: ConnectObserver? = null

    fun start(context: Context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder().build()
        connectivityManager.registerNetworkCallback(request, this)
    }

    fun stop(context: Context) {
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
}

interface ConnectObserver {
    fun connectChanged(connected: Boolean)
}