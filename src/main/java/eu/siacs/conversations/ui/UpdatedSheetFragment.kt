package eu.siacs.conversations.ui

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.R
import eu.siacs.conversations.update.UpdatePreferences

/**
 * "Impulse was updated!" -- shown on the first launch after an update (by the nightly one, or
 * the manual button), with the release title and notes of what was just installed. A dismiss
 * button sits at the top. Dismissing in any way forgets the update, so it shows once.
 *
 * It is a card in the middle of the screen, not a bottom sheet. In the glass style a drop of glass
 * falls from the top of the screen to the middle, and the drop itself grows into the card (a
 * container transform: the card is that circle, getting bigger and less round), whose content then
 * fades in. Without the glass style the card is simply there.
 */
class UpdatedSheetFragment : DialogFragment() {

    private lateinit var prefs: UpdatePreferences

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setDimAmount(0f)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        prefs = UpdatePreferences(requireContext())
        // The preview (Developer options) shows sample text and leaves the real "just updated"
        // record alone.
        val preview = arguments?.getBoolean(ARG_PREVIEW, false) == true
        val title = if (preview) getString(R.string.updated_sheet_preview_title) else prefs.justUpdatedTitle
        val notes = if (preview) getString(R.string.updated_sheet_preview_notes) else prefs.justUpdatedNotes
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ImpulseExpressiveTheme {
                    eu.siacs.conversations.ui.glass.GlassDropCard(
                        enabled = LiquidGlass.enabled(requireContext()),
                        onDismiss = { dismiss() },
                    ) {
                        Column(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .heightIn(max = 560.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Dismiss, above everything else.
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { dismiss() }) {
                                    Icon(
                                        painterResource(R.drawable.ic_close_24dp),
                                        contentDescription = stringResource(R.string.updated_sheet_dismiss),
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.updated_sheet_title),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                text = stringResource(R.string.updated_sheet_whats_new),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!title.isNullOrBlank()) {
                                Text(text = title, style = MaterialTheme.typography.titleLarge)
                            }
                            ReleaseNotesSection(notes)
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp))
                        }
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Shown once, however it was closed (not the preview).
        if (arguments?.getBoolean(ARG_PREVIEW, false) != true) {
            UpdatePreferences(requireContext()).clearJustUpdated()
        }
    }

    companion object {
        const val TAG = "UpdatedSheetFragment"
        private const val ARG_PREVIEW = "preview"

        /** The sheet with sample text, for Developer options. */
        @JvmStatic
        fun preview(): UpdatedSheetFragment =
            UpdatedSheetFragment().apply { arguments = Bundle().apply { putBoolean(ARG_PREVIEW, true) } }

        /** True when the running build is the one an update was just installed for. */
        @JvmStatic
        fun shouldShow(context: android.content.Context): Boolean =
            UpdatePreferences(context).justUpdatedMatchesRunning(BuildConfig.VERSION_NAME)
    }
}
