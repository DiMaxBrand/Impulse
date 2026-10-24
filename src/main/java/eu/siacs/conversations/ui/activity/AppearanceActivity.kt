package eu.siacs.conversations.ui.activity

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.ActionBarActivity
import eu.siacs.conversations.ui.AppearancePage
import eu.siacs.conversations.ui.AppearanceScreen
import eu.siacs.conversations.ui.AppearanceSettings
import eu.siacs.conversations.ui.AppearanceState
import eu.siacs.conversations.ui.ImpulseExpressiveTheme
import eu.siacs.conversations.ui.QuickReactionDialogFragment
import eu.siacs.conversations.ui.util.SettingsUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Compose replacement for the old "Interface" preference screen (+ its chat bubbles sub-screen). */
class AppearanceActivity : ActionBarActivity() {

    @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Writes the chosen style once (and moves anyone who had the old glass flag on over to it)
        // before the settings read it.
        eu.siacs.conversations.ui.LiquidGlass.style(this)
        val settings = AppearanceSettings(
            PreferenceManager.getDefaultSharedPreferences(this), resources)
        setContent {
            ImpulseExpressiveTheme {
                var page by remember { mutableStateOf(AppearancePage.MAIN) }
                // The screen follows the style that is chosen: glass when it is (and the phone can).
                // [celebrate] counts the times glass was just picked: each is a sweep of light.
                var celebrate by remember { mutableStateOf(0) }
                val glassOn = eu.siacs.conversations.ui.LiquidGlass.supported &&
                    settings.interfaceStyle.value == eu.siacs.conversations.ui.InterfaceStyle.GLASS.key
                BackHandler(enabled = page == AppearancePage.BUBBLES) { page = AppearancePage.MAIN }
                val pickImage = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()) { uri -> if (uri != null) importBackground(settings, uri) }
                eu.siacs.conversations.ui.glass.GlassSkinHost(
                    glass = glassOn,
                    celebrate = celebrate,
                    haptics = settings.haptics.value,
                ) {
                Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(stringResource(
                                    if (page == AppearancePage.BUBBLES) R.string.pref_chat_bubbles
                                    else R.string.appearance_screen_title))
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (page == AppearancePage.BUBBLES) page = AppearancePage.MAIN else finish()
                                }) {
                                    Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = null)
                                }
                            },
                            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        )
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        AppearanceScreen(
                            settings = settings,
                            page = page,
                            dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            onThemeChosen = { value ->
                                settings.theme.set(value)
                                setDesiredNightMode(AppSettings.getDesiredNightMode(value))
                            },
                            onDynamicChanged = { setDynamicColors(it) },
                            onAccentChanged = { recreate() },
                            onPickBackgroundImage = { pickImage.launch("image/*") },
                            onOpenQuickReactions = {
                                QuickReactionDialogFragment().show(supportFragmentManager, QuickReactionDialogFragment.TAG)
                            },
                            onScreenshotsChanged = { SettingsUtils.applyScreenshotSetting(this@AppearanceActivity) },
                            onOpenBubbles = { page = AppearancePage.BUBBLES },
                            onStyleChosen = { picked ->
                                if (picked == eu.siacs.conversations.ui.InterfaceStyle.GLASS) celebrate++
                            },
                        )
                    }
                }
                }
            }
        }
    }

    /** Copies the picked picture into app storage (downscaled), so it survives the source going away. */
    private fun importBackground(settings: AppearanceSettings, uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) sample *= 2
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                    val bmp = contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it, null, opts)
                    } ?: return@runCatching false
                    // A fresh file name each time so the Compose side reloads it.
                    val dir = File(filesDir, "chat_background").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val file = File(dir, "bg_${System.currentTimeMillis()}.jpg")
                    file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                    settings.backgroundImage.set(file.absolutePath)
                    true
                }.getOrDefault(false)
            }
            if (ok) settings.background.set("image")
            else Toast.makeText(this@AppearanceActivity, R.string.appearance_background_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
