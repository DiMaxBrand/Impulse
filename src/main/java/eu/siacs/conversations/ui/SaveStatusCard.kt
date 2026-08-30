package eu.siacs.conversations.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R

/**
 * The elevated card explaining what the save/download affordance actually did (or would do) —
 * shared between the media viewer's top-bar button and the message context sheet's "Save file"
 * row, both of which used to silently no-op once the file was already in shared storage ("Save
 * to Gallery" on, the default) with zero feedback. [heroIconRes] is always the exact icon that
 * was tapped to open this card (download-rounded in the viewer, save in the sheet), so the card
 * reads as a continuation of that same affordance rather than a generic dialog.
 */
@Composable
fun SaveStatusCardContent(
    heroIconRes: Int,
    alreadySaved: Boolean,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(heroIconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
        Text(
            text = stringResource(
                if (alreadySaved) R.string.save_card_already_saved else R.string.save_card_not_saved,
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
        if (alreadySaved) {
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.ok))
            }
        } else {
            Button(onClick = { onSave(); onClose() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save_file))
            }
        }
    }
}
