package eu.siacs.conversations.update

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import eu.siacs.conversations.ui.UpdateSheetFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

object UpdateCheckHelper {

    private var checked = false

    @JvmStatic
    fun runIfNeeded(activity: AppCompatActivity) {
        if (checked) return
        checked = true
        val prefs = UpdatePreferences(activity.applicationContext)
        if (!prefs.autoCheck) return
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    UpdateChecker(OkHttpClient()).checkForUpdate(prefs.selectedChannel)
                } catch (_: Exception) {
                    null
                }
            } ?: return@launch
            if (result is UpdateChecker.CheckResult.UpdateAvailable) {
                prefs.pendingUpdateVersion = result.info.versionName
                prefs.pendingUpdateUrl = result.info.downloadUrl
                if (!activity.isFinishing && UpdateSheetFragment.shouldShow(activity)) {
                    val fm = activity.supportFragmentManager
                    if (fm.findFragmentByTag(UpdateSheetFragment.TAG) == null) {
                        UpdateSheetFragment().show(fm, UpdateSheetFragment.TAG)
                    }
                }
            }
        }
    }
}
