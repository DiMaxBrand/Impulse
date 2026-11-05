package eu.siacs.conversations.ui

import eu.siacs.conversations.entities.Message

/** Never treat rows from a previous screen as history belonging to the current chat. */
internal fun messagesForConversation(uuid: String?, messages: List<Message>): List<Message> =
    if (uuid == null) emptyList() else messages.filter { it.getConversationUuid() == uuid }

internal fun mergeConversationHistory(
    uuid: String?, current: List<Message>, loaded: List<Message>,
): List<Message> = messagesForConversation(uuid, current + loaded)
    .distinctBy { it.getUuid() }.sortedBy { it.timeSent }

/** Ten rows at the origin, and the target with ten rows on either side; no middle gap rows. */
internal fun navigationWindowIndices(count: Int, origin: Int, target: Int): List<Int> {
    if (count <= 0) return emptyList()
    val start = (origin - 4).coerceIn(0, (count - 10).coerceAtLeast(0))
    return ((start until minOf(start + 10, count)) +
        ((target - 10).coerceAtLeast(0)..(target + 10).coerceAtMost(count - 1)))
        .distinct().sorted()
}
