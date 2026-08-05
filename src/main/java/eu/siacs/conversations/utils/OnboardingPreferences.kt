package eu.siacs.conversations.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** One-shot "have they seen this yet" flags for small in-app explainers — the edit/delete
 * bottom sheet shown the first time each action is used, and the reaction picker's blinking
 * "more" affordance. Mirrors UpdatePreferences' plain SharedPreferences-backed property pattern. */
class OnboardingPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)

    var hasSeenEditOnboarding: Boolean
        get() = prefs.getBoolean(KEY_SEEN_EDIT, false)
        set(value) = prefs.edit { putBoolean(KEY_SEEN_EDIT, value) }

    var hasSeenDeleteOnboarding: Boolean
        get() = prefs.getBoolean(KEY_SEEN_DELETE, false)
        set(value) = prefs.edit { putBoolean(KEY_SEEN_DELETE, value) }

    var hasOpenedMoreReactions: Boolean
        get() = prefs.getBoolean(KEY_OPENED_MORE_REACTIONS, false)
        set(value) = prefs.edit { putBoolean(KEY_OPENED_MORE_REACTIONS, value) }

    companion object {
        private const val KEY_SEEN_EDIT = "seen_edit_onboarding"
        private const val KEY_SEEN_DELETE = "seen_delete_onboarding"
        private const val KEY_OPENED_MORE_REACTIONS = "opened_more_reactions"
    }
}
