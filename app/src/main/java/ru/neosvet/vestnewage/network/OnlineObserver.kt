package ru.neosvet.vestnewage.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object OnlineObserver {
    private const val INTERVAL = 30000
    private var timeMessage = 0L
    private var lastValue = false
    private val _isOnline = MutableStateFlow(lastValue)
    val isOnline: StateFlow<Boolean> = _isOnline

    private val availableNetworks = mutableSetOf<Int>()
    private var manager: ConnectivityManager? = null
    private val request: NetworkRequest = NetworkRequest.Builder().build()
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            availableNetworks.remove(network.hashCode())
            update(availableNetworks.isNotEmpty())
        }

        override fun onAvailable(network: Network) {
            availableNetworks.add(network.hashCode())
            update(availableNetworks.isNotEmpty())
        }
    }

    fun init(context: Context) {
        manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager?.activeNetwork?.let {
            availableNetworks.add(it.hashCode())
            update(true)
        }
        onActive()
    }

    fun onActive() {
        manager?.registerNetworkCallback(request, callback)
    }

    fun onInactive() {
        manager?.unregisterNetworkCallback(callback)
    }

    private fun update(online: Boolean) {
        if (online != lastValue) {
            lastValue = online
            _isOnline.tryEmit(online)
        }
    }

    fun needShowMessage(): Boolean {
        val now = System.currentTimeMillis()
        if (now - timeMessage < INTERVAL) return false
        timeMessage = now
        return true
    }
}