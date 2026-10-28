package eu.siacs.conversations.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import eu.siacs.conversations.R
import eu.siacs.conversations.utils.BirthdayDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "15 October" or "15 October 1950", in the phone's language. */
fun BirthdayDate.format(): String {
    val shown = LocalDate.of(year ?: 2000, month, day)
    val pattern = if (year == null) "d MMMM" else "d MMMM yyyy"
    return shown.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

/** What the contact details and the hub show: the date, plus the age when the year is shared. */
fun BirthdayDate.describe(context: Context, today: LocalDate = LocalDate.now()): String {
    val age = ageOn(today)
    return if (age == null) format()
    else context.resources.getQuantityString(R.plurals.birthday_with_age, age, format(), age)
}

/**
 * Shown once after the update that brought birthdays: "New: you can set your birthday". "Set it
 * up" opens the Settings screen straight on the birthday picker; either button ends it for good.
 */
class BirthdayPromptDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        // Only the card drawn below shows, not the platform dialog's own frame.
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // Full screen width (the default dialog width is much narrower than this card needs).
        dialog?.window?.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ImpulseExpressiveTheme {
                    // Wide and tall: the window is stretched to the screen width in onStart, the
                    // card has generous padding, and the two buttons sit one under the other at full
                    // width (side by side they did not fit), as large pills.
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Surface(
                            shape = RoundedCornerShape(44.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 6.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(horizontal = 28.dp, vertical = 36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(104.dp),
                                ) { Box(contentAlignment = Alignment.Center) { Text("🎂", fontSize = 52.sp) } }
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    stringResource(R.string.birthday_prompt_title),
                                    style = MaterialTheme.typography.headlineMedium,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.birthday_prompt_text),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(32.dp))
                                Button(
                                    onClick = { finish(open = true) },
                                    shape = CircleShape,
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                ) { Text(stringResource(R.string.birthday_set_up), style = MaterialTheme.typography.titleMedium) }
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = { finish(open = false) },
                                    shape = CircleShape,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                ) { Text(stringResource(R.string.birthday_not_now), style = MaterialTheme.typography.titleMedium) }
                            }
                        }
                    }
                }
            }
        }

    private fun finish(open: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit().putBoolean(PREF_SEEN, true).apply()
        val context = requireContext()
        dismissAllowingStateLoss()
        if (open) {
            context.startActivity(
                Intent(context, AccountsHubActivity::class.java)
                    .putExtra(AccountsHubActivity.EXTRA_OPEN_BIRTHDAY, true)
            )
        }
    }

    companion object {
        const val TAG = "BirthdayPrompt"
        const val PREF_SEEN = "birthday_prompt_seen"

        /** Once, and only for someone who has an account to put the birthday on. */
        @JvmStatic
        fun maybeShow(activity: XmppActivity) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
            if (prefs.getBoolean(PREF_SEEN, false)) return
            // From the third day on, and not on a launch that already showed another first-run prompt.
            if (!SetupSchedule.birthdayPromptDue(activity)) return
            val service = activity.xmppConnectionService ?: return
            if (service.accounts.none { it.isEnabled }) return
            activity.window.decorView.postDelayed({
                if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                val manager = (activity as FragmentActivity).supportFragmentManager
                // Never on top of another dialog, and not while the birthday greeting is up.
                if (manager.isStateSaved || manager.fragments.any { it is DialogFragment }) return@postDelayed
                if (!SetupSchedule.claim()) return@postDelayed
                BirthdayPromptDialogFragment().show(manager, TAG)
            }, 3000)
        }
    }
}
