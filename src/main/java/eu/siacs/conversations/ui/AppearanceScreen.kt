package eu.siacs.conversations.ui

import android.content.SharedPreferences
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.siacs.conversations.R

/** The preference store behind the Appearance screen: every key is the one the rest of the app
 * already reads (old Interface screen) or an [AppearanceState] key. */
internal class AppearanceSettings(private val prefs: SharedPreferences, private val res: android.content.res.Resources) {
    inner class Bool(val key: String, default: Boolean) {
        var value by mutableStateOf(prefs.getBoolean(key, default))
            private set
        fun set(v: Boolean) { value = v; prefs.edit().putBoolean(key, v).apply() }
    }
    inner class Str(val key: String, default: String) {
        var value by mutableStateOf(prefs.getString(key, default) ?: default)
            private set
        fun set(v: String) { value = v; prefs.edit().putString(key, v).apply() }
    }
    inner class Flt(val key: String, default: Float) {
        var value by mutableFloatStateOf(prefs.getFloat(key, default))
            private set
        fun set(v: Float) { value = v; prefs.edit().putFloat(key, v).apply() }
    }
    inner class Num(val key: String, default: Int) {
        var value by mutableIntStateOf(prefs.getInt(key, default))
            private set
        fun set(v: Int) { value = v; prefs.edit().putInt(key, v).apply() }
    }

    private fun b(id: Int) = res.getBoolean(id)

    val dynamicColors = Bool("dynamic_colors", b(R.bool.dynamic_colors))
    val accentSeed = Num("accent_seed", eu.siacs.conversations.AppSettings.DEFAULT_ACCENT_SEED)
    val theme = Str("theme", res.getString(R.string.theme))
    val textScale = Flt(AppearanceState.TEXT_SCALE, 1f)
    val datePills = Bool(AppearanceState.DATE_PILLS, true)
    val background = Str(AppearanceState.CHAT_BACKGROUND, "plain")
    val backgroundDim = Num(AppearanceState.CHAT_BACKGROUND_DIM, 30)
    val backgroundImage = Str(AppearanceState.CHAT_BACKGROUND_IMAGE, "")
    val animations = Str(AppearanceState.ANIMATION_LEVEL, "full")
    val haptics = Bool(AppearanceState.HAPTICS, true)

    val colorfulBubbles = Bool("use_green_background", b(R.bool.use_green_background))
    val alignStart = Bool("align_start", b(R.bool.align_start))
    val avatars11 = Bool("show_avatars", b(R.bool.show_avatars))
    val avatarsAccounts = Bool("show_avatars_accounts", b(R.bool.show_avatars_accounts))

    val dynamicTags = Bool("show_dynamic_tags", b(R.bool.show_dynamic_tags))
    val allowScreenshots = Bool("allow_screenshots", b(R.bool.allow_screenshots))
    val quickAction = Str("quick_action", res.getString(R.string.quick_action))
    val scrollToBottom = Bool("scroll_to_bottom", b(R.bool.scroll_to_bottom))
    val startSearching = Bool("start_searching", b(R.bool.start_searching))
    val displayEnterKey = Bool("display_enter_key", b(R.bool.display_enter_key))
    val enterIsSend = Bool("enter_is_send", b(R.bool.enter_is_send))
}

internal val ACCENT_PALETTE = listOf(
    0xFF1B9E34.toInt(), 0xFF00897B.toInt(), 0xFF0288D1.toInt(), 0xFF3F51B5.toInt(),
    0xFF7E57C2.toInt(), 0xFFC2185B.toInt(), 0xFFD32F2F.toInt(), 0xFFE64A19.toInt(),
    0xFFF9A825.toInt(), 0xFF7CB342.toInt(), 0xFF6D4C41.toInt(), 0xFF546E7A.toInt(),
)

private val QUICK_ACTION_VALUES = listOf("none", "recent", "RECORD_VOICE", "TAKE_PHOTO", "RECORD_VIDEO", "CHOOSE_PICTURE", "SEND_LOCATION")
private val QUICK_ACTION_LABELS = listOf(
    R.string.none, R.string.recently_used, R.string.attachment_choice_recording,
    R.string.attachment_choice_camera, R.string.attachment_choice_video,
    R.string.attachment_choice_gallery, R.string.attachment_choice_location,
)

private val springBounds = BoundsTransform { _, _ -> spring(stiffness = 380f, dampingRatio = 0.8f) }
private val quickFade = spring<Float>(stiffness = 1600f, dampingRatio = 1.0f)

