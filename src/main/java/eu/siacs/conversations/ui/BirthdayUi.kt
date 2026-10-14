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

/** Pick your own birthday: a date, and a switch for whether the year is shared. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BirthdayEditDialog(
    current: BirthdayDate?,
    onConfirm: (BirthdayDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val thisYear = LocalDate.now().year
    val initial = current ?: BirthdayDate(LocalDate.now().monthValue, LocalDate.now().dayOfMonth, null)
    val initialDate = LocalDate.of(initial.year ?: 2000, initial.month, initial.day)
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = 1900..thisYear,
    )
    var shareYear by remember { mutableStateOf(current?.year != null) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                val picked = pickerState.selectedDateMillis?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                }
                onConfirm(picked?.let { BirthdayDate(it.monthValue, it.dayOfMonth, if (shareYear) it.year else null) })
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.birthday_remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    ) {
        Column {
            DatePicker(state = pickerState, title = {
                Text(stringResource(R.string.birthday_dialog_title),
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp), style = MaterialTheme.typography.titleMedium)
            })
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.birthday_share_year), Modifier.weight(1f))
                Switch(checked = shareYear, onCheckedChange = { shareYear = it })
            }
            Text(
                stringResource(R.string.birthday_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )
        }
    }
}

/**
 * Shown once after the update that brought birthdays: "New: you can set your birthday". "Set it
 * up" opens the Settings screen straight on the birthday picker; either button ends it for good.
 */
class BirthdayPromptDialogFragment : DialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ImpulseExpressiveTheme {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 6.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(72.dp),
                                ) { Box(contentAlignment = Alignment.Center) { Text("🎂", fontSize = 36.sp) } }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.birthday_prompt_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.birthday_prompt_text),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(24.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                                    TextButton(onClick = { finish(open = false) }) {
                                        Text(stringResource(R.string.birthday_not_now))
                                    }
                                    Button(onClick = { finish(open = true) }) {
                                        Text(stringResource(R.string.birthday_set_up))
                                    }
                                }
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
            val service = activity.xmppConnectionService ?: return
            if (service.accounts.none { it.isEnabled }) return
            activity.window.decorView.postDelayed({
                if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                val manager = (activity as FragmentActivity).supportFragmentManager
                // Never on top of another dialog, and not while the birthday greeting is up.
                if (manager.isStateSaved || manager.fragments.any { it is DialogFragment }) return@postDelayed
                BirthdayPromptDialogFragment().show(manager, TAG)
            }, 3000)
        }
    }
}
