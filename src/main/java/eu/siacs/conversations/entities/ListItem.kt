package eu.siacs.conversations.entities

import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import com.google.common.base.Strings
import com.google.common.collect.Iterables
import eu.siacs.conversations.services.AvatarService
import eu.siacs.conversations.xmpp.Jid
import im.conversations.android.model.DynamicTag
import java.util.Locale

interface ListItem : Comparable<ListItem>, AvatarService.Avatar {

    override fun getDisplayName(): String

    fun getAddress(): Jid

    fun getTags(): List<DynamicTag>

    fun match(needle: String?): Boolean {
        if (Strings.isNullOrEmpty(needle)) {
            return true
        }
        val parts =
            Splitter.on(CharMatcher.whitespace())
                .omitEmptyStrings()
                .trimResults()
                .splitToList(needle!!.lowercase(Locale.ROOT))
        return if (parts.size == 1) {
            matchInItem(Iterables.getOnlyElement(parts))
        } else {
            parts.all { matchInItem(it) }
        }
    }

    override fun compareTo(another: ListItem): Int =
        this.getDisplayName().compareTo(another.getDisplayName(), ignoreCase = true)

    private fun matchInItem(needle: String): Boolean =
        getAddress().toString().contains(needle) ||
            getDisplayName().lowercase(Locale.US).contains(needle) ||
            matchInTag(needle)

    private fun matchInTag(needle: String): Boolean {
        for (tag in getTags()) {
            if (tag is DynamicTag.RosterGroup &&
                Strings.nullToEmpty(tag.name)
                    .lowercase(Locale.getDefault())
                    .contains(needle)
            ) {
                return true
            }
            // TODO match for hat and availability
        }
        return false
    }
}
