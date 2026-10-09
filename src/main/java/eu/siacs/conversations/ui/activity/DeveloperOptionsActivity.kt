package eu.siacs.conversations.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.ActionBarActivity
import eu.siacs.conversations.ui.ExpressiveGroupRow
import eu.siacs.conversations.ui.GroupPosition
import eu.siacs.conversations.ui.ImpulseExpressiveTheme
import eu.siacs.conversations.ui.UpdateSheetFragment
import eu.siacs.conversations.update.InstallStatusBus
import eu.siacs.conversations.update.SilentInstaller
import eu.siacs.conversations.update.UpdateChecker
import eu.siacs.conversations.update.UpdateInfo
import eu.siacs.conversations.update.UpdatePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class DeveloperOptionsActivity : ActionBarActivity() {

    @OptIn(
        ExperimentalMaterial3ExpressiveApi::class,
        ExperimentalMaterial3Api::class,
        ExperimentalSharedTransitionApi::class,
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ImpulseExpressiveTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.developer_options_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_arrow_back_24dp),
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    var pickerExpanded by remember { mutableStateOf(false) }
                    // null = not yet loaded (spinner); emptyList = loaded, nothing found
                    var releases by remember { mutableStateOf<List<UpdateInfo>?>(null) }
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(pickerExpanded) {
                        if (pickerExpanded && releases == null) {
                            releases = withContext(Dispatchers.IO) {
                                UpdateChecker(OkHttpClient()).listReleases()
                            }
                        }
                    }

                    // Scrim animates independently of the shared-element transition
                    val scrimAlpha by animateFloatAsState(
                        targetValue = if (pickerExpanded) 0.32f else 0f,
                        animationSpec = spring(stiffness = 1600f, dampingRatio = 1.0f),
                        label = "version_picker_scrim",
                    )
                    // Expansion gets medium bounce — high bounce (0.2) was overwhelming here,
                    // even by Expressive-showcase standards. Collapse back into the row uses the
                    // standard M3E spatial token so it settles quickly before the bottom sheet
                    // appears. pickerExpanded is already flipped when the bounds animation
                    // starts, so it doubles as the direction flag.
                    val pickerBoundsTransform = BoundsTransform { _, _ ->
                        spring(
                            stiffness = 380f,
                            dampingRatio =
                                if (pickerExpanded) Spring.DampingRatioMediumBouncy else 0.8f,
                        )
                    }

                    SharedTransitionLayout(modifier = Modifier.padding(padding).fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                            ) {
                                ExpressiveGroupRow(GroupPosition.TOP) {
                                    ListItem(
                                        headlineContent = {
                                            Text(stringResource(R.string.developer_options_reset_pause_timer))
                                        },
                                        supportingContent = {
                                            Text(stringResource(R.string.developer_options_reset_pause_timer_summary))
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.clickable {
                                            UpdatePreferences(this@DeveloperOptionsActivity)
                                                .sheetDismissedUntil = 0L
                                            Toast.makeText(
                                                this@DeveloperOptionsActivity,
                                                R.string.developer_options_reset_pause_timer_done,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                ExpressiveGroupRow(GroupPosition.BOTTOM) {
                                    ListItem(
                                        headlineContent = {
                                            Text(stringResource(R.string.developer_options_show_update_sheet))
                                        },
                                        supportingContent = {
                                            Text(stringResource(R.string.developer_options_show_update_sheet_summary))
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.clickable { showUpdateSheet() },
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                // Runs the nightly update's silent install on demand and reports
                                // what Android answered (silent success, "needs confirmation",
                                // or an error) -- for finding out what a given phone allows.
                                var silentTestRunning by remember { mutableStateOf(false) }
                                var silentTestResult by remember { mutableStateOf<String?>(null) }
                                LaunchedEffect(silentTestRunning) {
                                    if (!silentTestRunning) return@LaunchedEffect
                                    InstallStatusBus.consume()
                                    InstallStatusBus.last.collect { result ->
                                        if (result != null) {
                                            silentTestResult = describeInstallResult(result)
                                            silentTestRunning = false
                                        }
                                    }
                                }
                                ExpressiveGroupRow(GroupPosition.SINGLE) {
                                    ListItem(
                                        headlineContent = {
                                            Text(stringResource(R.string.developer_options_silent_update_test))
                                        },
                                        supportingContent = {
                                            Text(stringResource(R.string.developer_options_silent_update_test_summary))
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.clickable {
                                            val tried = SilentInstaller.testInstall(this@DeveloperOptionsActivity)
                                            if (tried == null) {
                                                silentTestResult = getString(R.string.developer_options_silent_update_test_not_started)
                                            } else {
                                                Toast.makeText(
                                                    this@DeveloperOptionsActivity,
                                                    getString(R.string.developer_options_silent_update_test_started, tried),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                                silentTestRunning = true
                                            }
                                        },
                                    )
                                }
                                silentTestResult?.let { text ->
                                    AlertDialog(
                                        onDismissRequest = { silentTestResult = null },
                                        title = { Text(stringResource(R.string.developer_options_silent_update_test)) },
                                        text = {
                                            Text(
                                                text,
                                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                            )
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { silentTestResult = null }) {
                                                Text(stringResource(android.R.string.ok))
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(
                                                onClick = {
                                                    val clipboard =
                                                        getSystemService(CLIPBOARD_SERVICE)
                                                            as android.content.ClipboardManager
                                                    clipboard.setPrimaryClip(
                                                        android.content.ClipData.newPlainText("Impulse", text)
                                                    )
                                                    Toast.makeText(
                                                        this@DeveloperOptionsActivity,
                                                        R.string.help_copy_done,
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            ) {
                                                Text(stringResource(android.R.string.copy))
                                            }
                                        },
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                ExpressiveGroupRow(GroupPosition.SINGLE) {
                                    ListItem(
                                        headlineContent = {
                                            Text(stringResource(R.string.developer_options_show_notification_setup))
                                        },
                                        supportingContent = {
                                            Text(stringResource(R.string.developer_options_show_notification_setup_summary))
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.clickable {
                                            showHyperOsDebugToast()
                                            startActivity(
                                                Intent(
                                                    this@DeveloperOptionsActivity,
                                                    eu.siacs.conversations.ui.NotificationSetupActivity::class.java,
                                                ).putExtra(
                                                    eu.siacs.conversations.ui.NotificationSetupActivity
                                                        .EXTRA_ALWAYS_SHOW_PERMISSION_CARDS,
                                                    true,
                                                )
                                            )
                                        },
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                // Own group — language switching isn't an update-sheet debugging
                                // tool, so it doesn't belong visually merged with the group above.
                                ExpressiveGroupRow(GroupPosition.SINGLE) {
                                    ListItem(
                                        headlineContent = {
                                            Text(stringResource(R.string.developer_options_change_language))
                                        },
                                        supportingContent = {
                                            Text(stringResource(R.string.developer_options_change_language_summary))
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.clickable { openLanguageSettings() },
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                ExpressiveGroupRow(GroupPosition.SINGLE) {
                                    ListItem(
                                        headlineContent = {
                                            Text(stringResource(R.string.developer_options_shape_catalog))
                                        },
                                        supportingContent = {
                                            Text(stringResource(R.string.developer_options_shape_catalog_summary))
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.clickable {
                                            startActivity(
                                                Intent(
                                                    this@DeveloperOptionsActivity,
                                                    ShapeCatalogActivity::class.java,
                                                )
                                            )
                                        },
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                ExpressiveGroupRow(GroupPosition.SINGLE) {
                                    ListItem(
                                        headlineContent = {
                                            Text(stringResource(R.string.developer_options_incoming_call_preview))
                                        },
                                        supportingContent = {
                                            Text(stringResource(R.string.developer_options_incoming_call_preview_summary))
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.clickable {
                                            startActivity(
                                                Intent(
                                                    this@DeveloperOptionsActivity,
                                                    IncomingCallPreviewActivity::class.java,
                                                )
                                            )
                                        },
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                ExpressiveGroupRow(GroupPosition.SINGLE) {
                                    ListItem(
                                        headlineContent = {
                                            Text(stringResource(R.string.developer_options_feature_flags))
                                        },
                                        supportingContent = {
                                            Text(stringResource(R.string.developer_options_feature_flags_summary))
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.clickable {
                                            // Plain activity switch, no container transform — this
                                            // screen isn't part of the shared-element system above.
                                            startActivity(
                                                Intent(
                                                    this@DeveloperOptionsActivity,
                                                    FeatureFlagsActivity::class.java,
                                                )
                                            )
                                        },
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                // Version picker row — source of the row→card container transform
                                AnimatedVisibility(
                                    visible = !pickerExpanded,
                                    enter = EnterTransition.None,
                                    exit = ExitTransition.None,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(28.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .sharedBounds(
                                                rememberSharedContentState("version_picker"),
                                                animatedVisibilityScope = this@AnimatedVisibility,
                                                enter = fadeIn(spring(stiffness = 1600f, dampingRatio = 1.0f)),
                                                exit = fadeOut(spring(stiffness = 1600f, dampingRatio = 1.0f)),
                                                boundsTransform = pickerBoundsTransform,
                                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                            )
                                            .clickable { pickerExpanded = true },
                                    ) {
                                        ListItem(
                                            headlineContent = {
                                                Text(stringResource(R.string.developer_options_pick_version))
                                            },
                                            supportingContent = {
                                                Text(stringResource(R.string.developer_options_pick_version_summary))
                                            },
                                            trailingContent = {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_expand_more_24dp),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        )
                                    }
                                }
                            }

                            if (scrimAlpha > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = scrimAlpha)),
                                )
                            }

                            // Version picker overlay — destination of the container transform
                            AnimatedVisibility(
                                visible = pickerExpanded,
                                enter = EnterTransition.None,
                                exit = ExitTransition.None,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                        ) { pickerExpanded = false },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(28.dp),
                                        tonalElevation = 6.dp,
                                        shadowElevation = 8.dp,
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp)
                                            .fillMaxWidth()
                                            .sharedBounds(
                                                rememberSharedContentState("version_picker"),
                                                animatedVisibilityScope = this@AnimatedVisibility,
                                                enter = fadeIn(spring(stiffness = 1600f, dampingRatio = 1.0f)),
                                                exit = fadeOut(spring(stiffness = 1600f, dampingRatio = 1.0f)),
                                                boundsTransform = pickerBoundsTransform,
                                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                            )
                                            // Consume touches so taps inside don't dismiss it
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() },
                                            ) {},
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(vertical = 12.dp)
                                                .heightIn(max = 420.dp)
                                                .verticalScroll(rememberScrollState()),
                                        ) {
                                            val list = releases
                                            when {
                                                list == null ->
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 32.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        // Developer options is a showcase spot for
                                                        // new M3 Expressive components — the
                                                        // morphing-shapes indicator, sized up, in
                                                        // place of a plain circular spinner. Its
                                                        // shape-morph motion has no public
                                                        // animationSpec param in this material3
                                                        // version (checked the decompiled class:
                                                        // no MotionScheme consumption either), so
                                                        // the damping ratio can't be overridden —
                                                        // this is the built-in motion as shipped.
                                                        androidx.compose.material3.LoadingIndicator(
                                                            modifier = Modifier.size(64.dp),
                                                        )
                                                    }
                                                list.isEmpty() ->
                                                    Text(
                                                        text = stringResource(R.string.developer_options_pick_version_empty),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(24.dp),
                                                    )
                                                else ->
                                                    list.forEach { info ->
                                                        TextButton(
                                                            onClick = {
                                                                pickerExpanded = false
                                                                scope.launch {
                                                                    // Let the collapse land before
                                                                    // the sheet slides up over it.
                                                                    delay(650)
                                                                    applyManualPick(info)
                                                                }
                                                            },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 12.dp),
                                                        ) {
                                                            Text(info.versionName)
                                                        }
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Stages the picked version exactly as a manual check would — pending version + url with
     * the no-wifi flag, so the sheet opens showing a Download button WITHOUT auto-starting
     * anything. No version comparison happens on this path: downgrades are deliberately
     * allowed (the updater's upgrade-only restriction lives in checkForUpdate, which we
     * bypass entirely here). */
    private fun applyManualPick(info: UpdateInfo) {
        val prefs = UpdatePreferences(this)
        prefs.pendingUpdateVersion = info.versionName
        prefs.pendingUpdateUrl = info.downloadUrl
        prefs.pendingNoWifi = true
        prefs.sheetDismissedUntil = 0L
        // A leftover APK from some other version would make the sheet claim READY and install
        // the wrong thing — only keep it if it is exactly the picked version.
        if (prefs.downloadedVersion != info.versionName) {
            prefs.clearDownload()
            prefs.downloadedApkPath = null
        }
        // clearDownload() above also wipes the title/notes prefs, so this has to come after it —
        // otherwise the sheet falls back to the app name instead of the real GitHub release
        // title, exactly the gap that made this path worth fixing in the first place.
        prefs.pendingReleaseTitle = info.releaseTitle
        prefs.pendingReleaseNotes = info.releaseNotes
        showUpdateSheet()
    }

    private fun showUpdateSheet() {
        if (isFinishing || supportFragmentManager.isStateSaved) return
        if (supportFragmentManager.findFragmentByTag(UpdateSheetFragment.TAG) == null) {
            UpdateSheetFragment().show(supportFragmentManager, UpdateSheetFragment.TAG)
        }
    }

    /** Mirrors the debug toast in ConversationsActivity.scheduleNotificationSetupIfNeeded() so
     * triggering the screen from here is a faithful test of the automatic flow. */
    private fun showHyperOsDebugToast() {
        var hyperOsProp = ""
        try {
            val sp = Class.forName("android.os.SystemProperties")
            val get = sp.getMethod("get", String::class.java, String::class.java)
            hyperOsProp = get.invoke(null, "ro.mi.os.version.name", "") as String
        } catch (_: Exception) {
        }
        Toast.makeText(this, "hyperos: \"$hyperOsProp\"", Toast.LENGTH_LONG).show()
    }

    private fun openLanguageSettings() {
        val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
        startActivity(intent)
    }
}

private fun DeveloperOptionsActivity.describeInstallResult(
    result: InstallStatusBus.Result
): String {
    val summary =
        when (result.status) {
            android.content.pm.PackageInstaller.STATUS_SUCCESS ->
                getString(R.string.developer_options_silent_update_test_success)
            android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION ->
                getString(R.string.developer_options_silent_update_test_pending)
            else ->
                getString(R.string.developer_options_silent_update_test_failed, result.status)
        }
    return summary + "\n\n" + installDiagnostics(result)
}

// The "log" for the silent-update test: what Android answered plus the facts that decide whether
// an update can be silent, so one screenshot (or the Copy button) is enough to tell why not.
private fun DeveloperOptionsActivity.installDiagnostics(result: InstallStatusBus.Result): String {
    val statusName =
        when (result.status) {
            android.content.pm.PackageInstaller.STATUS_SUCCESS -> "SUCCESS"
            android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION -> "PENDING_USER_ACTION"
            android.content.pm.PackageInstaller.STATUS_FAILURE -> "FAILURE"
            android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED -> "FAILURE_ABORTED"
            android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED -> "FAILURE_BLOCKED"
            android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT -> "FAILURE_CONFLICT"
            android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "FAILURE_INCOMPATIBLE"
            android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID -> "FAILURE_INVALID"
            android.content.pm.PackageInstaller.STATUS_FAILURE_STORAGE -> "FAILURE_STORAGE"
            else -> "?"
        }
    val installer =
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val source = packageManager.getInstallSourceInfo(packageName)
                "installer of record: ${source.installingPackageName ?: "none"}\n" +
                    "initiating: ${source.initiatingPackageName ?: "none"}\n" +
                    "originating: ${if (android.os.Build.VERSION.SDK_INT >= 34) source.originatingPackageName ?: "none" else "n/a"}"
            } else {
                @Suppress("DEPRECATION")
                "installer of record: ${packageManager.getInstallerPackageName(packageName) ?: "none"}"
            }
        } catch (e: Exception) {
            "installer of record: unknown ($e)"
        }
    val silentPermission =
        checkSelfPermission("android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    return buildString {
        appendLine("--- details ---")
        appendLine("status: ${result.status} ($statusName)")
        appendLine("message: ${result.message?.takeIf { it.isNotBlank() } ?: "(none)"}")
        result.extra?.let { appendLine(it) }
        appendLine(installer)
        appendLine("can install apps: ${packageManager.canRequestPackageInstalls()}")
        appendLine("UPDATE_PACKAGES_WITHOUT_USER_ACTION granted: $silentPermission")
        appendLine("Impulse ${eu.siacs.conversations.BuildConfig.VERSION_NAME}")
        append(
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android " +
                "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        )
    }
}
