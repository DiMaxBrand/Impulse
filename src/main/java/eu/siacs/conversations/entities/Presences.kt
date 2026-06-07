package eu.siacs.conversations.entities

import android.util.Pair
import com.google.common.base.Strings
import com.google.common.collect.Collections2
import com.google.common.collect.Iterables
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.manager.DiscoManager
import im.conversations.android.xmpp.model.disco.info.Identity
import im.conversations.android.xmpp.model.stanza.Presence
import java.util.HashMap

object Presences {

    private fun nameWithoutVersion(name: String): String {
        val parts = name.split(" ")
        if (parts.size > 1 && Character.isDigit(parts[parts.size - 1][0])) {
            val output = StringBuilder()
            for (i in 0 until parts.size - 1) {
                if (output.isNotEmpty()) {
                    output.append(' ')
                }
                output.append(parts[i])
            }
            return output.toString()
        } else {
            return name
        }
    }

    @JvmStatic
    fun asTemplates(presences: List<Presence>): Collection<PresenceTemplate> =
        Collections2.transform(
            Collections2.filter(presences) { p -> !Strings.isNullOrEmpty(p.getStatus()) },
            { p -> PresenceTemplate(p.getAvailability(), p.getStatus()) }
        )

    @JvmStatic
    fun toTypeAndNameMap(
        account: Account,
        presences: List<Presence>
    ): Pair<Map<Jid, String>, Map<Jid, String>> {
        val connection = account.getXmppConnection()
        val typeMap: MutableMap<Jid, String> = HashMap()
        val nameMap: MutableMap<Jid, String> = HashMap()
        for (presence in presences) {
            val serviceDiscoveryResult =
                connection.getManager(DiscoManager::class.java).get(presence.getFrom())
            if (serviceDiscoveryResult != null &&
                    serviceDiscoveryResult.getIdentities().isNotEmpty()) {
                val identity: Identity =
                    Iterables.getFirst(serviceDiscoveryResult.getIdentities(), null)!!
                val type = identity.getType()
                val name = identity.getIdentityName()
                if (type != null) {
                    typeMap[presence.getFrom()] = type
                }
                if (name != null) {
                    nameMap[presence.getFrom()] = nameWithoutVersion(name)
                }
            }
        }
        return Pair(typeMap, nameMap)
    }
}
