package eu.siacs.conversations.ui

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import im.conversations.android.xmpp.model.support.ReplyStatus

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ReplyStatusesTest {
    @Test fun clearCannotBeOverwrittenByOlderOrDuplicateArchiveEvent() {
        val now = System.currentTimeMillis()
        val working = ReplyStatuses.Status("working", now, now + ReplyStatuses.TTL_MS, true)
        val clear = ReplyStatuses.Status("clear", now + 1, 0, true)
        assertTrue(ReplyStatuses.newer(working, clear))
        assertFalse(ReplyStatuses.newer(clear, working))
        assertFalse(ReplyStatuses.newer(clear, clear))
        assertFalse(ReplyStatuses.newer(clear, working.copy(stamp = clear.stamp)))
    }

    @Test fun statusesExpireAndRejectUnboundedOrUnknownEvents() {
        val now = System.currentTimeMillis()
        val status = ReplyStatuses.Status("checking", now, now + ReplyStatuses.TTL_MS, true)
        assertTrue(ReplyStatuses.valid(status, now))
        assertTrue(status.active(now))
        assertFalse(status.active(status.expires))
        assertFalse(ReplyStatuses.valid(status.copy(code = "anything"), now))
        assertFalse(ReplyStatuses.valid(status.copy(expires = Long.MAX_VALUE), now))
        assertFalse(ReplyStatuses.valid(status.copy(stamp = now + 600_000), now))
    }

    @Test fun accountsPeersAndOwnVersusRemoteStatesRemainIsolated() {
        val context = RuntimeEnvironment.getApplication()
        val now = System.currentTimeMillis()
        val status = ReplyStatuses.Status("waiting", now, now + ReplyStatuses.TTL_MS, false)
        ReplyStatuses.adopt(context, "account-test", "person@example.org", true, status)
        assertEquals(status, ReplyStatuses.get(context, "account-test", "person@example.org", true))
        assertNull(ReplyStatuses.get(context, "account-other", "person@example.org", true))
        assertNull(ReplyStatuses.get(context, "account-test", "other@example.org", true))
        assertNull(ReplyStatuses.get(context, "account-test", "person@example.org", false))
    }

    @Test fun wireEventHasNoChatBodyAndRetainsExpiryAndClearPreference() {
        val event = ReplyStatus.create("working", 1000, 2000, false, null)
        assertEquals("working", event.getAttribute("state"))
        assertEquals("2000", event.getAttribute("expires"))
        assertEquals("false", event.getAttribute("clear-on-send"))
        assertNull(event.getContent())
    }
}
