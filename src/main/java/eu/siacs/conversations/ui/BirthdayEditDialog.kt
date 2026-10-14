package eu.siacs.conversations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.siacs.conversations.R
import eu.siacs.conversations.utils.BirthdayDate
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** A leap year, so that 29 February can be chosen when the year is not shared. */
private const val NO_YEAR_PLACEHOLDER = 2000

/**
 * Pick your own birthday. The switch at the top decides whether the year is shared; it sits at
 * the top (not under the calendar) so it is always visible, and the content scrolls while the
 * buttons stay put.
 *
 * - Year shared: the Material date picker, calendar or typed (the pencil), in the phone's format.
 * - Year not shared: no year anywhere. A month and day grid, or (pencil) just day and month typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BirthdayEditDialog(
    current: BirthdayDate?,
    onConfirm: (BirthdayDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    val start = current ?: BirthdayDate(today.monthValue, today.dayOfMonth, null)
    var shareYear by remember { mutableStateOf(current?.year != null) }
    var month by remember { mutableIntStateOf(start.month) }
    var day by remember { mutableIntStateOf(start.day) }
    var typing by remember { mutableStateOf(false) }
    var dayText by remember { mutableStateOf(start.day.toString()) }
    var monthText by remember { mutableStateOf(start.month.toString()) }

    fun millisOf(year: Int, m: Int, d: Int): Long =
        LocalDate.of(year, m, minOf(d, YearMonth.of(year, m).lengthOfMonth()))
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = millisOf(start.year ?: (today.year - 30), start.month, start.day),
        yearRange = 1900..today.year,
    )

    fun pickedFromCalendar(): LocalDate? =
        pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

    // Typed day and month, valid only if the date exists (29 February is allowed).
    val typedDay = dayText.toIntOrNull()
    val typedMonth = monthText.toIntOrNull()
    val typedValid = typedDay != null && typedMonth != null && typedMonth in 1..12 &&
        typedDay in 1..YearMonth.of(NO_YEAR_PLACEHOLDER, typedMonth).lengthOfMonth()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 640.dp),
        ) {
            Column {
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp).padding(top = 24.dp),
                ) {
                    Text(stringResource(R.string.birthday_dialog_title), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.size(12.dp))
                    // First thing, so it can never be pushed off screen.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.birthday_share_year), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = shareYear,
                            onCheckedChange = { on ->
                                if (on) {
                                    // Carry the chosen day over to the calendar, with a plausible year.
                                    val m = if (typing && typedValid) typedMonth!! else month
                                    val d = if (typing && typedValid) typedDay!! else day
                                    pickerState.selectedDateMillis = millisOf(today.year - 30, m, d)
                                } else {
                                    pickedFromCalendar()?.let { month = it.monthValue; day = it.dayOfMonth }
                                    dayText = day.toString(); monthText = month.toString()
                                }
                                shareYear = on
                            },
                        )
                    }
                    Text(
                        stringResource(R.string.birthday_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    if (shareYear) {
                        DatePicker(state = pickerState, title = null, headline = null)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { typing = !typing }) {
                                Icon(
                                    painterResource(if (typing) R.drawable.ic_calendar_month_24dp else R.drawable.ic_edit_24dp),
                                    contentDescription = null,
                                )
                            }
                        }
                        if (typing) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = dayText,
                                    onValueChange = { dayText = it.filter(Char::isDigit).take(2) },
                                    label = { Text(stringResource(R.string.birthday_day)) },
                                    isError = !typedValid,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = monthText,
                                    onValueChange = { monthText = it.filter(Char::isDigit).take(2) },
                                    label = { Text(stringResource(R.string.birthday_month)) },
                                    isError = !typedValid,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            MonthAndDayGrid(
                                month = month, day = day,
                                onMonth = { month = it; day = minOf(day, YearMonth.of(NO_YEAR_PLACEHOLDER, it).lengthOfMonth()) },
                                onDay = { day = it },
                            )
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (current != null) {
                        TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.birthday_remove)) }
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        enabled = shareYear || !typing || typedValid,
                        onClick = {
                            val result = if (shareYear) {
                                pickedFromCalendar()?.let { BirthdayDate(it.monthValue, it.dayOfMonth, it.year) }
                            } else if (typing) {
                                BirthdayDate(typedMonth!!, typedDay!!, null)
                            } else {
                                BirthdayDate(month, day, null)
                            }
                            if (result != null) onConfirm(result)
                        },
                    ) { Text(stringResource(R.string.confirm)) }
                }
            }
        }
    }
}

/** January to December as chips, then the days of that month: no year anywhere. */
@Composable
private fun MonthAndDayGrid(month: Int, day: Int, onMonth: (Int) -> Unit, onDay: (Int) -> Unit) {
    val locale = Locale.getDefault()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (m in 1..12) {
            FilterChip(
                selected = m == month,
                onClick = { onMonth(m) },
                label = { Text(Month.of(m).getDisplayName(TextStyle.SHORT_STANDALONE, locale)) },
            )
        }
    }
    Spacer(Modifier.size(16.dp))
    val length = YearMonth.of(NO_YEAR_PLACEHOLDER, month).lengthOfMonth()
    for (week in 0 until 5) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 1..7) {
                val d = week * 7 + i
                if (d > length) {
                    Spacer(Modifier.size(40.dp))
                } else {
                    val selected = d == day
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onDay(d) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.toString(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
