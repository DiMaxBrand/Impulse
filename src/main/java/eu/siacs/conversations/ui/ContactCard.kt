package eu.siacs.conversations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R

/*
 * A shared contact as a card in the chat, instead of a line of text or a file.
 *
 * An Impulse contact travels as two lines of plain text, readable in any client:
 *     👤 Anna Petrova (anna@on-chat.ru)
 *     xmpp:anna@on-chat.ru
 * (the name is left out when it is not known: "👤 anna@on-chat.ru"). Impulse shows that as a card; a
 * bare "xmpp:address" line, as older versions sent, is shown as a card too. A contact from the
 * phone's address book travels as a .vcf file, and is shown as a card with its name and numbers.
 */

/** What a card shows. [username] is the Impulse address, [phones] the numbers from the phone's contact. */
internal class SharedContact(val name: String?, val username: String?, val phones: List<String>)

private const val MARK = "👤"

/** The two-line text that carries an Impulse contact. */
internal fun contactCardBody(name: String?, address: String): String {
    val shown = name?.trim().orEmpty()
    val first = if (shown.isNotEmpty() && shown != address) "$MARK $shown ($address)" else "$MARK $address"
    return first + "\nxmpp:" + address
}

private val CARD_LINE = Regex("^$MARK (?:(.+) \\((\\S+)\\)|(\\S+))$")

/** An Impulse contact from a message's text, or null when the text is something else. */
internal fun parseImpulseContact(body: String?): SharedContact? {
    val text = body?.trim() ?: return null
    val lines = text.lines()
    if (lines.size == 1) {
        // What older versions sent: just the address, as a link.
        val only = lines[0].trim()
        if (only.startsWith("xmpp:") && !only.contains('?') && !only.contains(' ') && only.length > 8) {
            return SharedContact(null, only.removePrefix("xmpp:"), emptyList())
        }
        return null
    }
    if (lines.size != 2) return null
    val link = lines[1].trim()
    if (!link.startsWith("xmpp:") || link.contains(' ') || link.contains('?')) return null
    val address = link.removePrefix("xmpp:")
    val match = CARD_LINE.matchEntire(lines[0].trim()) ?: return null
    val name = match.groupValues[1].ifBlank { null }
    return SharedContact(name, address, emptyList())
}

/** The name, numbers and Impulse address of a vCard file (the little of it the card needs). */
internal fun parseVCard(file: java.io.File): SharedContact? {
    if (!file.exists() || file.length() > 256 * 1024) return null
    val raw = try { file.readText() } catch (_: Exception) { return null }
    // Folded lines continue on the next line that starts with a space or a tab.
    val lines = raw.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "").lines()
    var name: String? = null
    var structured: String? = null
    var username: String? = null
    val phones = ArrayList<String>()
    for (line in lines) {
        val colon = line.indexOf(':')
        if (colon < 0) continue
        val key = line.substring(0, colon).substringBefore(';').uppercase()
        val value = line.substring(colon + 1).trim()
        when (key) {
            "FN" -> if (name == null && value.isNotEmpty()) name = value
            "N" -> if (structured == null) {
                val parts = value.split(';')
                structured = listOf(parts.getOrNull(3), parts.getOrNull(1), parts.getOrNull(2), parts.getOrNull(0))
                    .filter { !it.isNullOrBlank() }.joinToString(" ").ifBlank { null }
            }
            "TEL" -> if (value.isNotEmpty() && value !in phones) phones.add(value.removePrefix("tel:"))
            "IMPP", "X-JABBER", "X-XMPP" -> if (username == null) {
                val v = value.removePrefix("xmpp:").removePrefix("jabber:")
                if (v.contains('@')) username = v
            }
        }
    }
    if (name == null) name = structured
    if (name == null && phones.isEmpty() && username == null) return null
    return SharedContact(name, username, phones)
}

internal fun isVCardMime(mime: String?): Boolean =
    mime != null && (mime.startsWith("text/vcard") || mime.startsWith("text/x-vcard") || mime == "text/directory")

/**
 * The card: a round initial, the name as the main line, the Impulse username in brackets under it, the
 * numbers under that, and one button ([actionLabel]).
 */
@Composable
internal fun ContactCard(
    contact: SharedContact,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val title = contact.name?.takeIf { it.isNotBlank() } ?: contact.username ?: contact.phones.firstOrNull().orEmpty()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.widthIn(min = 220.dp, max = 300.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The username, in brackets, only when the name above is a name.
                    if (!contact.name.isNullOrBlank() && !contact.username.isNullOrBlank()) {
                        Text(
                            "(" + contact.username + ")",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    for (phone in contact.phones.take(3)) {
                        Text(
                            phone,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (actionLabel != null && onAction != null) {
                FilledTonalButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel)
                }
            }
        }
    }
}
