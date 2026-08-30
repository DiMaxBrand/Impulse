package eu.siacs.conversations.utils

import android.content.Context
import eu.siacs.conversations.entities.Contact

/**
 * Per-contact manual override for [NameGenderGuesser] — a one-time fix for any contact the
 * heuristic gets wrong, rather than chasing more exceptions into the guesser itself. Purely
 * local UI preference (not synced anywhere), so plain SharedPreferences is enough; doesn't
 * warrant a database column or migration.
 */
object ContactGenderOverride {
    private const val PREFS_NAME = "contact_gender_override"
    private const val MASCULINE = "masculine"
    private const val FEMININE = "feminine"

    private fun key(contact: Contact): String {
        val accountUuid = contact.getAccount()?.getUuid() ?: ""
        return "$accountUuid#${contact.getAddress()}"
    }

    fun get(context: Context, contact: Contact): NameGenderGuesser.Gender {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString(key(contact), null)) {
            MASCULINE -> NameGenderGuesser.Gender.MASCULINE
            FEMININE -> NameGenderGuesser.Gender.FEMININE
            else -> NameGenderGuesser.Gender.UNKNOWN
        }
    }

    fun set(context: Context, contact: Contact, gender: NameGenderGuesser.Gender) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        when (gender) {
            NameGenderGuesser.Gender.MASCULINE -> editor.putString(key(contact), MASCULINE)
            NameGenderGuesser.Gender.FEMININE -> editor.putString(key(contact), FEMININE)
            NameGenderGuesser.Gender.UNKNOWN -> editor.remove(key(contact))
        }
        editor.apply()
    }
}
