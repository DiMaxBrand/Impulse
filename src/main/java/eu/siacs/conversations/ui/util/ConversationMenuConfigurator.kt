package eu.siacs.conversations.ui.util

import android.content.Context
import android.content.pm.PackageManager
import android.view.Menu
import android.view.MenuItem
import eu.siacs.conversations.R
import eu.siacs.conversations.crypto.OmemoSetting
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message

object ConversationMenuConfigurator {

    @JvmField
    var microphoneAvailable = false

    @JvmStatic
    fun reloadFeatures(context: Context) {
        microphoneAvailable =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    }

    @JvmStatic
    fun configureEncryptionMenu(conversation: Conversation, menu: Menu) {
        val menuSecure: MenuItem = menu.findItem(R.id.action_security)

        val participating =
            conversation.getMode() == Conversational.MODE_SINGLE
                    || conversation.getMucOptions().participating()

        if (!participating) {
            menuSecure.isVisible = false
            return
        }

        val none: MenuItem = menu.findItem(R.id.encryption_choice_none)
        val pgp: MenuItem = menu.findItem(R.id.encryption_choice_pgp)
        val axolotl: MenuItem = menu.findItem(R.id.encryption_choice_axolotl)

        val next = conversation.nextEncryption

        val visible: Boolean
        if (OmemoSetting.isAlways()) {
            visible = false
        } else if (conversation.getMode() == Conversational.MODE_MULTI) {
            visible =
                next != Message.ENCRYPTION_NONE
                        || conversation.isPrivateAndNonAnonymous
                        || conversation.getBooleanAttribute(
                            Conversation.ATTRIBUTE_FORMERLY_PRIVATE_NON_ANONYMOUS, false
                        )
        } else {
            visible = true
        }

        menuSecure.isVisible = visible

        if (!visible) {
            return
        }

        if (next == Message.ENCRYPTION_NONE) {
            menuSecure.setIcon(R.drawable.ic_lock_open_outline_24dp)
        } else {
            menuSecure.setIcon(R.drawable.ic_lock_24dp)
        }

        pgp.isVisible = true
        none.isVisible = true
        axolotl.isVisible = true
        when (next) {
            Message.ENCRYPTION_PGP -> {
                menuSecure.setTitle(R.string.encrypted_with_openpgp)
                pgp.isChecked = true
            }
            Message.ENCRYPTION_AXOLOTL -> {
                menuSecure.setTitle(R.string.encrypted_with_omemo)
                axolotl.isChecked = true
            }
            else -> {
                menuSecure.setTitle(R.string.not_encrypted)
                none.isChecked = true
            }
        }
    }
}
