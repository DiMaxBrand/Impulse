package eu.siacs.conversations.update

import android.app.NotificationManager
import android.content.Intent
import eu.siacs.conversations.ui.activity.UpdatesActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class UpdateNotificationNavigationTest {
    @Test fun fixedAndTrackedReadyNotificationsOpenUpdatesAndRequestFreshCheck() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(true, false).forEach { bugsOnly ->
            manager.cancelAll()
            UpdateNotifications.postBugFixed(context, listOf("BUG-AB12"), "1.15.0-beta.330", bugsOnly = bugsOnly)
            val notification = shadowOf(manager).allNotifications.single()
            val target = shadowOf(notification.contentIntent).savedIntent
            assertEquals(UpdatesActivity::class.java.name, target.component!!.className)
            assertTrue(target.getBooleanExtra(UpdatesActivity.EXTRA_CHECK_NOW, false))
            assertTrue(target.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
            assertTrue(target.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        }
    }
}
