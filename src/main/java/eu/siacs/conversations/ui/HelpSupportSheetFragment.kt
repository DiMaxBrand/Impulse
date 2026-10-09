package eu.siacs.conversations.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.activity.UpdatesActivity

/**
 * "Help and support", opened from the three-dot menu on the main screen and in every chat. For
 * the people who get stuck and don't know where to look: each row fixes a common problem
 * directly (a permission the phone switched off, notification sounds, a stuck update) instead of
 * explaining it, plus one button that copies the app and phone details to send to whoever helps.
 */
class HelpSupportSheetFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ImpulseExpressiveTheme {
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Hero icon and title.
                        Icon(
                            painterResource(R.drawable.ic_help_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(48.dp).padding(bottom = 4.dp),
                        )
                        Text(
                            text = stringResource(R.string.help_and_support),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.help_sheet_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            HelpRow(
                                GroupPosition.TOP,
                                R.drawable.ic_call_24dp,
                                R.string.help_calls_title,
                                R.string.help_calls_summary,
                                ::fixCalls,
                            )
                            HelpRow(
                                GroupPosition.MIDDLE,
                                R.drawable.ic_notifications_24dp,
                                R.string.help_sounds_title,
                                R.string.help_sounds_summary,
                                ::openSoundSetup,
                            )
                            HelpRow(
                                GroupPosition.MIDDLE,
                                R.drawable.ic_system_update_24dp,
                                R.string.help_updates_title,
                                R.string.help_updates_summary,
                                ::openUpdates,
                            )
                            HelpRow(
                                GroupPosition.BOTTOM,
                                R.drawable.ic_info_outline_24dp,
                                R.string.help_copy_title,
                                R.string.help_copy_summary,
                                ::copyDeviceInfo,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }

    @Composable
    private fun HelpRow(
        position: GroupPosition,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes summary: Int,
        onClick: () -> Unit,
    ) {
        ExpressiveGroupRow(position) {
            ListItem(
                leadingContent = {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                headlineContent = { Text(stringResource(title)) },
                supportingContent = { Text(stringResource(summary)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    onClick()
                },
            )
        }
    }

    private fun fixCalls() {
        val intent = CallPermissions.fixIntent(requireContext())
        if (intent == null) {
            Toast.makeText(requireContext(), R.string.help_calls_all_good, Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(intent)
            dismiss()
        } catch (_: RuntimeException) {
            startActivity(Intent(requireContext(), NotificationSetupActivity::class.java))
            dismiss()
        }
    }

    private fun openSoundSetup() {
        startActivity(Intent(requireContext(), NotificationSetupActivity::class.java))
        dismiss()
    }

    private fun openUpdates() {
        startActivity(Intent(requireContext(), UpdatesActivity::class.java))
        dismiss()
    }

    private fun copyDeviceInfo() {
        val info =
            "Impulse ${BuildConfig.VERSION_NAME}\n" +
                "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n" +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Impulse", info))
        Toast.makeText(requireContext(), R.string.help_copy_done, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val TAG = "HelpSupportSheet"
    }
}
