package eu.siacs.conversations.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import eu.siacs.conversations.BuildConfig

/**
 * What this phone has learned about how long updates take, kept to make the next estimate better:
 *  - the download speed of the last few finished downloads, separately for Wi-Fi, mobile data and
 *    anything else, used as the first guess in the seconds before a download has shown its real speed;
 *  - how long it took from starting to install an update until the new version first ran, to say when
 *    the whole thing will be done and not only the download.
 */
object UpdateTimingHistory {
    private const val PREFS = "update_timing"
    private const val KEEP = 5

    enum class Network(val key: String) { WIFI("wifi"), MOBILE("mobile"), OTHER("other") }

    fun network(context: Context): Network {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return Network.OTHER
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Network.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Network.MOBILE
            else -> Network.OTHER
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun median(values: List<Double>): Double? =
        values.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

    private fun list(context: Context, key: String): List<Double> =
        (prefs(context).getString(key, "") ?: "").split(',').mapNotNull { it.toDoubleOrNull() }

    private fun push(context: Context, key: String, value: Double) {
        val next = (list(context, key) + value).takeLast(KEEP)
        prefs(context).edit().putString(key, next.joinToString(",")).apply()
    }

    /** The typical speed (bytes/s) of recent finished downloads on this kind of network, if any;
     * otherwise a cautious share of what Android says the network can do. */
    fun speedPrior(context: Context): Double? {
        val kind = network(context)
        median(list(context, "speed_${kind.key}"))?.let { return it }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val kbps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.linkDownstreamBandwidthKbps ?: 0
        return if (kbps > 0) kbps * 125.0 * 0.35 else null
    }

    fun recordSpeed(context: Context, bytesPerSecond: Double) {
        if (bytesPerSecond > 1000.0) push(context, "speed_${network(context).key}", bytesPerSecond)
    }

    /** Typical seconds from starting an install until the new version runs, if known. */
    fun installSeconds(context: Context): Double? = median(list(context, "install_seconds"))

    fun markInstallStart(context: Context) {
        prefs(context).edit()
            .putLong("install_started", System.currentTimeMillis())
            .putString("install_from", BuildConfig.VERSION_NAME)
            .apply()
    }

    /** Called when the app starts: if it is a different version from the one an install began on, that install is done. */
    fun markInstallDoneIfUpdated(context: Context) {
        val p = prefs(context)
        val started = p.getLong("install_started", 0L)
        if (started == 0L) return
        val age = System.currentTimeMillis() - started
        if (BuildConfig.VERSION_NAME != p.getString("install_from", null)) {
            if (age in 5_000L..30 * 60_000L) push(context, "install_seconds", age / 1000.0)
            p.edit().remove("install_started").remove("install_from").apply()
        } else if (age > 60 * 60_000L) {
            // Never happened (cancelled): forget it.
            p.edit().remove("install_started").remove("install_from").apply()
        }
    }
}
