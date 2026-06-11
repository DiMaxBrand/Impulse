package eu.siacs.conversations.entities

import androidx.annotation.NonNull
import eu.siacs.conversations.utils.UIHelper
import eu.siacs.conversations.xmpp.Jid
import im.conversations.android.model.DynamicTag
import java.util.Collections

class RawBlockable(@NonNull private val account: Account, @NonNull private val jid: Jid) :
    ListItem, Blockable {

    override fun isBlocked(): Boolean = true

    override fun isDomainBlocked(): Boolean {
        throw AssertionError("not implemented")
    }

    @NonNull
    override fun getBlockedAddress(): Jid = this.jid

    override fun getDisplayName(): String =
        if (jid.isFullJid()) {
            jid.getResource()
        } else {
            jid.toString()
        }

    override fun getAddress(): Jid = this.jid

    override fun getTags(): List<DynamicTag> = Collections.emptyList()

    override fun getAccount(): Account = account

    override fun getAvatarBackgroundColor(): Int = UIHelper.getColorForName(jid.toString())
}
