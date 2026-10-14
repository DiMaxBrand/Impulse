package eu.siacs.conversations.utils

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/** A birthday as people share it: month and day always, the year only if they chose to. */
data class BirthdayDate(val month: Int, val day: Int, val year: Int?) {

    /** The wire/storage form: `1950-10-15`, or `--10-15` without a year. */
    fun encode(): String =
        if (year == null) "--%02d-%02d".format(month, day) else "%04d-%02d-%02d".format(year, month, day)

    fun isOn(date: LocalDate): Boolean = month == date.monthValue && day == date.dayOfMonth

    /** Age on [date] (the birthday itself counts), or null when the year is not shared. */
    fun ageOn(date: LocalDate): Int? {
        val y = year ?: return null
        var age = date.year - y
        if (date.monthValue < month || (date.monthValue == month && date.dayOfMonth < day)) age--
        return if (age >= 0) age else null
    }

    companion object {
        fun parse(text: String?): BirthdayDate? {
            val value = text?.trim() ?: return null
            return try {
                if (value.startsWith("--")) {
                    val parts = value.removePrefix("--").split('-')
                    valid(parts[0].toInt(), parts[1].toInt(), null)
                } else {
                    val parts = value.split('-')
                    valid(parts[1].toInt(), parts[2].toInt(), parts[0].toInt())
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun valid(month: Int, day: Int, year: Int?): BirthdayDate? {
            if (month !in 1..12 || day !in 1..31) return null
            // 29 February is fine without a year; with one it has to exist.
            if (year != null) {
                try { LocalDate.of(year, month, day) } catch (_: Exception) { return null }
            }
            return BirthdayDate(month, day, year)
        }
    }
}

/**
 * Where birthdays are kept on the phone: our own (per account) and our contacts' (as they arrive
 * over PEP). Plain SharedPreferences, keyed `accountUuid|bareJid`. The server re-sends the last
 * published value whenever we connect, so this is only a cache that keeps the banner and the
 * contact details working while offline.
 */
class BirthdayStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("birthdays", Context.MODE_PRIVATE)

    fun get(accountUuid: String, bareJid: String): BirthdayDate? =
        BirthdayDate.parse(prefs.getString(key(accountUuid, bareJid), null))

    fun set(accountUuid: String, bareJid: String, birthday: BirthdayDate?) {
        prefs.edit {
            if (birthday == null) remove(key(accountUuid, bareJid))
            else putString(key(accountUuid, bareJid), birthday.encode())
        }
    }

    private fun key(accountUuid: String, bareJid: String) = "$accountUuid|${bareJid.lowercase()}"
}
