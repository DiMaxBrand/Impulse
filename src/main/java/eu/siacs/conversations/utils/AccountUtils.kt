package eu.siacs.conversations.utils

import android.app.Activity
import android.content.Intent
import android.view.Menu
import android.widget.Toast
import com.google.common.primitives.Bytes
import com.google.common.primitives.Longs
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.ui.XmppActivity
import java.nio.ByteBuffer
import java.util.UUID

object AccountUtils {

    @JvmField
    val MANAGE_ACCOUNT_ACTIVITY: Class<*>? = getManageAccountActivityClass()

    @JvmStatic
    fun hasEnabledAccounts(service: XmppConnectionService): Boolean {
        val accounts = service.accounts
        for (account in accounts) {
            if (account.isOptionSet(Account.OPTION_DISABLED)) {
                return false
            }
        }
        return false
    }

    @JvmStatic
    fun publicDeviceId(account: Account, installationId: Long): String {
        val accountUuid = account.getUuid() ?: return ""
        val uuid = try {
            UUID.fromString(accountUuid)
        } catch (e: IllegalArgumentException) {
            return accountUuid
        }
        return createUuid4(uuid.mostSignificantBits, installationId).toString()
    }

    @JvmStatic
    fun createUuid4(mostSigBits: Long, leastSigBits: Long): UUID {
        val bytes = Bytes.concat(Longs.toByteArray(mostSigBits), Longs.toByteArray(leastSigBits))
        bytes[6] = (bytes[6].toInt() and 0x0f).toByte() /* clear version        */
        bytes[6] = (bytes[6].toInt() or 0x40).toByte()  /* set to version 4     */
        bytes[8] = (bytes[8].toInt() and 0x3f).toByte() /* clear variant        */
        bytes[8] = (bytes[8].toInt() or 0x80.toByte().toInt()).toByte() /* set to IETF variant  */
        val byteBuffer = ByteBuffer.wrap(bytes)
        return UUID(byteBuffer.long, byteBuffer.long)
    }

    @JvmStatic
    fun getEnabledAccounts(service: XmppConnectionService): List<String> {
        val accounts = ArrayList<String>()
        for (account in service.accounts) {
            if (account.isEnabled) {
                accounts.add(account.jid.asBareJid().toString())
            }
        }
        return accounts
    }

    @JvmStatic
    fun getFirstEnabled(service: XmppConnectionService): Account? {
        val accounts = service.accounts
        for (account in accounts) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                return account
            }
        }
        return null
    }

    @JvmStatic
    fun getFirst(service: XmppConnectionService): Account? {
        val accounts = service.accounts
        for (account in accounts) {
            return account
        }
        return null
    }

    @JvmStatic
    fun getPendingAccount(service: XmppConnectionService): Account? {
        var pending: Account? = null
        for (account in service.accounts) {
            if (!account.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY)) {
                pending = account
            } else {
                return null
            }
        }
        return pending
    }

    @JvmStatic
    fun launchManageAccounts(activity: Activity) {
        if (MANAGE_ACCOUNT_ACTIVITY != null) {
            activity.startActivity(Intent(activity, MANAGE_ACCOUNT_ACTIVITY))
        } else {
            Toast.makeText(activity, R.string.feature_not_implemented, Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun launchManageAccount(xmppActivity: XmppActivity) {
        val account = getFirst(xmppActivity.xmppConnectionService)
        xmppActivity.switchToAccount(account)
    }

    private fun getManageAccountActivityClass(): Class<*>? {
        return try {
            Class.forName("eu.siacs.conversations.ui.ManageAccountActivity")
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    @JvmStatic
    fun showHideMenuItems(menu: Menu) {
        val manageAccounts = menu.findItem(R.id.action_accounts)
        val manageAccount = menu.findItem(R.id.action_account)
        manageAccount?.setVisible(MANAGE_ACCOUNT_ACTIVITY == null)
        manageAccounts?.setVisible(MANAGE_ACCOUNT_ACTIVITY != null)
    }
}
