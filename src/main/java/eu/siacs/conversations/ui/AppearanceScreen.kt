package eu.siacs.conversations.ui

import android.content.SharedPreferences
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContentScope
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
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.siacs.conversations.R
import androidx.compose.ui.input.pointer.pointerInput
import eu.siacs.conversations.ui.glass.LocalGlassHost
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.combinedClickable
import eu.siacs.conversations.ui.glass.rememberGlassStack
import eu.siacs.conversations.ui.glass.LiquidGlassButton
import eu.siacs.conversations.ui.glass.LiquidBottomTabs
import eu.siacs.conversations.ui.glass.LiquidBottomTab
import eu.siacs.conversations.ui.glass.GlassTuning
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import eu.siacs.conversations.ui.glass.LiquidSlider
import eu.siacs.conversations.ui.glass.LiquidToggle
import eu.siacs.conversations.ui.glass.LocalGlassSkin

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
    val interfaceStyle = Str(LiquidGlass.PREF_KEY, InterfaceStyle.MATERIAL.key)
    val haptics = Bool(AppearanceState.HAPTICS, true)

    val alignStart = Bool("align_start", b(R.bool.align_start))
    val avatars11 = Bool("show_avatars", b(R.bool.show_avatars))
    val avatarsAccounts = Bool("show_avatars_accounts", b(R.bool.show_avatars_accounts))
    val avatarsAccountsBeforeAlign = Bool("show_avatars_accounts_before_align", b(R.bool.show_avatars_accounts))

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

internal enum class AppearancePage { MAIN, BUBBLES, LAB, PLAYGROUND }

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
    onStyleChosen: (InterfaceStyle) -> Unit = {},
    onOpenLab: () -> Unit = {},
    /** The lab's button was pressed: where it is on the screen (window pixels), for the playground to grow out of. */
    onOpenPlayground: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    playgroundOrigin: androidx.compose.ui.geometry.Rect? = null,
    onClosePlayground: () -> Unit = {},
) {
  val labBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
  Box(Modifier.fillMaxSize()) {
    // The playground arrives over the lab, which stays where it is underneath.
    val shownPage = if (page == AppearancePage.PLAYGROUND) AppearancePage.LAB else page
    AnimatedContent(
        modifier = Modifier.fillMaxSize().layerBackdrop(labBackdrop),
        targetState = shownPage,
        transitionSpec = {
            if (targetState != AppearancePage.MAIN) {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
            }
        },
        label = "appearance_page",
    ) { target ->
        if (target == AppearancePage.BUBBLES) {
            BubblesPage(settings)
        } else if (target == AppearancePage.LAB) {
            GlassLabPage(onOpenPlayground)
        } else {
            MainPage(
                settings, dynamicAvailable, onThemeChosen, onDynamicChanged, onAccentChanged,
                onPickBackgroundImage, onOpenQuickReactions, onScreenshotsChanged, onOpenBubbles,
                onStyleChosen, onOpenLab,
            )
        }
    }
    AnimatedVisibility(
        visible = page == AppearancePage.PLAYGROUND,
        enter = EnterTransition.None,
        exit = fadeOut(tween(450)),
    ) {
        GlassPlayground(origin = playgroundOrigin, onBack = onClosePlayground, labBackdrop = labBackdrop)
    }
  }
}

// ─── Building blocks ─────────────────────────────────────────────────────────

