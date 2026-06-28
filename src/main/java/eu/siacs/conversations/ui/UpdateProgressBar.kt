package eu.siacs.conversations.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
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
        if (fraction > 0f) {
            LinearWavyProgressIndicator(progress = { fraction })
        } else {
            LinearWavyProgressIndicator()
        }
    }
}
