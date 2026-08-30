package eu.siacs.conversations.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.update.UpdateDownloader
import eu.siacs.conversations.update.UpdatePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadProgressBar(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { UpdatePreferences(context) }

    var visible by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val downloadId = prefs.activeDownloadId
            if (downloadId != -1L) {
                val progress = withContext(Dispatchers.IO) {
                    UpdateDownloader.queryProgress(context, downloadId)
                }
                when (progress) {
                    is UpdateDownloader.DownloadProgress.InProgress -> {
                        visible = true
                        fraction = progress.fraction
                        statusText = progress.statusText
                    }
                    is UpdateDownloader.DownloadProgress.Complete -> {
                        withContext(Dispatchers.IO) {
                            prefs.downloadedVersion = prefs.pendingUpdateVersion
                            prefs.downloadedApkPath = progress.localUri
                            prefs.activeDownloadId = -1L
                            prefs.clearPending()
                        }
                        visible = false
                        onComplete()
                    }
                    is UpdateDownloader.DownloadProgress.Failed -> {
                        visible = true
                        fraction = 0f
                        statusText = progress.reasonText
                        delay(4000L)
                        prefs.activeDownloadId = -1L
                        visible = false
                    }
                    else -> visible = false
                }
            } else {
                visible = false
            }
            delay(500L)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Column {
            statusText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            if (fraction > 0f) {
                LinearWavyProgressIndicator(progress = { fraction })
            } else {
                LinearWavyProgressIndicator()
            }
        }
    }
}
