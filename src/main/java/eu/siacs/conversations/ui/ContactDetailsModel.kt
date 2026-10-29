package eu.siacs.conversations.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.common.base.Strings
import eu.siacs.conversations.R
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus
import eu.siacs.conversations.entities.Contact
import im.conversations.android.model.DynamicTag
import eu.siacs.conversations.ui.util.Attachment
import eu.siacs.conversations.utils.ContactGenderOverride
import eu.siacs.conversations.utils.CryptoHelper
import eu.siacs.conversations.utils.NameGenderGuesser
import im.conversations.android.xmpp.model.stanza.Presence
import org.openintents.openpgp.util.OpenPgpUtils

/*
 * What the contact screen shows, worked out once per refresh from the entities, so the Compose side only
 * draws (and never touches the entities, which change in place).
 */

/** The state the screen reads: replaced as a whole whenever the contact or its keys change. */
@Stable
class ContactDetailsState {
    var ui by mutableStateOf<ContactDetailsUi?>(null)
    var media by mutableStateOf<List<Attachment>>(emptyList())
    var mediaAllowed by mutableStateOf(false)
}

class PresenceToggle(val label: String, val checked: Boolean, val enabled: Boolean)

/** One OMEMO device of the contact. */
class FingerprintRowUi(
    /** The stored form (with its leading type digits), what the trust calls take. */
    val fingerprint: String,
    /** Grouped for reading. */
    val pretty: String,
    val active: Boolean,
    val verified: Boolean,
    val trusted: Boolean,
    val undecided: Boolean,
    val x509: Boolean,
    /** This is the key the message that brought the user here was encrypted with. */
    val highlighted: Boolean,
)

class ContactDetailsUi(
    val name: String,
    val address: String,
    val account: String,
    val inRoster: Boolean,
    /** "Online", "Away"...: null when offline. */
    val presenceLabel: String?,
    val statusMessages: List<String>,
    val send: PresenceToggle?,
    val receive: PresenceToggle?,
    val genderText: String,
    val birthdayText: String?,
    val birthdayToday: Boolean,
    /** "Save to contacts" / "Show in contacts"; null when the address book is not in use. */
    val addressBookLabel: Int?,
    val inAddressBook: Boolean,
    val tags: List<DynamicTag>,
    val keys: List<FingerprintRowUi>,
    val pgpKeyHex: String?,
    val unverifiedWarning: Boolean,
    /** The label of the "show / hide inactive devices" toggle, or null when there are none to show. */
    val inactiveToggleLabel: Int?,
    val canScan: Boolean,
    val blocked: Boolean,
    val canBlock: Boolean,
    val canEditDelete: Boolean,
    val customNotifications: Boolean,
    /** Changes when the picture does: the screen reloads it. */
    val avatarKey: String?,
)

interface ContactDetailsActions {
    fun onBack()
    fun onMessage()
    fun onAvatar()
    fun onAccount()
    fun onCopyAddress()
    fun onSendPresence(checked: Boolean)
    fun onReceivePresence(checked: Boolean)
    fun onGender()
    fun onAddressBook()
    fun onAddToRoster()
    fun onShareLink(http: Boolean)
    fun onEdit()
    fun onDelete()
    fun onBlock()
    fun onCustomNotifications()
    fun onToggleInactive()
    fun onScan()
    fun onTrust(key: FingerprintRowUi, trusted: Boolean)
    fun onEnableDevice(key: FingerprintRowUi)
    fun onCopyKey(key: FingerprintRowUi)
    fun onDistrust(key: FingerprintRowUi)
    fun onPgpKey()
    fun onViewMedia()
    fun onOpenAttachment(attachment: Attachment)

    /** The contact's picture at about [sizePx] (a letter tile when there is none). Called off the main thread. */
    fun avatar(sizePx: Int): Bitmap?

    /** A preview of an attachment at about [sizePx]. */
    fun preview(attachment: Attachment, sizePx: Int): Bitmap?
}

object ContactDetailsPresenter {

    /** Called from the activity (Java): everything the screen shows about [contact]. */
    @JvmStatic
    fun build(
        context: Context,
        contact: Contact,
        showInactive: Boolean,
        messageFingerprint: String?,
        showDynamicTags: Boolean,
        cameraAvailable: Boolean,
        contactListIntegration: Boolean,
    ): ContactDetailsUi {
        val inRoster = contact.showInRoster()
        val presences = contact.getPresences()
        val statuses = presences.mapNotNull { it.getStatus() }.filter { it.isNotEmpty() }.distinct()

        var send: PresenceToggle? = null
        var receive: PresenceToggle? = null
        if (inRoster) {
            val online = contact.getAccount().isOnlineAndConnected()
            send = if (contact.getOption(Contact.Options.FROM)) {
                PresenceToggle(context.getString(R.string.send_presence_updates), true, online)
            } else if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                PresenceToggle(context.getString(R.string.send_presence_updates), false, online)
            } else {
                PresenceToggle(
                    context.getString(R.string.preemptively_grant),
                    contact.getOption(Contact.Options.PREEMPTIVE_GRANT), online,
                )
            }
            receive = if (contact.getOption(Contact.Options.TO)) {
                PresenceToggle(context.getString(R.string.receive_presence_updates), true, online)
            } else {
                PresenceToggle(
                    context.getString(R.string.ask_for_presence_updates),
                    contact.getOption(Contact.Options.ASKING), online,
                )
            }
        }

