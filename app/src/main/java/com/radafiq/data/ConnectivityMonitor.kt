package com.radafiq.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class ConnectivityMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isOnline: Flow<Boolean> = callbackFlow {
        /**
         * Tracks whether any callback has delivered a value yet.
         * If the callbacks fire synchronously during registration (most devices),
         * we skip the stale [activeNetwork] check to avoid racing with them.
         */
        var callbackHasFired = false

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // onAvailable alone doesn't guarantee internet (e.g. captive portal).
                // Wait for onCapabilitiesChanged to confirm validation.
                // Mark that we've heard from the callback system so the initial
                // activeNetwork check below knows it's already stale.
                callbackHasFired = true
            }

            override fun onLost(network: Network) {
                callbackHasFired = true
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                callbackHasFired = true
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(hasInternet)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)

        // Fallback initial check — only queried if the callbacks haven't already
        // delivered the real state (e.g. on slow OEMs where registration returns
        // without any synchronous callback).
        if (!callbackHasFired) {
            val currentNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(currentNetwork)
            val isConnected = caps?.let {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } ?: false
            trySend(isConnected)
        }

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
