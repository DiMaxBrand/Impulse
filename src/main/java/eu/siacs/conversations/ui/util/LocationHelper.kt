package eu.siacs.conversations.ui.util

import android.location.Location
import eu.siacs.conversations.Config
import org.osmdroid.util.GeoPoint

object LocationHelper {

    /**
     * Parses a lat long string in the form "lat,long".
     *
     * @param latlong A string in the form "lat,long"
     * @return A GeoPoint representing the lat,long string.
     * @throws NumberFormatException If an invalid lat or long is specified.
     */
    @JvmStatic
    @Throws(NumberFormatException::class)
    fun parseLatLong(latlong: String?): GeoPoint? {
        if (latlong == null || latlong.isEmpty()) {
            return null
        }

        val parts = latlong.split(",").toTypedArray()
        if (parts[1].contains("?")) {
            parts[1] = parts[1].substring(0, parts[1].indexOf("?"))
        }
        return GeoPoint(parts[0].toDouble(), parts[1].toDouble())
    }

    private fun isSameProvider(provider1: String?, provider2: String?): Boolean {
        if (provider1 == null) {
            return provider2 == null
        }
        return provider1 == provider2
    }

    @JvmStatic
    fun isBetterLocation(location: Location, prevLoc: Location?): Boolean {
        if (prevLoc == null) {
            return true
        }

        // Check whether the new location fix is newer or older
        val timeDelta = location.time - prevLoc.time
        val isSignificantlyNewer = timeDelta > Config.Map.LOCATION_FIX_SIGNIFICANT_TIME_DELTA
        val isSignificantlyOlder = timeDelta < -Config.Map.LOCATION_FIX_SIGNIFICANT_TIME_DELTA
        val isNewer = timeDelta > 0

        if (isSignificantlyNewer) {
            return true
        } else if (isSignificantlyOlder) {
            return false
        }

        // Check whether the new location fix is more or less accurate
        val accuracyDelta = (location.accuracy - prevLoc.accuracy).toInt()
        val isLessAccurate = accuracyDelta > 0
        val isMoreAccurate = accuracyDelta < 0
        val isSignificantlyLessAccurate = accuracyDelta > 200

        // Check if the old and new location are from the same provider
        val isFromSameProvider = isSameProvider(location.provider, prevLoc.provider)

        // Determine location quality using a combination of timeliness and accuracy
        return if (isMoreAccurate) {
            true
        } else if (isNewer && !isLessAccurate) {
            true
        } else {
            isNewer && !isSignificantlyLessAccurate && isFromSameProvider
        }
    }
}