internal enum class AppearancePage { MAIN, BUBBLES }

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun AppearanceScreen(
    settings: AppearanceSettings,
    page: AppearancePage,
    dynamicAvailable: Boolean,
    onThemeChosen: (String) -> Unit,
    onDynamicChanged: (Boolean) -> Unit,
    onAccentChanged: (Int) -> Unit,
    onPickBackgroundImage: () -> Unit,
    onOpenQuickReactions: () -> Unit,
    onScreenshotsChanged: () -> Unit,
    onOpenBubbles: () -> Unit,
) {
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (targetState == AppearancePage.BUBBLES) {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
            }
        },
        label = "appearance_page",
    ) { target ->
        if (target == AppearancePage.BUBBLES) {
            BubblesPage(settings)
        } else {
            MainPage(
                settings, dynamicAvailable, onThemeChosen, onDynamicChanged, onAccentChanged,
                onPickBackgroundImage, onOpenQuickReactions, onScreenshotsChanged, onOpenBubbles,
            )
        }
    }
}

// ─── Building blocks ─────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun rowColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun RowIcon(id: Int) {
    Icon(painterResource(id), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(
    position: GroupPosition,
    icon: Int,
    title: String,
    summary: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    ExpressiveGroupRow(position) {
        ListItem(
            leadingContent = { RowIcon(icon) },
            headlineContent = { Text(title) },
            supportingContent = summary?.let { { Text(it) } },
            trailingContent = { Switch(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled) },
            modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
            colors = rowColors(),
        )
    }
}

@Composable
private fun NavRow(position: GroupPosition, icon: Int, title: String, summary: String?, onClick: () -> Unit) {
    ExpressiveGroupRow(position) {
        ListItem(
            leadingContent = { RowIcon(icon) },
            headlineContent = { Text(title) },
            supportingContent = summary?.let { { Text(it) } },
            modifier = Modifier.clickable(onClick = onClick),
            colors = rowColors(),
        )
    }
}

/** A row that grows into a card: [hidden] while its card is open (a spacer keeps its height). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.PickerSource(
    key: String,
    hidden: Boolean,
    position: GroupPosition,
    icon: Int,
    title: String,
    value: String?,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    var heightPx by remember { mutableIntStateOf(0) }
    Box {
        AnimatedVisibility(visible = !hidden, enter = EnterTransition.None, exit = ExitTransition.None) {
            ExpressiveGroupRow(
                position,
                modifier = Modifier
                    .onSizeChanged { heightPx = it.height }
                    .sharedBounds(
                        rememberSharedContentState(key),
                        animatedVisibilityScope = this@AnimatedVisibility,
                        enter = fadeIn(quickFade),
                        exit = fadeOut(quickFade),
                        boundsTransform = springBounds,
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    ),
            ) {
                ListItem(
                    leadingContent = { RowIcon(icon) },
                    headlineContent = { Text(title) },
                    supportingContent = value?.let { { Text(it, color = MaterialTheme.colorScheme.primary) } },
                    trailingContent = { RowIcon(R.drawable.ic_expand_more_24dp) },
                    modifier = Modifier.clickable(onClick = onClick),
                    colors = rowColors(),
                )
            }
        }
        if (hidden) Spacer(Modifier.height(with(density) { heightPx.toDp() }))
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.PickerCard(
    key: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .sharedBounds(
                        rememberSharedContentState(key),
                        animatedVisibilityScope = this@AnimatedVisibility,
                        enter = fadeIn(quickFade),
                        exit = fadeOut(quickFade),
                        boundsTransform = springBounds,
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    )
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            ) {
                content()
            }
        }
    }
}

// ─── Main page ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun MainPage(
    s: AppearanceSettings,
    dynamicAvailable: Boolean,
    onThemeChosen: (String) -> Unit,
    onDynamicChanged: (Boolean) -> Unit,
    onAccentChanged: (Int) -> Unit,
    onPickBackgroundImage: () -> Unit,
    onOpenQuickReactions: () -> Unit,
    onScreenshotsChanged: () -> Unit,
    onOpenBubbles: () -> Unit,
) {
    var openPicker by remember { mutableStateOf<String?>(null) }
    val scrim by animateFloatAsState(
        targetValue = if (openPicker != null) 0.32f else 0f,
        animationSpec = spring(stiffness = 1600f, dampingRatio = 1.0f),
        label = "appearance_scrim",
    )
    BackHandler(enabled = openPicker != null) { openPicker = null }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // ── Colors ──
                SectionTitle(stringResource(R.string.appearance_section_colors))
                val dynamicOn = s.dynamicColors.value && dynamicAvailable
                if (dynamicAvailable) {
                    SwitchRow(
                        GroupPosition.TOP, R.drawable.ic_palette_24dp,
                        stringResource(R.string.pref_dynamic_colors),
                        stringResource(R.string.pref_dynamic_colors_summary),
                        s.dynamicColors.value,
                    ) { s.dynamicColors.set(it); onDynamicChanged(it) }
                }
                AnimatedVisibility(visible = !dynamicOn, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    PickerSource(
                        "accent", openPicker == "accent",
                        if (dynamicAvailable) GroupPosition.BOTTOM else GroupPosition.SINGLE,
                        R.drawable.ic_colors_24dp,
                        stringResource(R.string.appearance_accent),
                        stringResource(R.string.appearance_accent_summary),
                    ) { openPicker = "accent" }
                }
                Spacer(Modifier.height(6.dp))
                ThemeChips(s.theme.value, onThemeChosen)

                // ── Chat ──
                SectionTitle(stringResource(R.string.appearance_section_chat))
                TextSizeCard(s)
                Spacer(Modifier.height(2.dp))
                NavRow(
                    GroupPosition.TOP, R.drawable.ic_forum_24dp,
                    stringResource(R.string.pref_chat_bubbles),
                    stringResource(R.string.pref_chat_bubbles_summary), onOpenBubbles,
                )
                SwitchRow(
                    GroupPosition.MIDDLE, R.drawable.ic_calendar_month_24dp,
                    stringResource(R.string.appearance_date_pills),
                    stringResource(R.string.appearance_date_pills_summary),
                    s.datePills.value,
                ) { s.datePills.set(it) }
                SwitchRow(
                    GroupPosition.BOTTOM, R.drawable.ic_label_24dp,
                    stringResource(R.string.pref_show_dynamic_tags),
                    stringResource(R.string.pref_show_dynamic_tags_summary),
                    s.dynamicTags.value,
                ) { s.dynamicTags.set(it) }
                Spacer(Modifier.height(6.dp))
                BackgroundCard(s, onPickBackgroundImage)

                // ── Motion and feedback ──
                SectionTitle(stringResource(R.string.appearance_section_motion))
                AnimationLevelCard(s)
                Spacer(Modifier.height(2.dp))
                SwitchRow(
                    GroupPosition.SINGLE, R.drawable.ic_vibration_24dp,
                    stringResource(R.string.appearance_haptics),
                    stringResource(R.string.appearance_haptics_summary),
                    s.haptics.value,
                ) { s.haptics.set(it) }

                // ── Input ──
                SectionTitle(stringResource(R.string.pref_input_options))
                val quickIndex = QUICK_ACTION_VALUES.indexOf(s.quickAction.value).coerceAtLeast(0)
                PickerSource(
                    "quick_action", openPicker == "quick_action", GroupPosition.TOP,
                    R.drawable.ic_send_time_extension_24dp,
                    stringResource(R.string.pref_quick_action),
                    stringResource(QUICK_ACTION_LABELS[quickIndex]),
                ) { openPicker = "quick_action" }
                SwitchRow(
                    GroupPosition.MIDDLE, R.drawable.ic_vertical_align_bottom_24dp,
                    stringResource(R.string.pref_scroll_to_bottom),
                    stringResource(R.string.pref_scroll_to_bottom_summary),
                    s.scrollToBottom.value,
                ) { s.scrollToBottom.set(it) }
                SwitchRow(
                    GroupPosition.MIDDLE, R.drawable.ic_search_24dp,
                    stringResource(R.string.pref_start_search),
                    stringResource(R.string.pref_start_search_summary),
                    s.startSearching.value,
                ) { s.startSearching.set(it) }
                NavRow(
                    GroupPosition.BOTTOM, R.drawable.ic_add_reaction_24dp,
                    stringResource(R.string.pref_quick_reactions),
                    stringResource(R.string.pref_quick_reactions_summary), onOpenQuickReactions,
                )

                SectionTitle(stringResource(R.string.pref_keyboard_options))
                SwitchRow(
                    GroupPosition.TOP, R.drawable.ic_keyboard_return_24dp,
                    stringResource(R.string.pref_display_enter_key),
                    stringResource(R.string.pref_display_enter_key_summary),
                    s.displayEnterKey.value,
                ) { s.displayEnterKey.set(it) }
                SwitchRow(
                    GroupPosition.BOTTOM, R.drawable.ic_send_24dp,
                    stringResource(R.string.pref_enter_is_send),
                    stringResource(R.string.pref_enter_is_send_summary),
                    s.enterIsSend.value,
                ) { s.enterIsSend.set(it) }

                SectionTitle(stringResource(R.string.pref_category_operating_system))
                SwitchRow(
                    GroupPosition.SINGLE, R.drawable.ic_screenshot_24dp,
                    stringResource(R.string.pref_allow_screenshots),
                    stringResource(R.string.pref_allow_screenshots_summary),
                    s.allowScreenshots.value,
                ) { s.allowScreenshots.set(it); onScreenshotsChanged() }
                Spacer(Modifier.height(24.dp))
            }

            if (scrim > 0f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
            }

            PickerCard("accent", openPicker == "accent", { openPicker = null }) {
                AccentPickerContent(s.accentSeed.value) { seed ->
                    s.accentSeed.set(seed)
                    openPicker = null
                    onAccentChanged(seed)
                }
            }
            PickerCard("quick_action", openPicker == "quick_action", { openPicker = null }) {
                Column(Modifier.padding(vertical = 16.dp)) {
                    Text(
                        stringResource(R.string.choose_quick_action),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    QUICK_ACTION_VALUES.forEachIndexed { i, value ->
                        ListItem(
                            leadingContent = { RadioButton(selected = s.quickAction.value == value, onClick = null) },
                            headlineContent = { Text(stringResource(QUICK_ACTION_LABELS[i])) },
                            modifier = Modifier.clickable {
                                s.quickAction.set(value)
                                openPicker = null
                            },
                            colors = rowColors(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccentPickerContent(selected: Int, onPick: (Int) -> Unit) {
    Column(Modifier.padding(24.dp)) {
        Text(stringResource(R.string.appearance_accent), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ACCENT_PALETTE.forEach { color ->
                val isSelected = color == selected
                // Each swatch shows what the generated primary container/primary pair looks like.
                val scheme = remember(color) { seedColorScheme(color, false) }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(scheme.primaryContainer)
                        .border(if (isSelected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { onPick(color) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(scheme.primary))
                }
            }
        }
    }
}

// ─── Theme chips ─────────────────────────────────────────────────────────────

@Composable
private fun ThemeChips(current: String, onChosen: (String) -> Unit) {
    val context = LocalContext.current
    val light = remember { dynamicOrStaticColorScheme(context, false) }
    val dark = remember { dynamicOrStaticColorScheme(context, true) }
    val options = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add("automatic")
        add("light"); add("dark")
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { value ->
            val selected = current == value
            val border by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "theme_border")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(2.dp, border, RoundedCornerShape(20.dp))
                    .clickable { onChosen(value) }
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp))) {
                    when (value) {
                        "light" -> MiniChat(light, Modifier.weight(1f))
                        "dark" -> MiniChat(dark, Modifier.weight(1f))
                        else -> { MiniChat(light, Modifier.weight(1f)); MiniChat(dark, Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(when (value) {
                            "light" -> R.string.pref_theme_light
                            "dark" -> R.string.pref_theme_dark
                            else -> R.string.pref_theme_automatic
                        }),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** A tiny stand-in for a chat in the given scheme: top bar, one incoming and one outgoing bubble. */
@Composable
private fun MiniChat(scheme: androidx.compose.material3.ColorScheme, modifier: Modifier) {
    Column(modifier.fillMaxSize().background(scheme.surface)) {
        Box(Modifier.fillMaxWidth().height(10.dp).background(scheme.surfaceContainer))
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(26.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(scheme.surfaceContainerHigh))
            Box(Modifier.align(Alignment.End).width(30.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(scheme.primaryContainer))
            Box(Modifier.width(20.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(scheme.surfaceContainerHigh))
        }
    }
}

// ─── Text size ───────────────────────────────────────────────────────────────

@Composable
private fun TextSizeCard(s: AppearanceSettings) {
    ExpressiveGroupRow(GroupPosition.SINGLE) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(R.drawable.ic_format_size_24dp)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.appearance_text_size), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${(s.textScale.value * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            Slider(
                value = s.textScale.value,
                onValueChange = { s.textScale.set((Math.round(it * 20) / 20f)) },
                valueRange = 0.85f..1.6f,
                steps = 14,
            )
            // Live preview: same colours as real bubbles, same base 16sp, scaled.
            val size = (16f * s.textScale.value).sp
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 5.dp),
                ) {
                    Text(stringResource(R.string.appearance_preview_incoming), fontSize = size,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.appearance_preview_outgoing), fontSize = size,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
    }
}

// ─── Chat background ─────────────────────────────────────────────────────────

@Composable
private fun BackgroundCard(s: AppearanceSettings, onPickImage: () -> Unit) {
    ExpressiveGroupRow(GroupPosition.SINGLE) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(R.drawable.ic_image_24dp)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.appearance_background), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackgroundTile("plain", R.string.appearance_background_plain, s, Modifier.weight(1f)) { s.background.set("plain") }
                BackgroundTile("tonal", R.string.appearance_background_tonal, s, Modifier.weight(1f)) { s.background.set("tonal") }
                BackgroundTile("image", R.string.appearance_background_image, s, Modifier.weight(1f)) {
                    if (s.backgroundImage.value.isEmpty()) onPickImage() else s.background.set("image")
                }
            }
            AnimatedVisibility(visible = s.background.value == "image") {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.appearance_background_dim), Modifier.weight(1f))
                        Text("${s.backgroundDim.value}%", color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = s.backgroundDim.value.toFloat(),
                        onValueChange = { s.backgroundDim.set(it.toInt()) },
                        valueRange = 0f..80f,
                    )
                    OutlinedButton(onClick = onPickImage) {
                        Text(stringResource(R.string.appearance_background_pick))
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundTile(key: String, label: Int, s: AppearanceSettings, modifier: Modifier, onClick: () -> Unit) {
    val selected = s.background.value == key
    val scheme = MaterialTheme.colorScheme
    val border by animateColorAsState(if (selected) scheme.primary else scheme.outlineVariant, label = "bg_border")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when (key) {
                        "tonal" -> scheme.surfaceContainerLow
                        "image" -> scheme.tertiaryContainer
                        else -> scheme.surface
                    }
                )
                .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (key == "tonal") Box(Modifier.matchParentSizeCompat().background(scheme.primary.copy(alpha = 0.05f)))
            if (key == "image") Icon(painterResource(R.drawable.ic_image_24dp), null, tint = scheme.onTertiaryContainer)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxSize()

// ─── Animation level ─────────────────────────────────────────────────────────

@Composable
private fun AnimationLevelCard(s: AppearanceSettings) {
    val options = listOf(
        Triple("full", R.string.appearance_anim_full, R.string.appearance_anim_full_desc),
        Triple("auto", R.string.appearance_anim_auto, R.string.appearance_anim_auto_desc),
        Triple("reduced", R.string.appearance_anim_reduced, R.string.appearance_anim_reduced_desc),
        Triple("off", R.string.appearance_anim_off, R.string.appearance_anim_off_desc),
    )
    options.forEachIndexed { i, (key, title, desc) ->
        val pos = when (i) { 0 -> GroupPosition.TOP; options.lastIndex -> GroupPosition.BOTTOM; else -> GroupPosition.MIDDLE }
        ExpressiveGroupRow(pos) {
            ListItem(
                leadingContent = { RadioButton(selected = s.animations.value == key, onClick = null) },
                headlineContent = { Text(stringResource(title)) },
                supportingContent = { Text(stringResource(desc)) },
                modifier = Modifier.clickable { s.animations.set(key) },
                colors = rowColors(),
            )
        }
    }
}

// ─── Chat bubbles sub-page ───────────────────────────────────────────────────

@Composable
private fun BubblesPage(s: AppearanceSettings) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SwitchRow(
            GroupPosition.TOP, R.drawable.ic_colors_24dp,
            stringResource(R.string.pref_use_colorful_bubbles),
            stringResource(R.string.pref_use_colorful_bubbles_summary),
            s.colorfulBubbles.value,
        ) { s.colorfulBubbles.set(it) }
        SwitchRow(
            GroupPosition.BOTTOM, R.drawable.ic_format_align_left_24dp,
            stringResource(R.string.pref_align_start),
            stringResource(R.string.pref_align_start_summary),
            s.alignStart.value,
        ) { s.alignStart.set(it) }
        SectionTitle(stringResource(R.string.pref_category_avatars))
        SwitchRow(
            GroupPosition.TOP, R.drawable.ic_face_right_24dp,
            stringResource(R.string.pref_show_11_chats),
            stringResource(R.string.pref_show_11_chats_summary),
            s.avatars11.value,
        ) { s.avatars11.set(it) }
        SwitchRow(
            GroupPosition.BOTTOM, R.drawable.ic_face_left_24dp,
            stringResource(R.string.pref_account_avatar),
            stringResource(R.string.pref_account_avatar_summary),
            s.avatarsAccounts.value,
            enabled = !s.alignStart.value,
        ) { s.avatarsAccounts.set(it) }
    }
}
