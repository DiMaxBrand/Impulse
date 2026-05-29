package eu.siacs.conversations.utils

import eu.siacs.conversations.services.AbstractQuickConversationsService
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.Profile
import android.provider.Settings
import com.google.common.base.Strings

object PhoneHelper {

    @JvmStatic
    @SuppressLint("HardwareIds")
    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    @JvmStatic
    fun getProfilePictureUri(context: Context): Uri? {
        if (!AbstractQuickConversationsService.isContactListIntegration(context)
            || context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val projection = arrayOf(Profile._ID, Profile.PHOTO_URI)
        context.contentResolver
            .query(Profile.CONTENT_URI, projection, null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val photoUri = cursor.getString(1)
                    if (Strings.isNullOrEmpty(photoUri)) {
                        return null
                    }
                    return Uri.parse(photoUri)
                }
            }
        return null
    }
}
