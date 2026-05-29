package eu.siacs.conversations.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Log
import eu.siacs.conversations.Config

class NetworkManager(private val context: Context) {

    fun getHint(): Hint {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            ?: return Hint.ACTIVE

        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val activeNetwork = connectivityManager.activeNetwork
                    ?: return getNoInternetOrAirplaneMode()
                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                    ?: return getNoInternetOrAirplaneMode()
                if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    return Hint.NO_INTERNET
                }
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    if (isAirplaneMode()) {
                        return Hint.AIRPLANE_MODE
                    }
                }
                Hint.ACTIVE
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                if (networkInfo != null
                    && (networkInfo.isConnected
                            || networkInfo.type == ConnectivityManager.TYPE_ETHERNET)
                ) {
                    Hint.ACTIVE
                } else {
                    getNoInternetOrAirplaneMode()
                }
            }
        } catch (e: RuntimeException) {
            Log.d(Config.LOGTAG, "unable to check for internet connection", e)
            Hint.ACTIVE
        }
    }

    private fun isAirplaneMode(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0
    }

    private fun getNoInternetOrAirplaneMode(): Hint {
        return if (isAirplaneMode()) Hint.AIRPLANE_MODE else Hint.NO_INTERNET
    }

    enum class Hint {
        ACTIVE,
        NO_INTERNET,
        AIRPLANE_MODE
    }
}
