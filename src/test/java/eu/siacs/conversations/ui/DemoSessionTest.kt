package eu.siacs.conversations.ui

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DemoSessionTest {
    @Test fun sessionSurvivesBackendReconnectionAndCanBeCleared() {
        DemoMemory.clear()
        val jid = eu.siacs.conversations.xmpp.Jid.of("test@example.org")
        val account = eu.siacs.conversations.entities.Account(jid, "")
        val session = requireNotNull(DemoMemory.get(account, "en"))
        val chat = session.chats.first()
        val sent = session.send(chat, "Still here")
        assertSame(session, DemoMemory.get(null, "en"))
        val reconnected = object : eu.siacs.conversations.entities.Account(jid, "") {
            override fun getUuid(): String? = account.getUuid()
        }
        assertSame(session, DemoMemory.get(reconnected, "en"))
        assertSame(reconnected, chat.getAccount())
        assertSame(reconnected, chat.getContact().getAccount())
        assertTrue(chat.snapshotMessages().contains(sent))
        DemoMemory.clear()
        assertNull(DemoMemory.get(null, "en"))
    }

    @Test fun sessionsAreIsolatedAndHaveNoConnection() {
        val account = eu.siacs.conversations.entities.Account(eu.siacs.conversations.xmpp.Jid.of("test@example.org"), "")
        val first = DemoSession(account)
        val second = DemoSession(eu.siacs.conversations.entities.Account(eu.siacs.conversations.xmpp.Jid.of("test@example.org"), ""))
        val chat = first.chats.first()
        val count = chat.snapshotMessages().size
        assertEquals(50, count)
        assertSame(account, chat.getAccount())
        assertSame(chat.getContact(), chat.getContact())
        val sent = first.send(chat, "Hello")
        assertEquals(count + 1, chat.snapshotMessages().size)
        assertEquals(count, second.chats.first().snapshotMessages().size)
        first.remove(chat, sent)
        assertEquals(count, chat.snapshotMessages().size)
        assertEquals(count, DemoSession(eu.siacs.conversations.entities.Account(eu.siacs.conversations.xmpp.Jid.of("test@example.org"), "")).chats.first().snapshotMessages().size)
    }
}
