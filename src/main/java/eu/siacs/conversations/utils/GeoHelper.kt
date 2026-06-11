package eu.siacs.conversations.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.preference.PreferenceManager
import de.gultsch.common.Patterns
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Contact
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.ui.ShareLocationActivity
import eu.siacs.conversations.ui.ShowLocationActivity
import org.osmdroid.util.GeoPoint
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

object GeoHelper {

    private const val SHARE_LOCATION_PACKAGE_NAME = "eu.siacs.conversations.location.request"
    private const val SHOW_LOCATION_PACKAGE_NAME = "eu.siacs.conversations.location.show"

    @JvmStatic
    fun isLocationPluginInstalled(context: Context): Boolean {
        return Intent(SHARE_LOCATION_PACKAGE_NAME).resolveActivity(context.packageManager) != null
    }

    @JvmStatic
    fun isLocationPluginInstalledAndDesired(context: Context): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val configured = preferences.getBoolean(
            "use_share_location_plugin",
            context.resources.getBoolean(R.bool.use_share_location_plugin)
        )
        return configured && isLocationPluginInstalled(context)
    }

    @JvmStatic
    fun getFetchIntent(context: Context): Intent {
        return if (isLocationPluginInstalledAndDesired(context)) {
            Intent(SHARE_LOCATION_PACKAGE_NAME)
        } else {
            Intent(context, ShareLocationActivity::class.java)
        }
    }

    @JvmStatic
    fun parseGeoPoint(uri: Uri): GeoPoint {
        return parseGeoPoint(uri.toString())
    }

    private fun parseGeoPoint(body: String): GeoPoint {
        val matcher = Patterns.URI_GEO.matcher(body)
        if (!matcher.matches()) {
            throw IllegalArgumentException("Invalid geo uri")
        }
        val latitude: Double
        val longitude: Double
        try {
            latitude = matcher.group(1)!!.toDouble()
            if (latitude > 90.0 || latitude < -90.0) {
                throw IllegalArgumentException("Invalid geo uri")
            }
            longitude = matcher.group(2)!!.toDouble()
            if (longitude > 180.0 || longitude < -180.0) {
                throw IllegalArgumentException("Invalid geo uri")
            }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid geo uri", e)
        }
        return GeoPoint(latitude, longitude)
    }

    @JvmStatic
    fun createGeoIntentsFromMessage(context: Context, message: Message): ArrayList<Intent> {
        val intents = ArrayList<Intent>()
        val geoPoint = try {
            parseGeoPoint(message.body)
        } catch (e: IllegalArgumentException) {
            return intents
        }
        val conversation = message.conversation
        val label = getLabel(context, message)

        if (isLocationPluginInstalledAndDesired(context)) {
            val locationPluginIntent = Intent(SHOW_LOCATION_PACKAGE_NAME)
            locationPluginIntent.putExtra("latitude", geoPoint.latitude)
            locationPluginIntent.putExtra("longitude", geoPoint.longitude)
            if (message.status != Message.STATUS_RECEIVED) {
                locationPluginIntent.putExtra("jid", conversation.getAccount().jid.toString())
                locationPluginIntent.putExtra("name", conversation.getAccount().jid.local)
            } else {
                locationPluginIntent.putExtra("name", UIHelper.getMessageDisplayName(message))
                val contact: Contact? = message.contact
                if (contact != null) {
                    locationPluginIntent.putExtra("jid", contact.getAddress().toString())
                }
            }
            intents.add(locationPluginIntent)
        } else {
            val intent = Intent(context, ShowLocationActivity::class.java)
            intent.action = SHOW_LOCATION_PACKAGE_NAME
            intent.putExtra("latitude", geoPoint.latitude)
            intent.putExtra("longitude", geoPoint.longitude)
            intents.add(intent)
        }

        intents.add(geoIntent(geoPoint, label))

        val httpIntent = Intent(Intent.ACTION_VIEW)
        httpIntent.data = Uri.parse(
            "https://maps.google.com/maps?q=loc:${geoPoint.latitude},${geoPoint.longitude}$label"
        )
        intents.add(httpIntent)
        return intents
    }

    @JvmStatic
    fun view(context: Context, message: Message) {
        val geoPoint = parseGeoPoint(message.body)
        val label = getLabel(context, message)
        context.startActivity(geoIntent(geoPoint, label))
    }

    private fun geoIntent(geoPoint: GeoPoint, label: String): Intent {
        val geoIntent = Intent(Intent.ACTION_VIEW)
        geoIntent.data = Uri.parse(
            "geo:${geoPoint.latitude},${geoPoint.longitude}?q=${geoPoint.latitude},${geoPoint.longitude}($label)"
        )
        return geoIntent
    }

    @JvmStatic
    fun openInOsmAnd(context: Context, message: Message): Boolean {
        return try {
            val geoPoint = parseGeoPoint(message.body)
            val label = getLabel(context, message)
            geoIntent(geoPoint, label).resolveActivity(context.packageManager) != null
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun getLabel(context: Context, message: Message): String {
        return if (message.status == Message.STATUS_RECEIVED) {
            try {
                URLEncoder.encode(UIHelper.getMessageDisplayName(message), "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                throw AssertionError(e)
            }
        } else {
            context.getString(R.string.me)
        }
    }
}
