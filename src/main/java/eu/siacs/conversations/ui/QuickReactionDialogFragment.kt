package eu.siacs.conversations.ui

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import eu.siacs.conversations.AppSettings

/** "Quick Reactions" as an actual overlay card over whatever screen launched it (currently only
 * Settings → Interface), not a separate full-screen destination — matches how the existing
 * add-reaction dialog presents (an AlertDialog card over the chat, not its own screen), and the
 * spec's own "elevated card" language. */
class QuickReactionDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        // Transparent window background so only the Compose-drawn rounded Surface below shows —
        // otherwise the default dialog window paints its own opaque rectangle behind it.
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ImpulseExpressiveTheme {
                QuickReactionDialogCard(
                    onDismiss = { dismiss() },
                )
            }
        }
    }

    companion object {
        const val TAG = "quick_reaction_dialog"
    }
}

@androidx.compose.runtime.Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun QuickReactionDialogCard(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appSettings = androidx.compose.runtime.remember { AppSettings(context) }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = 400.dp),
    ) {
        QuickReactionPickerContent(
            initialEmoji = appSettings.quickReactionEmoji,
            initialRemember = appSettings.isQuickReactionRemember,
            onSave = { emoji, remember ->
                appSettings.setQuickReaction(emoji, remember)
                onDismiss()
            },
        )
    }
}
