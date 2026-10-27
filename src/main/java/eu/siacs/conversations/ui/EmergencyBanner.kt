package eu.siacs.conversations.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.EmergencyMode
import eu.siacs.conversations.R

/**
 * Shows that emergency mode is on (the connection is weak or failing, so the app waits longer and
 * leaves out avatars and automatic downloads), with a button to turn it off by hand. It turns
 * itself off once the connection has been stable for a while. Nothing is drawn while it is off.
 */
@Composable
fun EmergencyBanner(modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = EmergencyMode.active,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(painterResource(R.drawable.ic_warning_24dp), contentDescription = null, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.emergency_mode_banner_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.emergency_mode_banner_text), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { EmergencyMode.dismiss() }) {
                    Text(stringResource(R.string.emergency_mode_turn_off))
                }
            }
        }
    }
}
