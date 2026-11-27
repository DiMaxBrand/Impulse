package eu.siacs.conversations.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R

/** Server-provided registration challenge. Dismissal tells the connection to cancel registration. */
@Composable
internal fun OnboardingCaptcha(bitmap: Bitmap, onAnswer: (String) -> Unit, onCancel: () -> Unit) {
    var answer by remember(bitmap) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.captcha_required)) },
        text = {
            Column {
                Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
                OutlinedTextField(value = answer, onValueChange = { answer = it }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(answer) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
