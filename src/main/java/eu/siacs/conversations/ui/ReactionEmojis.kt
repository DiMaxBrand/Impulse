package eu.siacs.conversations.ui

import net.fellbaum.jemoji.EmojiManager

/** Shared reaction-emoji data/logic used by both [QuickReactionPickerContent] (Settings/
 * double-tap default) and [AddReactionDialogFragment] (long-press "add reaction"), so the two
 * surfaces behave identically instead of drifting -- family detection, skin-tone/color variant
 * lookup, and the toggle-vs-replace-within-family commit logic all live here once. */

const val HEART_DEFAULT = "❤️"
const val THUMBS_UP_DEFAULT = "👍"

/** Heart color variants are distinct *colored* code points -- there's no Fitzpatrick skin-tone
 * modifier for a heart -- so unlike everything else on this page, this one small family has to
 * stay a hardcoded list rather than coming from jemoji's variation data. */
val HEART_VARIANTS = listOf(
    "❤️", // red
    "🧡", // orange
    "💛", // yellow
    "💚", // green
    "💙", // blue
    "💜", // purple
    "🤎", // brown
    "🖤", // black
    "🤍", // white
)

private val SKIN_TONE_MODIFIERS = listOf("🏻", "🏼", "🏽", "🏾", "🏿")

fun stripSkinTone(emoji: String): String {
    val modifier = SKIN_TONE_MODIFIERS.firstOrNull { emoji.endsWith(it) } ?: return emoji
    return emoji.removeSuffix(modifier)
}

fun isHeartFamily(emoji: String): Boolean = emoji in HEART_VARIANTS

fun isThumbsUpFamily(emoji: String): Boolean = stripSkinTone(emoji) == THUMBS_UP_DEFAULT

/** The full variant family for any emoji -- membership-based (checks what family [emoji] itself
 * belongs to, not whether it equals some hardcoded "default"), so this keeps working correctly
 * no matter which member of the family is currently selected. That distinction is exactly the bug
 * Quick Reactions originally shipped with: matching only the family's default code point meant a
 * second long-press after picking a non-default variant only ever offered that one variant back.
 *
 * Two sources: [HEART_VARIANTS] for hearts (hardcoded, see its own doc), and jemoji's real
 * Fitzpatrick skin-tone data (`Emoji.hasFitzpatrickComponent`/`getVariations`) for every other
 * emoji that has skin-tone variants -- not a hardcoded per-emoji list, so this covers the entire
 * shortcut row (thumbs up/down, clapping hands, praying hands, flexed biceps, waving hand, ...)
 * automatically, including anything a future change to the shortcut set adds. Neutral/base first,
 * then light to dark. Emoji with no variants of either kind return just themselves -- long-press
 * is then a no-op (no popup shown).
 */
fun variantsFor(emoji: String): List<String> {
    if (isHeartFamily(emoji)) return HEART_VARIANTS
    val base = stripSkinTone(emoji)
    val info = EmojiManager.getEmoji(base).orElse(null) ?: return listOf(emoji)
    if (!info.hasFitzpatrickComponent()) return listOf(emoji)
    val members = (info.getVariations().map { it.emoji } + base).distinct()
    return members.sortedBy { member ->
        val toneIndex = SKIN_TONE_MODIFIERS.indexOfFirst { member.endsWith(it) }
        if (toneIndex == -1) 0 else toneIndex + 1
    }
}

/** Applies [picked] to [current]: toggles it off if it's already the exact reaction present
 * (double-tapping/tapping a message that already has your reaction removes it -- a committed
 * single-emoji action, not a browse-and-add picker); otherwise replaces any other member of
 * [picked]'s own family already present (so picking a different skin tone or heart color swaps
 * it in rather than leaving both), then adds it. */
fun toggledReactions(current: Set<String>, picked: String): Set<String> {
    if (picked in current) return current - picked
    val family = variantsFor(picked).toSet()
    return (current - family) + picked
}

/** Builds the add-reaction dialog's shortcut row from the raw most-recently-used list
 * ([AppSettings.getRecentReactionEmojis]): heart and thumbs-up are always pinned first, in that
 * order -- showing whichever variant of each was most recently used, or the family default if
 * neither has been used yet -- with the rest of the row filled from the remaining recents (falling
 * back to the legacy suggestion set to pad out to six when there isn't enough usage history yet).
 */
fun buildShortcutEmojis(recent: List<String>): List<String> {
    val heartSlot = recent.firstOrNull { isHeartFamily(it) } ?: HEART_DEFAULT
    val thumbsSlot = recent.firstOrNull { isThumbsUpFamily(it) } ?: THUMBS_UP_DEFAULT
    val others = recent.filterNot { isHeartFamily(it) || isThumbsUpFamily(it) }
    val fallback = listOf("👎", "😂", "😮", "😢")
    val filler = (others + fallback).distinct().take(4)
    return listOf(heartSlot, thumbsSlot) + filler
}
