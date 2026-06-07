package eu.siacs.conversations.utils

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale

object LocationProvider {

    @JvmField
    val FALLBACK: GeoPoint = GeoPoint(0.0, 0.0)

    @JvmStatic
    fun getUserCountry(context: Context): String {
        try {
            val tm = ContextCompat.getSystemService(context, TelephonyManager::class.java)
            if (tm == null) {
                return getUserCountryFallback()
            }
            val simCountry = tm.simCountryIso
            if (simCountry != null && simCountry.length == 2) { // SIM country code is available
                return simCountry.uppercase(Locale.US)
            } else if (tm.phoneType != TelephonyManager.PHONE_TYPE_CDMA) { // device is not 3G (would be unreliable)
                val networkCountry = tm.networkCountryIso
                if (networkCountry != null && networkCountry.length == 2) { // network country code is available
                    return networkCountry.uppercase(Locale.US)
                }
            }
            return getUserCountryFallback()
        } catch (e: Exception) {
            return getUserCountryFallback()
        }
    }

    private fun getUserCountryFallback(): String {
        val locale = Locale.getDefault()
        return locale.country
    }

    @JvmStatic
    fun getGeoPoint(context: Context): GeoPoint {
        return getGeoPoint(context, getUserCountry(context))
    }

    @JvmStatic
    @Synchronized
    fun getGeoPoint(context: Context, country: String): GeoPoint {
        try {
            BufferedReader(InputStreamReader(context.resources.openRawResource(R.raw.countries))).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.split("\\s+".toRegex(), limit = 4).toTypedArray()
                    if (parts.size == 4) {
                        if (country.equals(parts[0], ignoreCase = true)) {
                            try {
                                return GeoPoint(parts[1].toDouble(), parts[2].toDouble())
                            } catch (e: NumberFormatException) {
                                return FALLBACK
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.d(Config.LOGTAG, "unable to parse country->geo map", e)
        }
        return FALLBACK
    }
}
