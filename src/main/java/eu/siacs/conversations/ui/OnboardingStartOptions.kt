package eu.siacs.conversations.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R

/** The unselected state of the account card transform. */
@Composable
internal fun OnboardingStartOptions(
    onCreate: () -> Unit,
    onLogIn: () -> Unit,
    onRestore: () -> Unit,
    onQr: () -> Unit,
    onCertificate: () -> Unit,
    certificateEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        RotatableOnboardingLogo()
        Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.displaySmall)
        Text(
            stringResource(R.string.welcome_intro_body),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(2.dp))
        OnboardingChoiceCard(
            title = stringResource(R.string.create_account),
            detail = stringResource(R.string.onboarding_create_account_detail),
            emphasized = true,
            onClick = onCreate,
        )
        OnboardingChoiceCard(
            title = stringResource(R.string.log_in),
            detail = stringResource(R.string.onboarding_log_in_detail),
            onClick = onLogIn,
        )
        Text(
            stringResource(R.string.onboarding_other_start_options),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExpressiveGroupRow(GroupPosition.TOP) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.restore_backup)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onRestore),
            )
        }
        Spacer(Modifier.size(2.dp))
        ExpressiveGroupRow(GroupPosition.MIDDLE) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.scan_qr_code)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onQr),
            )
        }
        Spacer(Modifier.size(2.dp))
        ExpressiveGroupRow(GroupPosition.BOTTOM) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.action_add_account_with_certificate)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(enabled = certificateEnabled, onClick = onCertificate),
            )
        }
    }
}
