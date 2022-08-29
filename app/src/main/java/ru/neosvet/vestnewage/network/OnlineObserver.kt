package ru.neosvet.vestnewage.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object OnlineObserver {
    private const val INTERVAL = 15000
    private var timeMessage = 0L
    private var lastValue = true
    private val mIsOnline = MutableStateFlow(lastValue)
    val isOnline: StateFlow<Boolean> = mIsOnline

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
            update(true)
        }
    }

    fun init(context: Context) {
        manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        update(manager?.activeNetwork?.let {
            availableNetworks.add(it.hashCode())
            true
        } ?: false)
        onActive()
    }

    fun onActive() {
        manager?.registerNetworkCallback(request, callback)
    }

    fun onInactive() {
        manager?.unregisterNetworkCallback(callback)
    }

    private fun update(online: Boolean) {
        if (online != lastValue) return
        lastValue = online
        mIsOnline.tryEmit(online)
    }

    fun needShowMessage(): Boolean {
        val now = System.currentTimeMillis()
        if (now - timeMessage < INTERVAL) return false
        timeMessage = now
        return true
    }
}