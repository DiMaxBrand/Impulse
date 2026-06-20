package eu.siacs.conversations.ui

import android.media.RingtoneManager
import android.net.Uri
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context) }

    var callSoundUri by remember { mutableStateOf<Uri?>(appSettings.getWorkaroundCallSound()) }
    var messageSoundUri by remember { mutableStateOf<Uri?>(appSettings.getWorkaroundMessageSound()) }

    val callRingtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        callSoundUri = uri
        appSettings.setWorkaroundCallSound(uri)
    }

    val messageRingtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        messageSoundUri = uri
        appSettings.setWorkaroundMessageSound(uri)
    }

    val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )
    val googleSansFlex = GoogleFont("Google Sans Flex")
    val expressiveFontFamily = FontFamily(
        Font(
            googleFont = googleSansFlex,
            fontProvider = provider,
            weight = FontWeight.Bold
        )
    )

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
                text = stringResource(R.string.notification_setup_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Cards
            NotificationSetupCard(
                title = stringResource(R.string.notification_setup_call_ringtone_title),
                description = stringResource(R.string.notification_setup_call_ringtone_description),
                buttonLabel = if (callSoundUri != null)
                    stringResource(R.string.notification_setup_sound_set)
                else
                    stringResource(R.string.notification_setup_choose_ringtone),
                isSet = callSoundUri != null,
                isFirst = true,
                isLast = false,
                onClick = {
                    val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        callSoundUri?.let {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
                        }
                    }
                    callRingtoneLauncher.launch(intent)
                }
            )

            NotificationSetupCard(
                title = stringResource(R.string.notification_setup_message_sound_title),
                description = stringResource(R.string.notification_setup_message_sound_description),
                buttonLabel = if (messageSoundUri != null)
                    stringResource(R.string.notification_setup_sound_set)
                else
                    stringResource(R.string.notification_setup_choose_sound),
                isSet = messageSoundUri != null,
                isFirst = false,
                isLast = true,
                onClick = {
                    val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        messageSoundUri?.let {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
                        }
                    }
                    messageRingtoneLauncher.launch(intent)
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
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(buttonLabel)
            }
        }
    }
}
