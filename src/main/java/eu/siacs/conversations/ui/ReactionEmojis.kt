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
 * Fitzpatrick skin-tone data (`Emoji.getVariations`) for every other emoji that has skin-tone
 * variants -- not a hardcoded per-emoji list, so this covers the entire shortcut row (thumbs
 * up/down, clapping hands, praying hands, flexed biceps, waving hand, ...) automatically,
 * including anything a future change to the shortcut set adds. Neutral/base first, then light to
 * dark. Emoji with no variants of either kind return just themselves -- long-press is then a
 * no-op (no popup shown).
 *
 * Deliberately does *not* gate on `Emoji.hasFitzpatrickComponent()` -- despite the name, that
 * flag means "this exact string already contains a modifier", not "this emoji has tone variants".
 * Querying the plain neutral base (e.g. "👍") returns `false` for it even though `getVariations()`
 * on that same lookup correctly returns all 5 tones -- gating on the flag was the actual bug
 * behind "long-press does nothing on anything except heart": every other emoji has real variation
 * data, it was just never reached.
 */
fun variantsFor(emoji: String): List<String> {
    if (isHeartFamily(emoji)) return HEART_VARIANTS
    val base = stripSkinTone(emoji)
    val info = EmojiManager.getEmoji(base).orElse(null) ?: return listOf(emoji)
    val variations = info.getVariations()
    if (variations.isEmpty()) return listOf(emoji)
    val members = (variations.map { it.emoji } + base).distinct()
    return members.sortedBy { member ->
        val toneIndex = SKIN_TONE_MODIFIERS.indexOfFirst { member.endsWith(it) }
        if (toneIndex == -1) 0 else toneIndex + 1
    }
}

/** Applies [picked] to [current]: toggles it off if it's already present, otherwise adds it
 * alongside whatever's already there. Deliberately a plain toggle, not "replace any other member
 * of picked's family" -- picking a different skin tone or heart color is a *second* reaction, not
 * a correction of the first; e.g. green then blue means both are now your reactions, not just
 * blue. (An earlier version replaced within the family; that was wrong.) */
fun toggledReactions(current: Set<String>, picked: String): Set<String> {
    return if (picked in current) current - picked else current + picked
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
    val fallback = listOf("👎", "😂", "😮", "😁")
    val filler = (others + fallback).distinct().take(4)
    return listOf(heartSlot, thumbsSlot) + filler
}