@Composable
internal fun SectionTitle(text: String) {
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

/** A slider that turns into the glass one (a thumb that becomes a lens when held) on a glass screen. */
@Composable
private fun SkinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
) {
    val skin = LocalGlassSkin.current
    if (skin != null) {
        // The slider watches this lambda for changes (a snapshot flow), so it has to read state: a
        // plain `{ value }` captured the first value for good, the slider never heard of the new
        // ones, and every drag started again from where it began, so it could only be tapped.
        val current by androidx.compose.runtime.rememberUpdatedState(value)
        LiquidSlider(
            value = { current },
            onValueChange = onValueChange,
            valueRange = valueRange,
            visibilityThreshold = (valueRange.endInclusive - valueRange.start) / 100f,
            backdrop = skin.backdrop,
        )
    } else {
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
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
            trailingContent = {
                val skin = LocalGlassSkin.current
                if (skin != null && enabled) {
                    // The glass toggle: a lens that swells when held.
                    LiquidToggle(selected = { checked }, onSelect = onChange, backdrop = skin.backdrop)
                } else {
                    Switch(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled)
                }
            },
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
    onStyleChosen: (InterfaceStyle) -> Unit,
    onOpenLab: () -> Unit,
) {
    var openPicker by remember { mutableStateOf<String?>(null) }
    // The style picker's Easter egg (see StyleEgg): long-press "Liquid glass".
    val egg = remember { StyleEgg() }
    val glassHost = LocalGlassHost.current
    val haptic = LocalHapticFeedback.current
    var eggRun by remember { mutableIntStateOf(0) }
    LaunchedEffect(eggRun) { if (eggRun > 0) egg.play(glassHost, haptic) }
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
                // ── Style: Material 3 Expressive or liquid glass ──
                SectionTitle(stringResource(R.string.appearance_section_style))
                PickerSource(
                    "style", openPicker == "style", GroupPosition.SINGLE,
                    R.drawable.ic_touch_app_24dp,
                    stringResource(R.string.interface_style_title),
                    stringResource(InterfaceStyle.of(s.interfaceStyle.value).titleRes),
                ) { openPicker = "style" }
                // The Glass lab: only while the glass style is on.
                AnimatedVisibility(
                    visible = LiquidGlass.supported && s.interfaceStyle.value == InterfaceStyle.GLASS.key,
                    enter = expandVertically(spring(stiffness = 380f, dampingRatio = 0.8f)) + fadeIn(),
                    exit = shrinkVertically(spring(stiffness = 380f, dampingRatio = 0.8f)) + fadeOut(),
                ) {
                    Column {
                        Spacer(Modifier.height(2.dp))
                        NavRow(
                            GroupPosition.SINGLE, R.drawable.ic_lightbulb_24dp,
                            stringResource(R.string.glass_lab_title),
                            stringResource(R.string.glass_lab_summary), onOpenLab,
                        )
                    }
                }

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

            StyleCard(egg, glassHost, openPicker == "style", { openPicker = null }) {
              androidx.compose.runtime.CompositionLocalProvider(LocalEggFrac provides { egg.frac.value }) {
                StylePickerContent(
                    current = InterfaceStyle.of(s.interfaceStyle.value),
                    onEgg = { eggRun++ },
                ) { picked ->
                    s.interfaceStyle.set(picked.key)
                    openPicker = null
                    onStyleChosen(picked)
                }
              }
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

// ─── Style picker: the options with an info button each, and the info page ─────────────────

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StylePickerContent(current: InterfaceStyle, onEgg: () -> Unit, onPick: (InterfaceStyle) -> Unit) {
    var info by remember { mutableStateOf<InterfaceStyle?>(null) }
    BackHandler(enabled = info != null) { info = null }
    // Inner SharedTransitionLayout: the options list <-> an option's info page, as on the update
    // channel picker.
    SharedTransitionLayout {
        AnimatedContent(
            targetState = info,
            transitionSpec = {
                EnterTransition.None togetherWith ExitTransition.None using
                    SizeTransform(clip = false) { _, _ -> spring(stiffness = 380f, dampingRatio = 0.8f) }
            },
            label = "style_picker_content",
        ) { style ->
            if (style == null) {
                StyleList(current, onPick, onInfo = { info = it }, onEgg = onEgg, animatedContentScope = this)
            } else {
                StyleInfoPage(style, onBack = { info = null }, animatedContentScope = this)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SharedTransitionScope.StyleList(
    current: InterfaceStyle,
    onPick: (InterfaceStyle) -> Unit,
    onInfo: (InterfaceStyle) -> Unit,
    onEgg: () -> Unit,
    animatedContentScope: AnimatedContentScope,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 20.dp, bottom = 4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_touch_app_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.interface_style_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        val styles = InterfaceStyle.entries
        styles.forEachIndexed { index, style ->
            val available = style != InterfaceStyle.GLASS || LiquidGlass.supported
            val rowShape = when {
                styles.size == 1 -> RoundedCornerShape(28.dp)
                index == 0 -> RoundedCornerShape(28.dp, 28.dp, 8.dp, 8.dp)
                index == styles.lastIndex -> RoundedCornerShape(8.dp, 8.dp, 28.dp, 28.dp)
                else -> RoundedCornerShape(8.dp)
            }
            // Each row's surface is the source of its own row -> info container transform.
            Surface(
                shape = rowShape,
                color = androidx.compose.ui.graphics.lerp(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    Color.Transparent,
                    LocalEggFrac.current().coerceIn(0f, 1f),
                ),
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .sharedBounds(
                        rememberSharedContentState("style_info_${style.key}"),
                        animatedVisibilityScope = animatedContentScope,
                        enter = fadeIn(quickFade),
                        exit = fadeOut(quickFade),
                        boundsTransform = springBounds,
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    ),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(style.titleRes)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (available) style.summaryRes else R.string.interface_style_glass_unsupported
                            )
                        )
                    },
                    leadingContent = {
                        RadioButton(selected = style == current, onClick = null, enabled = available)
                    },
                    trailingContent = {
                        IconButton(onClick = { onInfo(style) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_info_outline_24dp),
                                contentDescription = stringResource(R.string.interface_style_info),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    // Long-press on "Liquid glass" is the Easter egg.
                    modifier = Modifier.combinedClickable(
                        enabled = available,
                        onClick = { onPick(style) },
                        onLongClick = if (style == InterfaceStyle.GLASS) onEgg else null,
                    ),
                    colors = rowColors(),
                )
            }
            if (index < styles.lastIndex) Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SharedTransitionScope.StyleInfoPage(
    style: InterfaceStyle,
    onBack: () -> Unit,
    animatedContentScope: AnimatedContentScope,
) {
    // Matches the source row's surface, so the container transform morphs the row into this card.
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .sharedBounds(
                rememberSharedContentState("style_info_${style.key}"),
                animatedVisibilityScope = animatedContentScope,
                enter = fadeIn(quickFade),
                exit = fadeOut(quickFade),
                boundsTransform = springBounds,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            ),
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_info_outline_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(style.titleRes),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(style.infoRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (style == InterfaceStyle.GLASS && LiquidGlass.supported) {
                Spacer(Modifier.height(16.dp))
                // The real components, live: touch them.
                GlassPreview()
            } else if (style == InterfaceStyle.MATERIAL) {
                Spacer(Modifier.height(16.dp))
                // What makes it Expressive: shapes that morph under the finger, motion that springs.
                ExpressivePreview()
            }
            Spacer(Modifier.height(16.dp))
            // A static shape, not the animated press-morph: this button first composes while its
            // card is still mid transition (see the same note on the update channel picker).
            FilledTonalButton(
                onClick = onBack,
                shape = ButtonDefaults.filledTonalShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(android.R.string.ok))
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
                val swatchContext = LocalContext.current
                val scheme = remember(color) { accentPreviewScheme(swatchContext, color, false) }
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
            SkinSlider(
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
                    SkinSlider(
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
            GroupPosition.SINGLE, R.drawable.ic_format_align_left_24dp,
            stringResource(R.string.pref_align_start),
            stringResource(R.string.pref_align_start_summary),
            s.alignStart.value,
        ) { on ->
            s.alignStart.set(on)
            if (on) {
                // Our own avatar has no place when everything is on one side: switch it off, but
                // remember what it was so turning alignment off again brings it back.
                s.avatarsAccountsBeforeAlign.set(s.avatarsAccounts.value)
                s.avatarsAccounts.set(false)
            } else {
                s.avatarsAccounts.set(s.avatarsAccountsBeforeAlign.value)
            }
        }
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
            s.avatarsAccounts.value && !s.alignStart.value,
            enabled = !s.alignStart.value,
        ) { s.avatarsAccounts.set(it) }
    }
}


/**
 * The style picker's card: [PickerCard], but with the card drawn by [EggCard] so it can turn into
 * glass; the overlay also follows the finger for the egg. While the egg runs, tapping outside does
 * not close it.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.StyleCard(
    egg: StyleEgg,
    host: eu.siacs.conversations.ui.glass.GlassHost?,
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Where the finger is, in coordinates that do not move with the card.
                .pointerInput(egg) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            egg.finger = event.changes.firstOrNull { it.pressed }?.position
                        }
                    }
                }
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    if (!egg.running) onDismiss()
                },
            contentAlignment = Alignment.Center,
        ) {
            EggCard(
                egg = egg,
                host = host,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .sharedBounds(
                        rememberSharedContentState("style"),
                        animatedVisibilityScope = this@AnimatedVisibility,
                        enter = fadeIn(quickFade),
                        exit = fadeOut(quickFade),
                        boundsTransform = springBounds,
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    )
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                content = content,
            )
        }
    }
}

/** The Expressive parts of Material 3, live: shapes that change under the finger, a wavy bar, a loader. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressivePreview() {
    var presses by remember { mutableIntStateOf(0) }
    val progress = remember { androidx.compose.animation.core.Animatable(0.2f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress.animateTo(0.9f, androidx.compose.animation.core.tween(2400))
            progress.animateTo(0.15f, androidx.compose.animation.core.tween(2400))
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { presses++ }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.interface_style_try))
            }
            FilledTonalButton(onClick = { presses++ }, shapes = ButtonDefaults.shapes()) {
                Text(presses.toString())
            }
            androidx.compose.material3.LoadingIndicator(Modifier.size(48.dp))
        }
        androidx.compose.material3.LinearWavyProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─── Glass: the live preview in the info card, and the lab ───────────────────────────────

/** A button, a toggle, a slider and a bottom bar of the glass style, over colour, to try out. */
@Composable
private fun GlassPreview() {
    val layer = rememberLayerBackdrop()
    val stack = rememberGlassStack(layer)
    var toggled by remember { mutableStateOf(true) }
    var slider by remember { mutableFloatStateOf(0.6f) }
    var tab by remember { mutableIntStateOf(0) }
    val a = MaterialTheme.colorScheme.primaryContainer
    val b = MaterialTheme.colorScheme.tertiaryContainer
    val c = MaterialTheme.colorScheme.secondaryContainer
    val base = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
        // What the glass shows: colour with a few soft blobs.
        androidx.compose.foundation.Canvas(Modifier.matchParentSize().layerBackdrop(layer)) {
            drawRect(base)
            val w = size.width
            val h = size.height
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(a, Color.Transparent), androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.2f), w * 0.55f),
                radius = w * 0.55f, center = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.2f),
            )
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(b, Color.Transparent), androidx.compose.ui.geometry.Offset(w * 0.85f, h * 0.5f), w * 0.55f),
                radius = w * 0.55f, center = androidx.compose.ui.geometry.Offset(w * 0.85f, h * 0.5f),
            )
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(c, Color.Transparent), androidx.compose.ui.geometry.Offset(w * 0.3f, h * 0.9f), w * 0.6f),
                radius = w * 0.6f, center = androidx.compose.ui.geometry.Offset(w * 0.3f, h * 0.9f),
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LiquidGlassButton(
                    onClick = {},
                    glass = stack,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Text(stringResource(R.string.interface_style_glass), style = MaterialTheme.typography.labelLarge)
                }
                LiquidToggle(selected = { toggled }, onSelect = { toggled = it }, backdrop = layer)
            }
            LiquidSlider(
                value = { slider },
                onValueChange = { slider = it },
                valueRange = 0f..1f,
                visibilityThreshold = 0.01f,
                backdrop = layer,
            )
            LiquidBottomTabs(
                selectedTabIndex = { tab },
                onTabSelected = { tab = it },
                backdrop = layer,
                tabsCount = 3,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                LiquidBottomTab({ tab = 0 }) { Icon(painterResource(R.drawable.ic_chat_24dp), null) }
                LiquidBottomTab({ tab = 1 }) { Icon(painterResource(R.drawable.ic_call_24dp), null) }
                LiquidBottomTab({ tab = 2 }) { Icon(painterResource(R.drawable.ic_person_24dp), null) }
            }
        }
    }
}

