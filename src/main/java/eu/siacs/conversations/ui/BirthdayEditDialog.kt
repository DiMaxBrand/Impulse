package eu.siacs.conversations.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
 * the top so it is always visible, and the content scrolls while the buttons stay put.
 *
 * - Year shared: the Material date picker as it is, calendar or typed (its own pencil). Typing
 *   follows the phone's language: month first on English phones, day first on Russian ones.
 *   The first weekday and the date order come from the same place, the phone's region.
 * - Year not shared: no year anywhere. A month header you can swipe through (tap it for all
 *   twelve months), and the days of that month; or (pencil) day and month typed, in the order
 *   the phone uses.
 *
 * The calendar is shrunk to the width the screen offers instead of being clipped or scrolled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BirthdayEditDialog(
    current: BirthdayDate?,
    onConfirm: (BirthdayDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val start = current ?: BirthdayDate(today.monthValue, today.dayOfMonth, null)
    var shareYear by remember { mutableStateOf(current?.year != null) }
    var month by remember { mutableIntStateOf(start.month) }
    var day by remember { mutableIntStateOf(start.day) }
    var typing by remember { mutableStateOf(false) }
    var monthListOpen by remember { mutableStateOf(false) }
    var direction by remember { mutableIntStateOf(1) }
    var dayText by remember { mutableStateOf(start.day.toString()) }
    var monthText by remember { mutableStateOf(start.month.toString()) }

    fun millisOf(year: Int, m: Int, d: Int): Long =
        LocalDate.of(year, m, minOf(d, YearMonth.of(year, m).lengthOfMonth()))
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = millisOf(start.year ?: (today.year - 30), start.month, start.day),
        yearRange = 1900..today.year,
    )

    fun picked(): LocalDate? =
        pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

    fun dayCount(m: Int) = YearMonth.of(NO_YEAR_PLACEHOLDER, m).lengthOfMonth()

    fun changeMonth(delta: Int) {
        direction = if (delta >= 0) 1 else -1
        month = (month - 1 + delta + 12) % 12 + 1
        day = minOf(day, dayCount(month))
    }

    val typedDay = dayText.toIntOrNull()
    val typedMonth = monthText.toIntOrNull()
    val typedValid = typedDay != null && typedMonth != null && typedMonth in 1..12 &&
        typedDay in 1..dayCount(typedMonth)
    // The order the phone writes day and month in ('d' before 'M' or after).
    val dayFirst = remember {
        val order = android.text.format.DateFormat.getDateFormatOrder(context)
        order.indexOf('d') < order.indexOf('M')
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
                                        // Carry the chosen day over to the calendar, with a plausible year.
                                        val m = if (typing && typedValid) typedMonth!! else month
                                        val d = if (typing && typedValid) typedDay!! else day
                                        pickerState.selectedDateMillis = millisOf(today.year - 30, m, d)
                                    } else {
                                        picked()?.let { month = it.monthValue; day = minOf(it.dayOfMonth, dayCount(it.monthValue)) }
                                        dayText = day.toString(); monthText = month.toString()
                                        // Collapsed by default: the month header, not the list of months.
                                        monthListOpen = false
                                        typing = false
                                    }
                                    shareYear = on
                                },
                            )
                        }
                        Text(
                            stringResource(R.string.birthday_explainer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        )
                    }
                    if (shareYear) {
                        ScaleToFitWidth(natural = 360.dp) {
                            DatePicker(state = pickerState, title = null, headline = null)
                        }
                    } else {
                        Column(Modifier.padding(horizontal = 24.dp)) {
                            NoYearHeader(
                                month = month,
                                typing = typing,
                                monthListOpen = monthListOpen,
                                onToggleList = { monthListOpen = !monthListOpen },
                                onPrev = { changeMonth(-1) },
                                onNext = { changeMonth(1) },
                                onToggleTyping = {
                                    if (typing) {
                                        if (typedValid) { month = typedMonth!!; day = typedDay!! }
                                    } else {
                                        dayText = day.toString(); monthText = month.toString()
                                    }
                                    typing = !typing
                                    monthListOpen = false
                                },
                            )
                            Spacer(Modifier.size(8.dp))
                            if (typing) {
                                val dayField: @Composable (Modifier) -> Unit = {
                                    NumberField(dayText, { dayText = it.take(2) }, R.string.birthday_day, !typedValid, it)
                                }
                                val monthField: @Composable (Modifier) -> Unit = {
                                    NumberField(monthText, { monthText = it.take(2) }, R.string.birthday_month, !typedValid, it)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (dayFirst) { dayField(Modifier.weight(1f)); monthField(Modifier.weight(1f)) }
                                    else { monthField(Modifier.weight(1f)); dayField(Modifier.weight(1f)) }
                                }
                            } else if (monthListOpen) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    for (m in 1..12) {
                                        FilterChip(
                                            selected = m == month,
                                            onClick = {
                                                direction = if (m >= month) 1 else -1
                                                month = m; day = minOf(day, dayCount(m)); monthListOpen = false
                                            },
                                            label = { Text(Month.of(m).getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault())) },
                                        )
                                    }
                                }
                            } else {
                                // Swipe sideways to change month; the days slide with it.
                                Box(
                                    Modifier.fillMaxWidth().pointerInput(month) {
                                        var total = 0f
                                        detectHorizontalDragGestures(
                                            onDragStart = { total = 0f },
                                            onDragEnd = {
                                                if (total < -80f) changeMonth(1) else if (total > 80f) changeMonth(-1)
                                            },
                                            onHorizontalDrag = { _, dx -> total += dx },
                                        )
                                    },
                                ) {
                                    AnimatedContent(
                                        targetState = month,
                                        transitionSpec = {
                                            val s = spring<androidx.compose.ui.unit.IntOffset>(stiffness = 500f, dampingRatio = 0.9f)
                                            (slideInHorizontally(s) { it * direction } + fadeIn()) togetherWith
                                                (slideOutHorizontally(s) { -it * direction } + fadeOut())
                                        },
                                        label = "birthdayMonth",
                                    ) { shownMonth ->
                                        DayGrid(shownMonth, day) { day = it }
                                    }
                                }
                            }
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
                        enabled = if (shareYear) picked() != null else (!typing || typedValid),
                        onClick = {
                            val result = when {
                                shareYear -> picked()?.let { BirthdayDate(it.monthValue, it.dayOfMonth, it.year) }
                                typing -> BirthdayDate(typedMonth!!, typedDay!!, null)
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

/**
 * Lays [content] out at its natural width and shrinks it, as a whole, when the screen offers
 * less. The Material calendar is a fixed 360dp wide: clipped on a narrower card, wrong to scroll
 * (sideways is how months change), so it is scaled down instead. Touches follow the scale.
 */
@Composable
private fun ScaleToFitWidth(natural: Dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val naturalPx = natural.roundToPx()
        val scale = (constraints.maxWidth.toFloat() / naturalPx).coerceAtMost(1f)
        val placeable = measurables.first().measure(
            Constraints(minWidth = naturalPx, maxWidth = naturalPx, maxHeight = Constraints.Infinity),
        )
        val width = constraints.maxWidth
        val height = (placeable.height * scale).toInt()
        layout(width, height) {
            val x = ((width - naturalPx * scale) / 2f).toInt()
            placeable.placeWithLayer(x, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/** "October", the pencil and the arrows: where the Material header has the year. Tapping the
 * month opens the list of all twelve; there is no year button. */
@Composable
private fun NoYearHeader(
    month: Int,
    typing: Boolean,
    monthListOpen: Boolean,
    onToggleList: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleTyping: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (!typing) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onToggleList)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(painterResource(R.drawable.ic_expand_more_24dp), contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp).size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            if (!monthListOpen) {
                IconButton(onClick = onPrev) {
                    Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = null)
                }
                IconButton(onClick = onNext) {
                    Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = null,
                        modifier = Modifier.rotate(180f))
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        IconButton(onClick = onToggleTyping) {
            Icon(
                painterResource(if (typing) R.drawable.ic_calendar_month_24dp else R.drawable.ic_edit_24dp),
                contentDescription = null,
            )
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

/** The days of [month], seven to a row (no year, so no weekdays), shrinking with the width. */
@Composable
private fun DayGrid(month: Int, day: Int, onDay: (Int) -> Unit) {
    val length = YearMonth.of(NO_YEAR_PLACEHOLDER, month).lengthOfMonth()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (week in 0 until 5) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 1..7) {
                    val d = week * 7 + i
                    val shown = d <= length
                    val selected = shown && d == day
                    Box(
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                            .then(Modifier.heightIn(min = 40.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (shown) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
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
    }
}
