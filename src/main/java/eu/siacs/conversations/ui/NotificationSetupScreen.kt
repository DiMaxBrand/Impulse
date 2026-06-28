package eu.siacs.conversations.ui

import android.app.NotificationManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context) }

    fun isHyperOs(): Boolean {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java, String::class.java)
            val v = get.invoke(null, "ro.mi.os.version.name", "") as String
            v.isNotEmpty()
        } catch (_: Exception) { false }
    }

    fun channelHasNoSound(channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        // HyperOS lies: reports a non-empty URI even when the user has set None.
        // Trust the channel on non-HyperOS devices only.
        if (isHyperOs()) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        val sound = nm.getNotificationChannel(channelId)?.sound ?: return true
        return sound == Uri.EMPTY || sound.toString().isEmpty()
    }

    fun ringtoneTitle(uri: Uri?): String? {
        uri ?: return null
        return try {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        } catch (_: Exception) { null }
    }

    // Determine workaround need once and store it persistently.
    val callNeedsWorkaround = remember {
        if (appSettings.isWorkaroundCallsEnabled()) true
        else channelHasNoSound("incoming_calls_channel#0").also { needed ->
            if (needed) appSettings.setWorkaroundCallsEnabled(true)
        }
    }
    val anyWorkaround = callNeedsWorkaround

    var callSoundUri by remember {
        mutableStateOf(
            appSettings.getWorkaroundCallSound()
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )
    }

    val callRingtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            callSoundUri = picked ?: callSoundUri
            if (callNeedsWorkaround) {
                appSettings.setWorkaroundCallSound(picked)
            } else {
                appSettings.setRingtone(picked)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    eu.siacs.conversations.services.NotificationService
                        .recreateIncomingCallChannel(context, picked)
                }
            }
        }
    }

    val expressiveFontFamily = remember {
        FontFamily(
            Font(
                R.font.google_sans_flex,
                weight = FontWeight.Bold,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(700),
                    FontVariation.Setting("ROND", 100f)
                )
            )
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Title
            Text(
                text = stringResource(R.string.notification_setup_title),
                style = MaterialTheme.typography.headlineMediumEmphasized.copy(
                    fontFamily = expressiveFontFamily,
                    fontWeight = FontWeight.Bold
                )
            )

            // Description
            Text(
                text = stringResource(
                    if (anyWorkaround) R.string.notification_setup_description_workaround
                    else R.string.notification_setup_description
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Cards
            NotificationSetupCard(
                title = stringResource(R.string.notification_setup_call_ringtone_title),
                description = stringResource(R.string.notification_setup_call_ringtone_description),
                buttonLabel = stringResource(R.string.notification_setup_choose_ringtone),
                soundName = ringtoneTitle(callSoundUri),
                isSet = callSoundUri != null,
                isFirst = true,
                isLast = false,
                onClick = {
                    val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        callSoundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it) }
                    }
                    callRingtoneLauncher.launch(intent)
                }
            )

            NotificationSetupCard(
                title = stringResource(R.string.notification_setup_message_sound_title),
                description = stringResource(R.string.notification_setup_message_sound_description),
                buttonLabel = stringResource(R.string.notification_setup_open_settings),
                soundName = null,
                isSet = false,
                isFirst = false,
                isLast = true,
                onClick = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, "messages")
                    }
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    appSettings.setNotificationSetupDone()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.notification_setup_done))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotificationSetupCard(
    title: String,
    description: String,
    buttonLabel: String,
    soundName: String?,
    isSet: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val topCorner = if (isFirst) 28.dp else 8.dp
    val bottomCorner = if (isLast) 28.dp else 8.dp

    val scale by animateFloatAsState(
        targetValue = if (isSet) 1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = topCorner,
            topEnd = topCorner,
            bottomStart = bottomCorner,
            bottomEnd = bottomCorner
        ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.weight(1f)
                )
                if (isSet) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_check_circle_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (soundName != null) {
                    Text(
                        text = soundName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                FilledTonalButton(onClick = onClick) {
                    Text(buttonLabel)
                }
            }
        }
    }
}
