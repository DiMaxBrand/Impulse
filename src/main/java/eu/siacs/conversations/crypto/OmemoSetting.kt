package eu.siacs.conversations.crypto

import android.content.Context
import com.google.common.base.Strings
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.entities.Message

object OmemoSetting {

    private var always: Boolean = false
    private var encryption: Int = Message.ENCRYPTION_AXOLOTL

    @JvmStatic
    fun isAlways(): Boolean = always

    @JvmStatic
    fun getEncryption(): Int = encryption

    @JvmStatic
    fun load(context: Context) {
        val appSettings = AppSettings(context)
        val value = appSettings.omemo
        when (Strings.nullToEmpty(value)) {
            "always" -> {
                always = true
                encryption = Message.ENCRYPTION_AXOLOTL
            }
            "default_off" -> {
                always = false
                encryption = Message.ENCRYPTION_NONE
            }
            else -> {
                always = false
                encryption = Message.ENCRYPTION_AXOLOTL
            }
        }
    }
}
