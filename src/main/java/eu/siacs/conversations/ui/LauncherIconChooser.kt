package eu.siacs.conversations.ui

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import eu.siacs.conversations.R
import kotlinx.coroutines.delay

@Composable
internal fun LauncherIconChooser(onPreview: (Int?) -> Unit) {
    val context = LocalContext.current
    val manager = context.packageManager
    val conversations = remember { ComponentName(context, "eu.siacs.conversations.ui.ConversationActivity") }
    val impulse = remember { ComponentName(context, "eu.siacs.conversations.ui.ImpulseLauncherIcon") }
    var selected by remember { mutableStateOf(manager.getComponentEnabledSetting(impulse) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) }
    fun select(useImpulse: Boolean) {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("launcher_icon_explicit", true).apply()
        if (selected == useImpulse) return
        val enable = if (useImpulse) impulse else conversations
        val disable = if (useImpulse) conversations else impulse
        if (Build.VERSION.SDK_INT >= 33) {
            manager.setComponentEnabledSettings(listOf(
                PackageManager.ComponentEnabledSetting(enable, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP),
                PackageManager.ComponentEnabledSetting(disable, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP),
            ))
        } else {
            manager.setComponentEnabledSetting(enable, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            manager.setComponentEnabledSetting(disable, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        selected = useImpulse
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp)) {
        Text(stringResource(R.string.launcher_icon_hint), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(Triple(false, R.drawable.conversations_icon_art, R.string.launcher_icon_conversations), Triple(true, R.drawable.impulse_icon_art, R.string.launcher_icon_impulse)).forEach { (isImpulse, image, label) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics { onClick { select(isImpulse); true } }.pointerInput(isImpulse) {
                    detectTapGestures(onPress = { try { tryAwaitRelease() } finally { onPreview(null) } }, onLongPress = { onPreview(image) }, onTap = { select(isImpulse) })
                }.padding(8.dp)) {
                    Image(painterResource(image), contentDescription = stringResource(label), modifier = Modifier.size(80.dp))
                    Text(stringResource(label), color = if (selected == isImpulse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (selected == isImpulse) Text("✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
