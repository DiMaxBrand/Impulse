package eu.siacs.conversations.entities

import com.google.common.base.Objects
import com.google.common.base.Strings
import com.google.common.collect.ComparisonChain
import com.google.common.collect.Iterables
import com.google.common.primitives.Ints
import eu.siacs.conversations.services.AvatarService
import eu.siacs.conversations.utils.LanguageUtils
import eu.siacs.conversations.utils.UIHelper
import eu.siacs.conversations.xmpp.Jid
import im.conversations.android.xmpp.model.disco.info.InfoQuery

class Room(
    @JvmField val address: String,
    @JvmField val name: String?,
    @JvmField val description: String?,
    @JvmField val language: String?,
    numberOfUsers: Int?
) : AvatarService.Avatar, Comparable<Room> {

    @JvmField
    val numberOfUsers: Int = numberOfUsers ?: 0

    fun getName(): String {
        return if (Strings.isNullOrEmpty(name)) {
            val jid = Jid.ofOrInvalid(address)
            jid.local
        } else {
            name!!
        }
    }

    fun getDescription(): String? = description

    fun getRoom(): Jid? {
        return try {
            Jid.of(address)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun getLanguage(): String? = LanguageUtils.convert(language)

    override fun getAvatarBackgroundColor(): Int {
        val room = getRoom()
        return UIHelper.getColorForName(if (room != null) room.asBareJid().toString() else name)
    }

    override fun getDisplayName(): String? = name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val room = other as Room
        return Objects.equal(address, room.address)
                && Objects.equal(name, room.name)
                && Objects.equal(description, room.description)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(address, name, description)
    }

    fun contains(needle: String): Boolean {
        return Strings.nullToEmpty(name).contains(needle)
                || Strings.nullToEmpty(description).contains(needle)
                || Strings.nullToEmpty(address).contains(needle)
    }

    override fun compareTo(other: Room): Int {
        return ComparisonChain.start()
            .compare(other.numberOfUsers, numberOfUsers)
            .compare(Strings.nullToEmpty(name), Strings.nullToEmpty(other.name))
            .compare(Strings.nullToEmpty(address), Strings.nullToEmpty(other.address))
            .result()
    }

    companion object {
        @JvmStatic
        fun of(address: Jid, query: InfoQuery): Room {
            val identity = Iterables.getFirst(query.identities, null)
            val ri =
                query.getServiceDiscoveryExtension("http://jabber.org/protocol/muc#roominfo")
            val name: String? = identity?.identityName
            val roomName: String? = ri?.getValue("muc#roomconfig_roomname")
            val description: String? = ri?.getValue("muc#roominfo_description")
            val language: String? = ri?.getValue("muc#roominfo_lang")
            val occupants: String? = ri?.getValue("muc#roominfo_occupants")
            val numberOfUsers: Int? = Ints.tryParse(Strings.nullToEmpty(occupants))

            return Room(
                address.toString(),
                if (Strings.isNullOrEmpty(roomName)) name else roomName,
                description,
                language,
                numberOfUsers
            )
        }
    }
}
