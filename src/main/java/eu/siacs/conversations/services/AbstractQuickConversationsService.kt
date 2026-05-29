package eu.siacs.conversations.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.google.common.collect.Iterables
import eu.siacs.conversations.BuildConfig
import java.util.Arrays

abstract class AbstractQuickConversationsService(protected val service: XmppConnectionService) {

    abstract fun considerSync()

    abstract fun signalAccountStateChange()

    abstract fun isSynchronizing(): Boolean

    abstract fun considerSyncBackground(force: Boolean)

    abstract fun handleSmsReceived(intent: Intent)

    companion object {
        const val SMS_RETRIEVED_ACTION = "com.google.android.gms.auth.api.phone.SMS_RETRIEVED"

        private var declaredReadContacts: Boolean? = null

        @JvmStatic
        fun isQuicksy(): Boolean {
            return "quicksy" == BuildConfig.FLAVOR_mode
        }

        @JvmStatic
        fun isConversations(): Boolean {
            return "conversations" == BuildConfig.FLAVOR_mode
        }

        @JvmStatic
        fun isPlayStoreFlavor(): Boolean {
            return "playstore" == BuildConfig.FLAVOR_distribution
        }

        @JvmStatic
        fun isContactListIntegration(context: Context): Boolean {
            if ("quicksy" == BuildConfig.FLAVOR_mode) {
                return true
            }
            val readContacts = declaredReadContacts
            if (readContacts != null) {
                return readContacts
            }
            declaredReadContacts = hasDeclaredReadContacts(context)
            return declaredReadContacts!!
        }

        private fun hasDeclaredReadContacts(context: Context): Boolean {
            val permissions: Array<String?>?
            try {
                permissions = context.packageManager
                    .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                    .requestedPermissions
            } catch (e: PackageManager.NameNotFoundException) {
                return false
            }
            if (permissions == null) return false
            return Iterables.any(Arrays.asList(*permissions)) { p -> p == Manifest.permission.READ_CONTACTS }
        }

        @JvmStatic
        fun isQuicksyPlayStore(): Boolean {
            return isQuicksy() && isPlayStoreFlavor()
        }
    }
}