        val availability = contact.getShownStatus()
        val presenceLabel = when (availability) {
            Presence.Availability.CHAT, Presence.Availability.ONLINE -> context.getString(R.string.presence_online)
            Presence.Availability.AWAY -> context.getString(R.string.presence_away)
            Presence.Availability.XA -> context.getString(R.string.presence_xa)
            Presence.Availability.DND -> context.getString(R.string.presence_dnd)
            else -> null
        }

        // OMEMO devices (the fingerprints stay at the bottom of the screen).
        val keys = ArrayList<FingerprintRowUi>()
        var hasKeys = false
        var unverifiedWarning = false
        var skippedInactive = false
        var showsInactive = false
        val axolotl = contact.getAccount().getAxolotlService()
        if (axolotl != null) {
            val sessions = axolotl.findSessionsForContact(contact)
            val anyActive = sessions.any { it.getTrust().isActive() }
            for (session in sessions) {
                val trust = session.getTrust()
                hasKeys = hasKeys or !trust.isCompromised()
                if (!trust.isActive() && anyActive) {
                    if (showInactive) showsInactive = true else { skippedInactive = true; continue }
                }
                if (!trust.isCompromised()) {
                    val fp = session.getFingerprint()
                    keys += FingerprintRowUi(
                        fingerprint = fp,
                        pretty = CryptoHelper.prettifyFingerprint(fp.substring(2)),
                        active = trust.isActive(),
                        verified = trust.isVerified(),
                        trusted = trust.isTrusted(),
                        undecided = trust.getTrust() == FingerprintStatus.Trust.UNDECIDED,
                        x509 = eu.siacs.conversations.Config.X509_VERIFICATION &&
                            trust.getTrust() == FingerprintStatus.Trust.VERIFIED_X509,
                        highlighted = fp == messageFingerprint,
                    )
                }
                if (trust.isUnverified()) unverifiedWarning = true
            }
        }
        val pgp = contact.getPgpKeyId()
        if (pgp != 0L) hasKeys = true

        val birthday = contact.getAccount().getUuid()?.let {
            eu.siacs.conversations.utils.BirthdayStore(context).get(it, contact.getAddress().asBareJid().toString())
        }
        val today = java.time.LocalDate.now()
        val birthdayText = birthday?.let {
            context.getString(R.string.birthday_contact_row, it.describe(context, today))
        }

        val connection = contact.getAccount().getXmppConnection()
        val canBlock = connection != null &&
            connection.getManager(eu.siacs.conversations.xmpp.manager.BlockingManager::class.java).hasFeature()
        val tags = contact.getTags()
        val shownTags = if (eu.siacs.conversations.entities.Contact.isNoteworthy(tags) || showDynamicTags) tags else emptyList()

        return ContactDetailsUi(
            name = contact.getDisplayName(),
            address = contact.getAddress().toString(),
            account = contact.getAccount().getJid().asBareJid().toString(),
            inRoster = inRoster,
            presenceLabel = presenceLabel,
            statusMessages = statuses,
            send = send,
            receive = receive,
            genderText = context.getString(R.string.contact_grammatical_gender, genderLabel(context, contact)),
            birthdayText = birthdayText,
            birthdayToday = birthday?.isOn(today) == true,
            addressBookLabel = if (contactListIntegration) {
                if (contact.getSystemAccount() == null) R.string.save_to_contact else R.string.show_in_contacts
            } else null,
            inAddressBook = contact.getSystemAccount() != null,
            tags = shownTags,
            keys = keys,
            pgpKeyHex = if (pgp != 0L) OpenPgpUtils.convertKeyIdToHex(pgp) else null,
            unverifiedWarning = unverifiedWarning,
            inactiveToggleLabel = if (showsInactive || skippedInactive) {
                if (showsInactive) R.string.hide_inactive_devices else R.string.show_inactive_devices
            } else null,
            canScan = hasKeys && cameraAvailable,
            blocked = contact.isBlocked(),
            canBlock = canBlock,
            canEditDelete = inRoster,
            customNotifications = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R,
            avatarKey = contact.getAvatar() ?: contact.getProfilePhoto(),
        )
    }

    private fun genderLabel(context: Context, contact: Contact): String {
        return when (ContactGenderOverride.get(context, contact)) {
            NameGenderGuesser.Gender.MASCULINE -> context.getString(R.string.contact_grammatical_gender_masculine)
            NameGenderGuesser.Gender.FEMININE -> context.getString(R.string.contact_grammatical_gender_feminine)
            NameGenderGuesser.Gender.UNKNOWN -> when (NameGenderGuesser.guess(contact.getDisplayName())) {
                NameGenderGuesser.Gender.MASCULINE -> context.getString(
                    R.string.contact_grammatical_gender_auto_detected,
                    context.getString(R.string.contact_grammatical_gender_masculine),
                )
                NameGenderGuesser.Gender.FEMININE -> context.getString(
                    R.string.contact_grammatical_gender_auto_detected,
                    context.getString(R.string.contact_grammatical_gender_feminine),
                )
                NameGenderGuesser.Gender.UNKNOWN -> context.getString(R.string.contact_grammatical_gender_auto)
            }
        }
    }
}
