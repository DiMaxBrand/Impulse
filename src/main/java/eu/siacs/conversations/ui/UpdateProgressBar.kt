package eu.siacs.conversations.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.update.UpdateDownloader
import eu.siacs.conversations.update.UpdatePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed interface UpdateBarState {
    data class Downloading(val version: String?, val fraction: Float) : UpdateBarState
    data class Ready(val version: String?) : UpdateBarState
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UpdateProgressBar(onTap: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { UpdatePreferences(context) }
    var barState by remember { mutableStateOf<UpdateBarState?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val downloadId = prefs.activeDownloadId
            val version = prefs.downloadedVersion ?: prefs.pendingUpdateVersion
            barState = when {
                downloadId != -1L -> {
                    val progress = withContext(Dispatchers.IO) {
                        UpdateDownloader.queryProgress(context, downloadId)
                    }
                    when (progress) {
                        is UpdateDownloader.DownloadProgress.InProgress ->
                            UpdateBarState.Downloading(version, progress.fraction)
                        is UpdateDownloader.DownloadProgress.Complete -> {
                            withContext(Dispatchers.IO) {
                                prefs.downloadedVersion = prefs.pendingUpdateVersion
                                prefs.downloadedApkPath = progress.localUri
                                prefs.activeDownloadId = -1L
                                prefs.clearPending()
                            }
                            UpdateBarState.Ready(prefs.downloadedVersion)
                        }
                        else -> barState // keep current state; transient failure
                    }
                }
                prefs.downloadedApkExists() -> UpdateBarState.Ready(version)
                else -> null
            }
            delay(500L)
        }
    }

    AnimatedVisibility(
        visible = barState != null,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            initialOffsetY = { it },
        ),
        exit = slideOutVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            targetOffsetY = { it },
        ),
    ) {
        val state = barState ?: return@AnimatedVisibility
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTap),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            when (state) {
                                is UpdateBarState.Ready -> R.drawable.ic_system_update_24dp
                                is UpdateBarState.Downloading -> R.drawable.ic_download_24dp
                            }
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            text = when (state) {
                                is UpdateBarState.Ready -> "Ready to install"
                                is UpdateBarState.Downloading -> {
                                    val pct = (state.fraction * 100).toInt()
                                    if (pct > 0) "Downloading · $pct%" else "Downloading update"
                                }
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        val version = when (state) {
                            is UpdateBarState.Ready -> state.version
                            is UpdateBarState.Downloading -> state.version
                        }
                        if (version != null) {
                            Text(
                                text = "Impulse $version",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(180f), // points up — tap to expand the sheet above
                    )
                }
                when (state) {
                    is UpdateBarState.Downloading ->
                        if (state.fraction > 0f) {
                            LinearWavyProgressIndicator(
                                progress = { state.fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    is UpdateBarState.Ready ->
                        LinearWavyProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

object UpdateProgressBarSetup {
    @JvmStatic
    fun setup(view: ComposeView, onTap: Runnable) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            ImpulseExpressiveTheme {
                UpdateProgressBar(onTap = { onTap.run() })
            }
        }
    }
}
