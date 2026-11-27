package eu.siacs.conversations.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.update.UpdateChannel
import eu.siacs.conversations.ui.glass.LocalGlassSkin
import eu.siacs.conversations.ui.glass.GlassGroupRow

/** The caller persists choices and drives navigation; selecting a channel never advances a page. */
@Composable
internal fun OnboardingUpdateChannel(
    installedVersion: String,
    selected: UpdateChannel,
    onChoose: (channel: UpdateChannel, gradual: Boolean) -> Unit,
    onUpdateNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val installed = remember(installedVersion) { UpdateChannel.fromVersion(installedVersion) }
    var pending by remember { mutableStateOf<UpdateChannel?>(null) }
    var offerUpdate by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.onboarding_updates_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onboarding_updates_description), style = MaterialTheme.typography.bodyLarge)
        // Selecting a more stable channel deliberately replaces this content with a method
        // choice. Keeping it in one animated slot prevents a second card from being pushed below
        // the four channel choices on shorter phones.
        AnimatedContent(
            targetState = pending,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboarding_channel_method",
        ) { transitionTarget ->
            if (transitionTarget == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpdateChannel.entries.forEach { channel ->
                        val description = when (channel) {
                            UpdateChannel.STABLE -> R.string.update_channel_stable_description
                            UpdateChannel.RC -> R.string.update_channel_rc_description
                            UpdateChannel.BETA -> R.string.update_channel_beta_description
                            UpdateChannel.ALPHA -> R.string.update_channel_alpha_description
                        }
                        val choose = {
                            offerUpdate = false
                            if (channel == selected) {
                                pending = null
                            } else if (installed != null && channel.ordinal < installed.ordinal) {
                                pending = channel
                            } else {
                                onChoose(channel, false)
                                offerUpdate = installed != null && channel.ordinal > installed.ordinal
                            }
                        }
                        ChannelCard(channel, selected == channel, description, choose)
                    }
                    if (offerUpdate) {
                        Text(stringResource(R.string.onboarding_updates_offer), style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.material3.TextButton(onClick = { offerUpdate = false; onUpdateNow() }) {
                            Text(stringResource(R.string.updates_check_now))
                        }
                        androidx.compose.material3.TextButton(onClick = { offerUpdate = false }) {
                            Text(stringResource(R.string.onboarding_updates_later))
                        }
                    }
                }
            } else {
                val name = stringResource(channelDisplayName(transitionTarget))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.update_transition_title), style = MaterialTheme.typography.titleLarge)
                    TransitionCard(
                        title = stringResource(R.string.update_transition_keep),
                        detail = stringResource(R.string.update_transition_keep_detail, name),
                        onClick = { onChoose(transitionTarget, true); pending = null },
                    )
                    TransitionCard(
                        title = stringResource(R.string.update_transition_wait, name),
                        detail = stringResource(R.string.update_transition_wait_detail, name),
                        onClick = { onChoose(transitionTarget, false); pending = null },
                    )
                    androidx.compose.material3.TextButton(onClick = { pending = null }) {
                        Text(stringResource(R.string.back))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: UpdateChannel, selected: Boolean, description: Int, onClick: () -> Unit) {
    val cardContent: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp)) {
            Text((if (selected) "✓  " else "") + stringResource(channelDisplayName(channel)), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
        }
    }
    val color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val skin = LocalGlassSkin.current
    if (skin != null) {
        GlassGroupRow(skin, RoundedCornerShape(20.dp), color, Modifier.fillMaxWidth().clickable(onClick = onClick), cardContent)
    } else {
        Surface(shape = RoundedCornerShape(20.dp), color = color, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            cardContent()
        }
    }
}

@Composable
private fun TransitionCard(title: String, detail: String, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
    val skin = LocalGlassSkin.current
    if (skin != null) {
        GlassGroupRow(
            skin,
            RoundedCornerShape(20.dp),
            MaterialTheme.colorScheme.surfaceContainerHighest,
            Modifier.fillMaxWidth().clickable(onClick = onClick),
            content,
        )
    } else {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        ) { content() }
    }
}
