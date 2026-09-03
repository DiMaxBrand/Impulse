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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.graphics.shapes.RoundedPolygon
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
    // Separate from callSoundUri (which always holds a value, falling back to the system
    // default) -- this tracks whether the user has actually picked/stored a sound, so the
    // card's checkmark doesn't claim "set" for a fallback default nobody chose.
    var callSoundIsUserSet by remember {
        mutableStateOf(appSettings.getWorkaroundCallSound() != null)
    }

    val callRingtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            callSoundUri = picked ?: callSoundUri
            callSoundIsUserSet = true
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
        Box(modifier = Modifier.fillMaxSize()) {
            // Purely decorative — three shapes, each cycling through the full MaterialShapes
            // catalog forever and rotating clockwise, scattered around the screen behind
            // everything else. Each one starts on a random shape at a random angle
            // (randomStart) so they never look synchronized, and their scatter positions are
            // themselves randomized once per screen visit rather than fixed — a deliberate
            // "might land a little differently every time" choice over a hand-placed layout.
            val shapePolygons = remember { MATERIAL_SHAPE_CATALOG.map { it.second } }
            val scatter = remember {
                List(3) {
                    Triple(
                        (-40..40).random().dp,
                        (-40..40).random().dp,
                        (14000..30000).random(),
                    )
                }
            }
            DecorativeShapeBackdrop(
                shapes = shapePolygons,
                alignment = Alignment.TopEnd,
                baseOffsetX = 120.dp,
                baseOffsetY = (-120).dp,
                jitter = scatter[0],
                size = 340.dp,
                alpha = 0.10f,
            )
            // Left-side shapes were previously cropped by ~90% (pushed almost entirely off the
            // left edge) -- only ~10% of each should hang off-screen now, so most of the shape
            // is actually visible instead of a barely-there sliver.
            DecorativeShapeBackdrop(
                shapes = shapePolygons,
                alignment = Alignment.BottomStart,
                baseOffsetX = (-24).dp,
                baseOffsetY = 100.dp,
                jitter = scatter[1],
                size = 240.dp,
                alpha = 0.08f,
            )
            // Moved out of dead-center (where it competed with the title/description/cards for
            // attention) into the empty space below the cards and above the Done button, on the
            // right side -- fully on-screen, not edge-cropped like the two above.
            DecorativeShapeBackdrop(
                shapes = shapePolygons,
                alignment = Alignment.BottomEnd,
                baseOffsetX = (-32).dp,
                baseOffsetY = (-140).dp,
                jitter = scatter[2],
                size = 150.dp,
                alpha = 0.07f,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Title. Two GlyphFilledTitle attempts shipped a solid black bar on-device,
                // both diagnosed and fixed differently -- and both failed identically. Root
                // cause found: TextLayoutResult.getPathForRange() is the *selection-highlight*
                // API (the same one used to draw text-selection background rectangles), not a
                // glyph-outline API -- it was always going to return a filled bounding box, no
                // matter how/when it was measured. Real glyph outlines need a lower-level API
                // this hasn't been attempted with yet (Paint.getTextPath() against a manually
                // built Typeface with the same FontVariation settings) -- a real chunk of work,
                // not a one-line fix, so reverted to plain Text rather than guess a third time.
                // See GlyphFilledTitle's own doc in DecorativeMorphingShape.kt.
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

                // Full-screen call alerts (Android 14+ only): USE_FULL_SCREEN_INTENT is declared
                // in the manifest, but as of API 34 it can land in a denied state for sideloaded
                // apps with no runtime prompt at all -- the notification just silently degrades
                // to an ordinary heads-up alert instead of auto-launching the call screen (sound
                // plays, the call screen never appears until the notification is tapped
                // manually). Unlike everything else on this screen, this one has a real,
                // documented settings deep-link, so it's worth surfacing here.
                val fullScreenIntentGranted = remember {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        androidx.core.app.NotificationManagerCompat.from(context)
                            .canUseFullScreenIntent()
                    } else {
                        true
                    }
                }

                // Display over other apps (SYSTEM_ALERT_WINDOW) -- unlike the OEM-only toggles
                // (HyperOS's "Show on Lock screen", MIUI's "Display pop-up windows", etc.), this
                // one is real, standard AOSP: available since API 23, with a genuine public API
                // to check (Settings.canDrawOverlays) and a genuine deep-link to request it
                // (ACTION_MANAGE_OVERLAY_PERMISSION). Declared in the manifest but never actually
                // checked or requested anywhere until now -- some OEMs (MIUI/HyperOS among them,
                // by report) gate pop-up call UI behind it as an extra condition on top of
                // Android's own full-screen-intent mechanism.
                val overlayGranted = remember {
                    android.provider.Settings.canDrawOverlays(context)
                }

                // Cards -- grouped tightly (2dp, matching the app's other grouped-list rows)
                // rather than the outer Column's 24dp spacedBy, so they read as one merged pair
                // (top card rounds only its top corners, bottom card only its bottom corners)
                // instead of two separate cards with a large gap between them.
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    NotificationSetupCard(
                        title = stringResource(R.string.notification_setup_call_ringtone_title),
                        description = stringResource(R.string.notification_setup_call_ringtone_description),
                        buttonLabel = stringResource(R.string.notification_setup_choose_ringtone),
                        soundName = ringtoneTitle(callSoundUri),
                        isSet = callSoundIsUserSet,
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
                        isLast = fullScreenIntentGranted && overlayGranted,
                        onClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, "messages")
                            }
                            context.startActivity(intent)
                        }
                    )

                    if (!fullScreenIntentGranted) {
                        NotificationSetupCard(
                            title = stringResource(R.string.notification_setup_full_screen_intent_title),
                            description = stringResource(R.string.notification_setup_full_screen_intent_description),
                            buttonLabel = stringResource(R.string.notification_setup_open_settings),
                            soundName = null,
                            isSet = false,
                            isFirst = false,
                            isLast = overlayGranted,
                            onClick = {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    android.net.Uri.parse("package:" + context.packageName),
                                )
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    // No activity handles this on some OEM skins -- nothing more
                                    // we can do than fall through silently.
                                }
                            }
                        )
                    }

                    if (!overlayGranted) {
                        NotificationSetupCard(
                            title = stringResource(R.string.notification_setup_overlay_title),
                            description = stringResource(R.string.notification_setup_overlay_description),
                            buttonLabel = stringResource(R.string.notification_setup_open_settings),
                            soundName = null,
                            isSet = false,
                            isFirst = false,
                            isLast = true,
                            onClick = {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:" + context.packageName),
                                )
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    // Some OEMs don't accept the package-scoped URI -- retry
                                    // with the bare, unscoped settings screen instead.
                                    try {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                                            )
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        )
                    }
                }

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
}

/** One scattered instance of [AutoMorphingShape] — [jitter] is a (dx, dy, rotationDurationMs)
 * triple so each backdrop shape gets its own small position wobble and its own rotation speed,
 * on top of the alignment/base-offset placement that positions it in the first place. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.DecorativeShapeBackdrop(
    shapes: List<RoundedPolygon>,
    alignment: Alignment,
    baseOffsetX: Dp,
    baseOffsetY: Dp,
    jitter: Triple<Dp, Dp, Int>,
    size: Dp,
    alpha: Float,
) {
    val (jitterX, jitterY, rotationDurationMs) = jitter
    AutoMorphingShape(
        shapes = shapes,
        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        randomStart = true,
        rotationDurationMs = rotationDurationMs,
        modifier = Modifier
            .size(size)
            .align(alignment)
            .offset(x = baseOffsetX + jitterX, y = baseOffsetY + jitterY),
    )
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
