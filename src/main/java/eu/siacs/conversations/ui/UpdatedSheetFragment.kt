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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.R
import eu.siacs.conversations.update.UpdatePreferences

/**
 * "Impulse was updated!" -- shown on the first launch after an update (by the nightly one, or
 * the manual button), with the release title and notes of what was just installed. A dismiss
 * button sits at the top. Dismissing in any way forgets the update, so it shows once.
 */
class UpdatedSheetFragment : BottomSheetDialogFragment() {

    private lateinit var prefs: UpdatePreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        prefs = UpdatePreferences(requireContext())
        val title = prefs.justUpdatedTitle
        val notes = prefs.justUpdatedNotes
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ImpulseExpressiveTheme {
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
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

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Shown once, however it was closed.
        UpdatePreferences(requireContext()).clearJustUpdated()
    }

    companion object {
        const val TAG = "UpdatedSheetFragment"

        /** True when the running build is the one an update was just installed for. */
        @JvmStatic
        fun shouldShow(context: android.content.Context): Boolean =
            UpdatePreferences(context).justUpdatedMatchesRunning(BuildConfig.VERSION_NAME)
    }
}
