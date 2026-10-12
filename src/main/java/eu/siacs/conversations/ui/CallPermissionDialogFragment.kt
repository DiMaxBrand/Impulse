package eu.siacs.conversations.ui

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import eu.siacs.conversations.R

/**
 * "Allow incoming calls to pop up": shown at most once a day when the phone has switched off a
 * permission the call pop-up needs (see [CallPermissions]). It asks about exactly the missing
 * one. A hero icon, one clearly highlighted action, a plain "Not now", and a quiet "Don't remind
 * me" -- present and tappable, just not inviting. The same switch is in Settings.
 */
class CallPermissionDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        val fix = CallPermissions.fixIntent(context)
        @StringRes
        val message =
            if (fix?.action == android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                R.string.call_permission_dialog_full_screen
            else R.string.call_permission_dialog_overlay
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ImpulseExpressiveTheme {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 6.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Hero icon.
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(72.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painterResource(R.drawable.ic_call_24dp),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(36.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.call_permission_dialog_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(24.dp))
                                // The one action that matters, highlighted.
                                Button(
                                    onClick = {
                                        openSettings(fix)
                                        dismiss()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                ) {
                                    Text(stringResource(R.string.call_permission_dialog_open))
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(onClick = { dismiss() }) {
                                        Text(stringResource(R.string.call_permission_dialog_later))
                                    }
                                    // Quiet: readable and tappable, but not drawing the eye.
                                    TextButton(
                                        onClick = {
                                            setReminderEnabled(false)
                                            dismiss()
                                        }
                                    ) {
                                        Text(
                                            text = stringResource(R.string.call_permission_dialog_never),
                                            color =
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.7f
                                                ),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            // The card draws its own background and shape.
            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun openSettings(fix: android.content.Intent?) {
        if (fix == null) return
        try {
            startActivity(fix)
        } catch (_: RuntimeException) {
            startActivity(android.content.Intent(requireContext(), NotificationSetupActivity::class.java))
        }
    }

    private fun setReminderEnabled(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit()
            .putBoolean(PREF_REMINDER, enabled)
            .apply()
    }

    companion object {
        const val TAG = "CallPermissionDialog"
        /** Settings switch and the dialog's "Don't remind me" share this preference. */
        const val PREF_REMINDER = "call_permission_reminder"
    }
}