/** The Glass lab: sliders and switches for how the glass looks and behaves, live on this very screen. */
@Composable
private fun GlassLabPage(onOpenPlayground: (androidx.compose.ui.geometry.Rect) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.glass_lab_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        // The playground: this button melts into the playground's own buttons (see GlassPlayground).
        var playgroundBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
        FilledTonalButton(
            onClick = { onOpenPlayground(playgroundBounds) },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(64.dp)
                .onGloballyPositioned { playgroundBounds = it.boundsInWindow() },
        ) {
            Icon(painterResource(R.drawable.ic_touch_app_24dp), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.glass_lab_playground), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        SectionTitle(stringResource(R.string.glass_lab_section_look))
        LabSlider(GroupPosition.TOP, R.string.glass_lab_blur, GlassTuning.blur) { GlassTuning.putBlur(it) }
        LabSlider(GroupPosition.MIDDLE, R.string.glass_lab_refraction, GlassTuning.refraction) { GlassTuning.putRefraction(it) }
        LabSlider(GroupPosition.MIDDLE, R.string.glass_lab_frost, GlassTuning.frost) { GlassTuning.putFrost(it) }
        LabSlider(GroupPosition.BOTTOM, R.string.glass_lab_jelly, GlassTuning.jelly) { GlassTuning.putJelly(it) }
        Spacer(Modifier.height(6.dp))
        SectionTitle(stringResource(R.string.glass_lab_section_behaviour))
        SwitchRow(
            GroupPosition.TOP, R.drawable.ic_palette_24dp,
            stringResource(R.string.glass_lab_fringes), stringResource(R.string.glass_lab_fringes_summary),
            GlassTuning.fringes,
        ) { GlassTuning.putFringes(it) }
        SwitchRow(
            GroupPosition.MIDDLE, R.drawable.ic_hourglass_top_24dp,
            stringResource(R.string.glass_lab_less_often), stringResource(R.string.glass_lab_less_often_summary),
            GlassTuning.lessOften,
        ) { GlassTuning.putLessOften(it) }
        SwitchRow(
            GroupPosition.BOTTOM, R.drawable.ic_touch_app_24dp,
            stringResource(R.string.glass_lab_bar_blur), stringResource(R.string.glass_lab_bar_blur_summary),
            GlassTuning.barBlur,
        ) { GlassTuning.putBarBlur(it) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { GlassTuning.reset() }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.glass_lab_reset))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The merging-glass prototype: three pieces to drag around, and the numbers that shape the bridge. */
@Composable
internal fun FluidPrototype() {
    val skin = LocalGlassSkin.current ?: return
    var reach by remember { mutableFloatStateOf(28f) }
    var response by remember { mutableFloatStateOf(650f) }
    var soft by remember { mutableFloatStateOf(16f) }
    var walls by remember { mutableStateOf(false) }
    var floor by remember { mutableStateOf(false) }
    var friction by remember { mutableFloatStateOf(1.4f) }
    var bounce by remember { mutableFloatStateOf(0.7f) }
    var rigidOn by remember { mutableStateOf(false) }
    var rotation by remember { mutableIntStateOf(1) }
    var solidOn by remember { mutableStateOf(false) }
    var mergeOff by remember { mutableStateOf(false) }
    var gravityOn by remember { mutableStateOf(false) }
    var gravity by remember { mutableFloatStateOf(1200f) }
    var gravityAngle by remember { mutableFloatStateOf(90f) }
    var tiltOn by remember { mutableStateOf(false) }
    var laser by remember { mutableStateOf(false) }
    var laserAngle by remember { mutableFloatStateOf(35f) }
    var laserSpin by remember { mutableFloatStateOf(0f) }
    var glassIndex by remember { mutableFloatStateOf(1.5f) }
    var fieldHeight by remember { mutableFloatStateOf(300f) }
    var fieldWidth by remember { mutableFloatStateOf(100f) }
    val fluid = eu.siacs.conversations.ui.glass.rememberGlassFluidState()
    SectionTitle(stringResource(R.string.glass_lab_section_merge))
    Text(
        stringResource(R.string.glass_lab_merge_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
    // A setting that only matters when another is on is shown only then, right under its parent.
    val mergeRows = buildList<SettingsRow> {
        if (!mergeOff) {
            add("glass_lab_merge_connect") { ProtoSlider(it, R.string.glass_lab_merge_connect, reach, 4f..80f, "dp") { v -> reach = v } }
            add("glass_lab_merge_release") { ProtoSlider(it, R.string.glass_lab_merge_release, response, 300f..1500f, "ms") { v -> response = v } }
            add("glass_lab_merge_soft") { ProtoSlider(it, R.string.glass_lab_merge_soft, soft, 4f..48f, "dp") { v -> soft = v } }
        }
    }
    // The field comes first (the playground), the controls under it.
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { fluid.add() }, enabled = fluid.pieces.size < 16, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.glass_lab_merge_add, fluid.pieces.size))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { fluid.removeLast() }, enabled = fluid.pieces.size > 1, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.glass_lab_merge_remove), maxLines = 1)
            }
            OutlinedButton(onClick = { fluid.reset() }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.glass_lab_reset), maxLines = 1)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    eu.siacs.conversations.ui.glass.GlassFluidField(
        state = fluid,
        reach = reach,
        responseMs = response,
        softness = soft,
        wallsOn = walls,
        inertiaOn = floor,
        friction = friction,
        bounce = bounce,
        rigidOn = rigidOn,
        rotationLevel = rotation,
        solidOn = solidOn,
        mergeOff = mergeOff,
        gravityOn = gravityOn,
        gravity = gravity,
        gravityAngle = gravityAngle,
        tiltOn = tiltOn,
        laserOn = laser,
        laserAngle = laserAngle,
        laserSpin = laserSpin,
        glassIndex = glassIndex,
        heightDp = fieldHeight,
        widthFraction = fieldWidth,
    ) { index, tint ->
        when (index % 4) {
            0 -> Icon(painterResource(R.drawable.ic_palette_24dp), null, tint = tint)
            1 -> Text("Merge", color = tint, style = MaterialTheme.typography.titleSmall)
            2 -> Icon(painterResource(R.drawable.ic_touch_app_24dp), null, tint = tint)
            else -> Icon(painterResource(R.drawable.ic_hourglass_top_24dp), null, tint = tint)
        }
    }
    Spacer(Modifier.height(8.dp))
    SettingsGroup(mergeRows)
    val behaviour = buildList<SettingsRow> {
        add("glass_lab_merge_rigid") {
            SwitchRow(
                it, R.drawable.ic_touch_app_24dp,
                stringResource(R.string.glass_lab_merge_rigid), stringResource(R.string.glass_lab_merge_rigid_summary),
                rigidOn,
            ) { v ->
                rigidOn = v
                // Real physics is about pieces meeting each other: they become solid with it.
                if (v) solidOn = true
            }
        }
        if (rigidOn) {
            add("glass_lab_merge_rotation") {
                ChoiceRow(
                    it, stringResource(R.string.glass_lab_merge_rotation), stringResource(R.string.glass_lab_merge_rotation_summary),
                    listOf(
                        stringResource(R.string.glass_lab_merge_rotation_off),
                        stringResource(R.string.glass_lab_merge_rotation_stiff),
                        stringResource(R.string.glass_lab_merge_rotation_free),
                    ),
                    rotation,
                ) { v -> rotation = v }
            }
        }
        add("glass_lab_merge_nomerge") {
            SwitchRow(
                it, R.drawable.ic_palette_24dp,
                stringResource(R.string.glass_lab_merge_nomerge), stringResource(R.string.glass_lab_merge_nomerge_summary),
                mergeOff,
            ) { v -> mergeOff = v }
        }
        add("glass_lab_merge_stack") {
            SwitchRow(
                it, R.drawable.ic_touch_app_24dp,
                stringResource(R.string.glass_lab_merge_stack), stringResource(R.string.glass_lab_merge_stack_summary),
                solidOn,
            ) { v -> solidOn = v }
        }
        add("glass_lab_merge_walls") {
            SwitchRow(
                it, R.drawable.ic_touch_app_24dp,
                stringResource(R.string.glass_lab_merge_walls), stringResource(R.string.glass_lab_merge_walls_summary),
                walls,
            ) { v -> walls = v }
        }
        add("glass_lab_merge_floor") {
            SwitchRow(
                it, R.drawable.ic_hourglass_top_24dp,
                stringResource(R.string.glass_lab_merge_floor), stringResource(R.string.glass_lab_merge_floor_summary),
                floor,
            ) { v -> floor = v }
        }
        add("glass_lab_merge_gravity") {
            SwitchRow(
                it, R.drawable.ic_hourglass_top_24dp,
                stringResource(R.string.glass_lab_merge_gravity), stringResource(R.string.glass_lab_merge_gravity_summary),
                gravityOn,
            ) { v -> gravityOn = v }
        }
        if (gravityOn) {
            add("glass_lab_merge_gravity_strength") { ProtoSlider(it, R.string.glass_lab_merge_gravity_strength, gravity, 0f..4000f, "dp/s²") { v -> gravity = v } }
            add("glass_lab_merge_tilt") {
                SwitchRow(
                    it, R.drawable.ic_touch_app_24dp,
                    stringResource(R.string.glass_lab_merge_tilt), stringResource(R.string.glass_lab_merge_tilt_summary),
                    tiltOn,
                ) { v -> tiltOn = v }
            }
            if (!tiltOn) {
                add("glass_lab_merge_gravity_angle") { ProtoSlider(it, R.string.glass_lab_merge_gravity_angle, gravityAngle, 0f..360f, "°") { v -> gravityAngle = v } }
            }
        }
        // Friction and bounce act on anything that slides or falls: the floor or gravity.
        if (floor || gravityOn) {
            add("glass_lab_merge_friction") { ProtoSlider(it, R.string.glass_lab_merge_friction, friction, 0f..8f, "/s", decimals = 1) { v -> friction = v } }
            add("glass_lab_merge_bounce") { ProtoSlider(it, R.string.glass_lab_merge_bounce, bounce, 0f..1.5f, "", decimals = 2) { v -> bounce = v } }
        }
        add("glass_lab_merge_laser") {
            SwitchRow(
                it, R.drawable.ic_palette_24dp,
                stringResource(R.string.glass_lab_merge_laser), stringResource(R.string.glass_lab_merge_laser_summary),
                laser,
            ) { v -> laser = v }
        }
        if (laser) {
            add("glass_lab_merge_laser_angle") { ProtoSlider(it, R.string.glass_lab_merge_laser_angle, laserAngle, 0f..360f, "°") { v -> laserAngle = v } }
            add("glass_lab_merge_laser_spin") { ProtoSlider(it, R.string.glass_lab_merge_laser_spin, laserSpin, -90f..90f, "°/s") { v -> laserSpin = v } }
            add("glass_lab_merge_index") { ProtoSlider(it, R.string.glass_lab_merge_index, glassIndex, 1f..2.5f, "", decimals = 2) { v -> glassIndex = v } }
        }
    }
    Spacer(Modifier.height(8.dp))
    SettingsGroup(behaviour)
    Spacer(Modifier.height(8.dp))
    SettingsGroup(
        listOf(
            SettingsRow("height") { p -> ProtoSlider(p, R.string.glass_lab_merge_height, fieldHeight, 150f..1000f, "dp") { v -> fieldHeight = v } },
            SettingsRow("width") { p -> ProtoSlider(p, R.string.glass_lab_merge_width, fieldWidth, 50f..400f, "%") { v -> fieldWidth = v } },
        ),
    )

}

