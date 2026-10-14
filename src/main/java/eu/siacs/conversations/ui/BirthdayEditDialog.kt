package eu.siacs.conversations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
 * - Year shared: the Material calendar, or (the pencil) day, month and year typed into three
 *   labelled fields. Not the Material text field: it wants the date in the phone's order
 *   (month first on English phones), so a perfectly good 25/10/2013 was refused.
 * - Year not shared: no year anywhere. A month and day grid, or (pencil) day and month typed.
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
    var yearText by remember { mutableStateOf((start.year ?: (today.year - 30)).toString()) }

    fun millisOf(year: Int, m: Int, d: Int): Long =
        LocalDate.of(year, m, minOf(d, YearMonth.of(year, m).lengthOfMonth()))
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = millisOf(start.year ?: (today.year - 30), start.month, start.day),
        yearRange = 1900..today.year,
    )

    fun pickedFromCalendar(): LocalDate? =
        pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

    // What is typed counts only if that date exists (29 February needs a leap year when a year is
    // given, and is always fine without one).
    val typedDay = dayText.toIntOrNull()
    val typedMonth = monthText.toIntOrNull()
    val typedYear = yearText.toIntOrNull()
    val typedValid = typedDay != null && typedMonth != null && typedMonth in 1..12 &&
        (if (shareYear) {
            typedYear != null && typedYear in 1900..today.year &&
                typedDay in 1..YearMonth.of(typedYear, typedMonth).lengthOfMonth() &&
                !LocalDate.of(typedYear, typedMonth, typedDay).isAfter(today)
        } else {
            typedDay in 1..YearMonth.of(NO_YEAR_PLACEHOLDER, typedMonth).lengthOfMonth()
        })

    fun syncTypedFromCalendar() {
        pickedFromCalendar()?.let {
            dayText = it.dayOfMonth.toString(); monthText = it.monthValue.toString(); yearText = it.year.toString()
        }
    }

    fun syncCalendarFromTyped() {
        if (typedValid && shareYear) pickerState.selectedDateMillis = millisOf(typedYear!!, typedMonth!!, typedDay!!)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(max = 680.dp),
        ) {
            Column {
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 24.dp),
                ) {
                    Column(Modifier.padding(horizontal = 24.dp)) {
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
                                        // Carry the chosen day over, with a plausible year.
                                        val m = if (typing && typedValid) typedMonth!! else month
                                        val d = if (typing && typedValid) typedDay!! else day
                                        val y = typedYear?.takeIf { it in 1900..today.year } ?: (today.year - 30)
                                        pickerState.selectedDateMillis = millisOf(y, m, d)
                                        dayText = d.toString(); monthText = m.toString(); yearText = y.toString()
                                    } else {
                                        val picked = pickedFromCalendar()
                                        val m = if (typing && typedValid) typedMonth!! else picked?.monthValue ?: month
                                        val d = if (typing && typedValid) typedDay!! else picked?.dayOfMonth ?: day
                                        month = m; day = minOf(d, YearMonth.of(NO_YEAR_PLACEHOLDER, m).lengthOfMonth())
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
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                        // One pencil for both modes: calendar <-> typing.
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = {
                                if (typing) {
                                    if (shareYear) syncCalendarFromTyped()
                                    else if (typedValid) { month = typedMonth!!; day = typedDay!! }
                                } else {
                                    if (shareYear) syncTypedFromCalendar()
                                    else { dayText = day.toString(); monthText = month.toString() }
                                }
                                typing = !typing
                            }) {
                                Icon(
                                    painterResource(if (typing) R.drawable.ic_calendar_month_24dp else R.drawable.ic_edit_24dp),
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                    if (typing) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            NumberField(dayText, { dayText = it.take(2) }, R.string.birthday_day, !typedValid, Modifier.weight(1f))
                            NumberField(monthText, { monthText = it.take(2) }, R.string.birthday_month, !typedValid, Modifier.weight(1f))
                            if (shareYear) {
                                NumberField(yearText, { yearText = it.take(4) }, R.string.birthday_year, !typedValid, Modifier.weight(1.3f))
                            }
                        }
                    } else if (shareYear) {
                        // The Material calendar is a fixed 360dp wide: no side padding around it,
                        // and on a narrower screen it scrolls sideways instead of losing Saturday.
                        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            DatePicker(state = pickerState, title = null, headline = null, showModeToggle = false)
                        }
                    } else {
                        Column(Modifier.padding(horizontal = 24.dp)) {
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
                        enabled = !typing || typedValid,
                        onClick = {
                            val result = when {
                                typing -> BirthdayDate(typedMonth!!, typedDay!!, if (shareYear) typedYear else null)
                                shareYear -> pickedFromCalendar()?.let { BirthdayDate(it.monthValue, it.dayOfMonth, it.year) }
                                else -> BirthdayDate(month, day, null)
                            }
                            if (result != null) onConfirm(result)
                        },
                    ) { Text(stringResource(R.string.confirm)) }
                }
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: Int, error: Boolean, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit)) },
        label = { Text(stringResource(label)) },
        isError = error,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
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
