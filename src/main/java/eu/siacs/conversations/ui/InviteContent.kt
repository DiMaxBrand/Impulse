package eu.siacs.conversations.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R

// /releases/latest only ever resolves against the most recent non-prerelease (stable) release —
// matches the flag's own "not finished, blocked on a stable release existing" rationale exactly,
// rather than being an unrelated coincidence. The asset is the arm64 build: that's the only APK
// the release workflow actually publishes (a universal one doesn't exist on any release), and
// arm64 covers essentially every current phone.
const val INVITE_APK_URL =
    "https://github.com/DiMaxBrand/Impulse/releases/latest/download/Impulse_arm64.apk"

/**
 * The card behind [eu.siacs.conversations.FeatureFlag.INVITE_CONTACTS]. Lives inline in
 * [StartConversationScreen] (grown from the FAB's "Invite" item via a shared-bounds container
 * transform) rather than as its own Activity — that's what a cross-screen morph needs, since
 * true Compose shared-element continuity only works within one Activity's Compose tree.
 *
 * This is *content* for a floating elevated card, not a full screen — same "Add Contact"-style
 * card treatment, not a Scaffold/TopAppBar takeover. The caller supplies the Surface/Card shell
 * (see the destination half of the transform in StartConversationScreen.kt) so this stays
 * reusable as just the inside of it.
 */
@Composable
fun InviteScreenContent(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.invite_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_24dp),
                    contentDescription = stringResource(R.string.cancel),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Reassurance, not a warning — leads the content so it's read before the link, not
        // stumbled into mid-install on the invited person's end.
        Text(
            text = stringResource(R.string.invite_play_protect_note),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        InviteCard(inviteUrl = INVITE_APK_URL)

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.invite_install_steps_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        // Sets expectations before the per-browser steps below: the "allow installs from this
        // source" prompt (and on some phones, its greyed-out-for-10-seconds confirm button) is
        // routine here, not something specific to Chrome or Samsung Internet individually.
        Text(
            text = stringResource(R.string.invite_install_permission_heads_up),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        InstallStep(
            title = stringResource(R.string.invite_install_chrome_title),
            steps = stringResource(R.string.invite_install_chrome_steps),
        )
        Spacer(Modifier.height(12.dp))
        InstallStep(
            title = stringResource(R.string.invite_install_samsung_title),
            steps = stringResource(R.string.invite_install_samsung_steps),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.invite_install_unknown_sources),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InviteCard(inviteUrl: String) {
    val context = LocalContext.current
    Card(colors = CardDefaults.elevatedCardColors(), elevation = CardDefaults.elevatedCardElevation()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.invite_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = inviteUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { copyInviteLink(context, inviteUrl) }) {
                    // Same icon this codebase already uses to represent "copy" on the message
                    // context sheet (there's no dedicated copy glyph in the drawable set).
                    Icon(
                        painter = painterResource(R.drawable.ic_description_24dp),
                        contentDescription = stringResource(android.R.string.copy),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Tapping "Share invite link" expands in place into one button per message language
            // (English / Русский); the language the app is currently in goes first (left) as the
            // filled primary option, the other one beside it as the tonal secondary. Picking one
            // shares the invite in THAT language, whatever the app's own language is.
            var languagePickerOpen by remember { mutableStateOf(false) }
            val appLanguage = context.resources.configuration.locales[0].language
            val languages = if (appLanguage == "ru") listOf("ru", "en") else listOf("en", "ru")
            AnimatedContent(
                targetState = languagePickerOpen,
                transitionSpec = {
                    (fadeIn(spring(stiffness = 1600f, dampingRatio = 1f)) togetherWith
                        fadeOut(spring(stiffness = 1600f, dampingRatio = 1f)))
                        .using(
                            SizeTransform(clip = false) { _, _ ->
                                spring(stiffness = 380f, dampingRatio = 0.8f)
                            }
                        )
                },
                label = "inviteShareExpand",
            ) { open ->
                if (!open) {
                    Button(
                        onClick = { languagePickerOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.invite_share_button))
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        languages.forEachIndexed { index, tag ->
                            val label = stringResource(
                                if (tag == "ru") R.string.invite_language_ru else R.string.invite_language_en
                            )
                            val onClick = {
                                languagePickerOpen = false
                                shareInviteLink(context, inviteUrl, tag)
                            }
                            if (index == 0) {
                                Button(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label) }
                            } else {
                                FilledTonalButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Right below the button, on purpose — this is the moment someone's about to pick a
            // share target, and it needs to be visible without scrolling. Deliberately not a
            // hardcoded segment count: that number depends on the exact message text (locale,
            // whichever non-ASCII character happens to be in it — even the EN string forces
            // Unicode SMS encoding via a single em dash), which has already changed several
            // times and will again; a stale specific number would be worse than a general one.
            Text(
                text = stringResource(R.string.invite_sms_warning),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun InstallStep(title: String, steps: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = steps,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun copyInviteLink(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("invite_link", url))
    Toast.makeText(context, R.string.invite_link_copied, Toast.LENGTH_SHORT).show()
}

/** [languageTag] ("en"/"ru") is the language of the MESSAGE, which can differ from the app's own:
 * the string is resolved against a copy of the configuration with just that locale swapped in. */
private fun shareInviteLink(context: Context, url: String, languageTag: String) {
    val localized = Configuration(context.resources.configuration).apply {
        setLocale(java.util.Locale.forLanguageTag(languageTag))
    }
    val message = context.createConfigurationContext(localized)
        .getString(R.string.invite_share_message, url)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, context.getText(R.string.share_with)))
}
