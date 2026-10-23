package eu.siacs.conversations.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.update.BugReportRegistry

/**
 * "Track for this person", the support-only card opened from a chat's attach menu (see
 * [eu.siacs.conversations.update.SupportTracking]). It grows out of the bottom-left corner, where
 * the paperclip is, while the rest of the screen dims; what it asks for is what the person will see
 * on their Updates screen in place of the generic "waiting for a fix": what kind of thing it is, and
 * a title in English and in Russian (their phone shows the one for its language).
 */
@Composable
internal fun TrackItemOverlay(
    visible: Boolean,
    contactName: String,
    onDismiss: () -> Unit,
    onSend: (kind: String, titleEn: String, titleRu: String, note: String, checkNow: Boolean) -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = 1600f)),
        exit = fadeOut(spring(stiffness = 1600f)),
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier =
                    Modifier.padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .animateEnterExit(
                            enter =
                                scaleIn(
                                    transformOrigin = TransformOrigin(0.1f, 1f),
                                    animationSpec = spring(stiffness = 380f, dampingRatio = 0.8f),
                                ) + fadeIn(spring(stiffness = 900f)),
                            exit =
                                scaleOut(
                                    transformOrigin = TransformOrigin(0.1f, 1f),
                                    animationSpec = spring(stiffness = 380f, dampingRatio = 0.8f),
                                ) + fadeOut(spring(stiffness = 900f)),
                        )
                        // Touches inside the card must not dismiss it.
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {},
            ) {
                TrackItemContent(contactName, onDismiss, onSend)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackItemContent(
    contactName: String,
    onDismiss: () -> Unit,
    onSend: (kind: String, titleEn: String, titleRu: String, note: String, checkNow: Boolean) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(BugReportRegistry.KIND_BUG) }
    var titleEn by rememberSaveable { mutableStateOf("") }
    var titleRu by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var checkNow by rememberSaveable { mutableStateOf(true) }
    Column(
        modifier =
            Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_report_24dp),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.track_title, contactName),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.track_text),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (k in BugReportRegistry.KINDS) {
                FilterChip(
                    selected = kind == k,
                    onClick = { kind = k },
                    label = {
                        Text(
                            stringResource(
                                when (k) {
                                    BugReportRegistry.KIND_IMPROVEMENT -> R.string.tracked_kind_improvement
                                    BugReportRegistry.KIND_FEATURE -> R.string.tracked_kind_feature
                                    else -> R.string.tracked_kind_bug
                                }
                            )
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = titleEn,
            onValueChange = { titleEn = it },
            label = { Text(stringResource(R.string.track_field_en)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = titleRu,
            onValueChange = { titleRu = it },
            label = { Text(stringResource(R.string.track_field_ru)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.track_field_note)) },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.track_check_now),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checkNow, onCheckedChange = { checkNow = it })
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSend(kind, titleEn.trim(), titleRu.trim(), note.trim(), checkNow) },
            enabled = titleEn.isNotBlank() || titleRu.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.track_send))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(android.R.string.cancel),
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.72f),
            )
        }
    }
}
