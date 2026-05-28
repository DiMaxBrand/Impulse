package eu.siacs.conversations.ui.util

import android.util.Rational

object Rationals {
    private val MIN = Rational(100, 239)
    private val MAX = Rational(239, 100)

    @JvmStatic
    fun clip(input: Rational): Rational = when {
        input.compareTo(MIN) < 0 -> MIN
        input.compareTo(MAX) > 0 -> MAX
        else -> input
    }
}
