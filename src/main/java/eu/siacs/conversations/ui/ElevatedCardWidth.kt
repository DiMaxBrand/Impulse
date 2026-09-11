package eu.siacs.conversations.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widest an elevated-card-style dialog (like [AddReactionDialogFragment]/
 * [QuickReactionDialogFragment]'s cards) should get on this device, leaving [OUTER_MARGIN] of
 * breathing room on each side -- as wide as the "thickest" reasonable variant gets, so content
 * that needs the space (the reaction shortcut row) gets as much of it as it can before Material3's
 * own [androidx.compose.material3.ButtonGroup] overflow behavior has to kick in.
 *
 * [OUTER_MARGIN] itself doesn't have one single authoritative Material 3 number -- two real
 * sources disagree: the classic Material (2) dialog spec calls for a 48dp minimum margin from the
 * screen edge, while Material Components for Android's own `MaterialAlertDialogBuilder` /
 * `mtrl_alert_dialog_background_inset_start`/`_end` implementation defaults to just 10dp. 24dp is
 * the practical middle used here -- Material's own 8dp-grid keyline unit, and what this app's
 * other elevated-card dialogs already use for the same purpose (`QuickReactionDialogFragment`'s
 * card, `AddReactionDialogFragment`'s card).
 */
private val OUTER_MARGIN: Dp = 24.dp

@Composable
fun rememberMaxElevatedCardWidth(): Dp {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    return screenWidthDp - OUTER_MARGIN * 2
}
