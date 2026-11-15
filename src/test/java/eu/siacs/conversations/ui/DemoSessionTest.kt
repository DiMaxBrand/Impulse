package eu.siacs.conversations.ui

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DemoSessionTest {
    @Test fun sessionsAreIsolatedAndHaveNoConnection() {
        val first = DemoSession()
        val second = DemoSession()
        val chat = first.chats.first()
        val count = chat.snapshotMessages().size
        assertEquals(50, count)
        assertNull(chat.getAccount().xmppConnection)
        val sent = first.send(chat, "Hello")
        assertEquals(count + 1, chat.snapshotMessages().size)
        assertEquals(count, second.chats.first().snapshotMessages().size)
        first.remove(chat, sent)
        assertEquals(count, chat.snapshotMessages().size)
        assertEquals(count, DemoSession().chats.first().snapshotMessages().size)
    }
}