/** A row with a few choices as chips. */
@Composable
private fun ChoiceRow(position: GroupPosition, title: String, summary: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    ExpressiveGroupRow(position) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEachIndexed { i, label ->
                    androidx.compose.material3.FilterChip(
                        selected = i == selected,
                        onClick = { onSelect(i) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

/** Rows that make up one rounded group: the corners follow whichever rows are showing now. */
private class SettingsRow(val key: String, val content: @Composable (GroupPosition) -> Unit)

private fun MutableList<SettingsRow>.add(key: String, content: @Composable (GroupPosition) -> Unit) {
    add(SettingsRow(key, content))
}

@Composable
private fun SettingsGroup(rows: List<SettingsRow>) {
    if (rows.isEmpty()) return
    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEachIndexed { i, row ->
            // Keyed: a row keeps its own state (a slider's value) when another appears above it.
            androidx.compose.runtime.key(row.key) {
            row.content(
                when {
                    rows.size == 1 -> GroupPosition.SINGLE
                    i == 0 -> GroupPosition.TOP
                    i == rows.lastIndex -> GroupPosition.BOTTOM
                    else -> GroupPosition.MIDDLE
                },
            )
            }
        }
    }
}

/**
 * A value that can be typed in: tap it and enter any number, even one the slider cannot reach
 * (the slider just sits at its end then).
 */
@Composable
private fun EditableValue(shown: String, initial: String, onCommit: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text(
        shown,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { open = true }
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
    if (open) {
        var field by remember { mutableStateOf(initial) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.glass_lab_value_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.glass_lab_value_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = field,
                        onValueChange = { field = it },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        ),
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        field.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }?.let { onCommit(it) }
                        open = false
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { open = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProtoSlider(position: GroupPosition, label: Int, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, decimals: Int = 0, onChange: (Float) -> Unit) {
    var live by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { if (kotlin.math.abs(value - live) > (range.endInclusive - range.start) * 0.002f) live = value }
    val shown = if (decimals > 0) String.format(java.util.Locale.US, "%.${decimals}f", live) else live.toInt().toString()
    ExpressiveGroupRow(position) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(label), Modifier.weight(1f))
                EditableValue("$shown $unit", shown) { typed ->
                    live = typed
                    onChange(typed)
                }
            }
            SkinSlider(value = live.coerceIn(range), onValueChange = { live = it; onChange(it) }, valueRange = range)
        }
    }
}

@Composable
private fun LabSlider(position: GroupPosition, label: Int, value: Float, onChange: (Float) -> Unit) {
    // The slider keeps its own position while a finger drags it. Feeding it the stored value back
    // (which was rounded, and which changes the glass under the slider itself) made it jump to the
    // rounded value on every frame, so a drag could never get anywhere; only a tap worked.
    var live by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { if (kotlin.math.abs(value - live) > 0.051f) live = value }
    ExpressiveGroupRow(position) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(label), Modifier.weight(1f))
                EditableValue("${(live * 100).toInt()}%", (live * 100).toInt().toString()) { typed ->
                    live = typed / 100f
                    onChange(live)
                }
            }
            SkinSlider(
                value = live.coerceIn(0f, 2f),
                onValueChange = {
                    live = it
                    onChange(Math.round(it * 20) / 20f)
                },
                valueRange = 0f..2f,
            )
        }
    }
}
