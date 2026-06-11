package eu.siacs.conversations.ui.util

import android.content.Context
import androidx.annotation.StringRes
import com.google.common.collect.ImmutableMap
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.MucOptions

class MucConfiguration private constructor(
    @JvmField @StringRes val title: Int,
    @JvmField val names: Array<String>,
    @JvmField val values: BooleanArray,
    internal val options: Array<Option>
) {

    fun toBundle(values: BooleanArray): Map<String, Any> {
        val builder = ImmutableMap.Builder<String, Any>()
        for (i in values.indices) {
            val option = options[i]
            builder.put(option.name, option.values[if (values[i]) 0 else 1])
        }
        builder.put("muc#roomconfig_persistentroom", true)
        return builder.buildOrThrow()
    }

    internal class Option private constructor(val name: String, val values: Array<Any>) {
        constructor(name: String) : this(name, arrayOf(true, false))
        constructor(name: String, on: String, off: String) : this(name, arrayOf(on, off))
    }

    companion object {
        @JvmStatic
        fun get(context: Context, mucOptions: MucOptions): MucConfiguration {
            return if (mucOptions.isPrivateAndNonAnonymous) {
                val names = arrayOf(
                    context.getString(R.string.allow_participants_to_edit_subject),
                    context.getString(R.string.allow_participants_to_invite_others)
                )
                val values = booleanArrayOf(
                    mucOptions.participantsCanChangeSubject(),
                    mucOptions.allowInvites()
                )
                val options = arrayOf(
                    Option("muc#roomconfig_changesubject"),
                    Option("muc#roomconfig_allowinvites")
                )
                MucConfiguration(R.string.conference_options, names, values, options)
            } else {
                val names = arrayOf(
                    context.getString(R.string.non_anonymous),
                    context.getString(R.string.allow_participants_to_edit_subject),
                    context.getString(R.string.moderated),
                    context.getString(R.string.allow_private_messages)
                )
                val values = booleanArrayOf(
                    mucOptions.nonanonymous(),
                    mucOptions.participantsCanChangeSubject(),
                    mucOptions.moderated(),
                    mucOptions.allowPmRaw()
                )
                val options = arrayOf(
                    Option("muc#roomconfig_whois", "anyone", "moderators"),
                    Option("muc#roomconfig_changesubject"),
                    Option("muc#roomconfig_moderatedroom"),
                    Option("muc#roomconfig_allowpm", "anyone", "moderators")
                )
                MucConfiguration(R.string.channel_options, names, values, options)
            }
        }

        @JvmStatic
        fun describe(context: Context, mucOptions: MucOptions): String {
            val builder = StringBuilder()
            if (mucOptions.isPrivateAndNonAnonymous) {
                if (mucOptions.participantsCanChangeSubject()) {
                    builder.append(context.getString(R.string.anyone_can_edit_subject))
                } else {
                    builder.append(context.getString(R.string.owners_can_edit_subject))
                }
                builder.append(' ')
                if (mucOptions.allowInvites()) {
                    builder.append(context.getString(R.string.anyone_can_invite_others))
                } else {
                    builder.append(context.getString(R.string.owners_can_invite_others))
                }
            } else {
                if (mucOptions.nonanonymous()) {
                    builder.append(context.getString(R.string.jabber_ids_are_visible_to_anyone))
                } else {
                    builder.append(context.getString(R.string.jabber_ids_are_visible_to_admins))
                }
                builder.append(' ')
                if (mucOptions.participantsCanChangeSubject()) {
                    builder.append(context.getString(R.string.anyone_can_edit_subject))
                } else {
                    builder.append(context.getString(R.string.admins_can_edit_subject))
                }
            }
            return builder.toString()
        }
    }
}
