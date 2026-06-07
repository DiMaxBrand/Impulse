package eu.siacs.conversations.xmpp.jingle

import com.google.common.base.Strings
import com.google.common.collect.Collections2
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import eu.siacs.conversations.entities.Contact
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.manager.DiscoManager
import im.conversations.android.xmpp.model.disco.info.InfoQuery
import im.conversations.android.xmpp.model.stanza.Presence
import java.util.Arrays

object RtpCapability {

    private val BASIC_RTP_REQUIREMENTS: List<String> = Arrays.asList(
        Namespace.JINGLE,
        Namespace.JINGLE_TRANSPORT_ICE_UDP,
        Namespace.JINGLE_APPS_RTP,
        Namespace.JINGLE_APPS_DTLS
    )
    private val VIDEO_REQUIREMENTS: Collection<String> = Arrays.asList(
        Namespace.JINGLE_FEATURE_AUDIO,
        Namespace.JINGLE_FEATURE_VIDEO
    )

    @JvmStatic
    fun check(infoQuery: InfoQuery?): Capability {
        val features: Set<String> = if (infoQuery == null) {
            emptySet()
        } else {
            ImmutableSet.copyOf(infoQuery.featureStrings)
        }
        if (features.containsAll(BASIC_RTP_REQUIREMENTS)) {
            if (features.containsAll(VIDEO_REQUIREMENTS)) {
                return Capability.VIDEO
            }
            if (features.contains(Namespace.JINGLE_FEATURE_AUDIO)) {
                return Capability.AUDIO
            }
        }
        return Capability.NONE
    }

    @JvmStatic
    fun filterPresences(contact: Contact, required: Capability): List<Presence> {
        val connection = contact.getAccount().xmppConnection
        val builder = ImmutableList.Builder<Presence>()
        for (presence in contact.presences) {
            val capability = check(connection.getManager(DiscoManager::class.java).get(presence.from))
            if (capability == Capability.NONE) {
                continue
            }
            if (required == Capability.AUDIO || capability == required) {
                builder.add(presence)
            }
        }
        return builder.build()
    }

    @JvmStatic
    fun checkWithFallback(contact: Contact): Capability {
        val presences = contact.presences
        if (presences.isEmpty() && contact.getAccount().isEnabled) {
            return contact.rtpCapability
        }
        return check(contact, presences)
    }

    @JvmStatic
    fun check(contact: Contact, presences: List<Presence>): Capability {
        val connection = contact.getAccount().xmppConnection
            ?: return Capability.NONE
        val capabilities: Set<Capability> = ImmutableSet.copyOf(
            Collections2.transform(presences) { p ->
                check(connection.getManager(DiscoManager::class.java).get(p!!.from))
            }
        )
        return when {
            capabilities.contains(Capability.VIDEO) -> Capability.VIDEO
            capabilities.contains(Capability.AUDIO) -> Capability.AUDIO
            else -> Capability.NONE
        }
    }

    // do all devices that support Rtp Call also support JMI?
    @JvmStatic
    fun jmiSupport(contact: Contact): Boolean {
        val connection = contact.getAccount().xmppConnection
            ?: return false
        return !Collections2.transform(
            Collections2.filter(contact.fullAddresses) { a ->
                RtpCapability.check(
                    connection.getManager(DiscoManager::class.java).get(a)
                ) != Capability.NONE
            }
        ) { a ->
            val disco = connection.getManager(DiscoManager::class.java).get(a)
            disco != null && disco.featureStrings.contains(Namespace.JINGLE_MESSAGE)
        }.contains(false)
    }

    enum class Capability {
        NONE,
        AUDIO,
        VIDEO;

        companion object {
            @JvmStatic
            fun of(value: String?): Capability {
                if (Strings.isNullOrEmpty(value)) {
                    return NONE
                }
                return try {
                    valueOf(value!!)
                } catch (e: IllegalArgumentException) {
                    NONE
                }
            }
        }
    }
}
