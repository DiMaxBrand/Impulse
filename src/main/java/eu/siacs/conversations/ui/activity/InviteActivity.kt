package eu.siacs.conversations.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.ActionBarActivity
import eu.siacs.conversations.ui.ImpulseExpressiveTheme

/**
 * Behind [eu.siacs.conversations.FeatureFlag.INVITE_CONTACTS]. Shares a direct-download link to
 * the latest *stable* release's universal APK — GitHub's `/releases/latest/download/<file>`
 * redirect only resolves once a stable release actually exists, which is also why the flag
 * defaults off (see the flag's own doc comment).
 */
class InviteActivity : ActionBarActivity() {

    @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ImpulseExpressiveTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.invite_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_arrow_back_24dp),
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        // Reassurance, not a warning — leads the screen so it's read before the
                        // link, not stumbled into mid-install on the friend's end.
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
            }
        }
    }

    companion object {
        // /releases/latest only ever resolves against the most recent non-prerelease (stable)
        // release — matches the flag's own "not finished, blocked on a stable release existing"
        // rationale exactly, rather than being an unrelated coincidence.
        const val INVITE_APK_URL =
            "https://github.com/DiMaxBrand/Impulse/releases/latest/download/Impulse_universal.apk"
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
            Button(
                onClick = { shareInviteLink(context, inviteUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.invite_share_button))
            }
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

private fun shareInviteLink(context: Context, url: String) {
    val message = context.getString(R.string.invite_share_message, url)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, context.getText(R.string.share_with)))
}
