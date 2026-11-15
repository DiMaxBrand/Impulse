package eu.siacs.conversations.ui

import eu.siacs.conversations.android.AbstractPhoneContact
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.Contact
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.entities.Roster
import eu.siacs.conversations.xmpp.Jid
import im.conversations.android.xmpp.model.stanza.Presence

/** Fake model objects: no service, connection, database, or persistent storage. */
internal class DemoAccount : Account(Jid.of("demo@example.invalid"), "") {
    private val demoRoster = DemoRoster(this)
    override fun getRoster(): Roster = demoRoster
}

private class DemoContact(jid: Jid, account: Account) : Contact(jid) {
    init { setAccount(account); setServerName(jid.local ?: "Demo") }
    override fun getPresences(): MutableList<Presence> = mutableListOf()
    override fun isBlocked(): Boolean = false
    override fun isDomainBlocked(): Boolean = false
}

private class DemoRoster(private val account: Account) : Roster {
    private val contacts = mutableMapOf<Jid, Contact>()
    override fun getContacts(): List<Contact> = contacts.values.toList()
    override fun getWithSystemAccounts(clazz: Class<out AbstractPhoneContact>): List<Contact> = emptyList()
    override fun getContact(jid: Jid): Contact = contacts.getOrPut(jid.asBareJid()) { DemoContact(jid.asBareJid(), account) }
    override fun getContactFromContactList(jid: Jid): Contact = getContact(jid)
}

internal class DemoConversation(account: Account, name: String, jid: Jid) :
    Conversation(name, account, jid, Conversational.MODE_SINGLE) {
    private val demoName = name
    val demoStatuses = androidx.compose.runtime.mutableStateMapOf<Boolean, ReplyStatuses.Status>()
    override fun getName(): CharSequence = demoName
    override fun isWithStranger(): Boolean = false
}

/** All state disappears when this object is discarded. Never pass these entities to app services. */
internal class DemoSession {
    private val account = DemoAccount()
    val chats: List<DemoConversation> = listOf(
        DemoConversation(account, "Alex", Jid.of("alex@example.invalid")),
        DemoConversation(account, "Sam", Jid.of("sam@example.invalid")),
        DemoConversation(account, "Taylor", Jid.of("taylor@example.invalid")),
    )

    init {
        val chat = chats[0]
        val now = System.currentTimeMillis()
        val ru = java.util.Locale.getDefault().language == "ru"
        val phrases = if (ru) listOf("Привет! Как дела?", "Хорошо, спасибо!", "Уже попробовал обновление?", "Да, мне нравится.", "Проверяю настройки на телефоне.", "Какой у тебя сейчас Android?", "Обновился вчера вечером.", "Отлично, всё работает?", "Да! Даже батарея держится дольше.", "Вот это приятно слышать.", "Скину фотографии чуть позже.", "Хорошо, никуда не тороплюсь.", "Кстати, видел новый проект?", "Да, добавил его в закладки.", "Давай позже созвонимся.")
            else listOf("Hey! How are you?", "Doing well, thanks!", "Have you tried the new update?", "Yes, it looks great.", "I'm checking the settings on my phone.", "Which Android version are you on?", "I updated yesterday evening.", "Nice, is everything working?", "Yes! The battery even lasts longer.", "That's good to hear.", "I'll send the photos a little later.", "Sure, no rush.", "By the way, did you see the new project?", "Yes, I bookmarked it.", "Let's catch up later.")
        repeat(50) { index ->
            chat.add(Message(chat, phrases[index % phrases.size], Message.ENCRYPTION_NONE,
                if (index % 2 == 0) Message.STATUS_RECEIVED else Message.STATUS_SEND_RECEIVED).apply {
                setTime(now - (50 - index) * 60_000L)
            })
        }
        listOf("A", "U", "Okay!").forEachIndexed { index, text ->
            chats[1].add(Message(chats[1], text, Message.ENCRYPTION_NONE,
                if (index == 0) Message.STATUS_RECEIVED else Message.STATUS_SEND_RECEIVED))
        }
        val firstPhoto = photo(chats[1])
        photo(chats[1])
        chats[1].add(Message(chats[1], if (ru) "Пример фотографии с подписью" else "An example photo with a caption", Message.ENCRYPTION_NONE, Message.STATUS_SEND_RECEIVED).apply {
            setRepliedTo(firstPhoto.getUuid())
        })
        chats[2].add(Message(chats[2], if (ru) "В этой беседе только одно сообщение." else "This conversation has just one message.", Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED))
        chats[0].demoStatuses[false] = ReplyStatuses.Status("checking", now, now + ReplyStatuses.TTL_MS, true)
    }

    fun send(chat: DemoConversation, body: String): Message =
        Message(chat, body, Message.ENCRYPTION_NONE, Message.STATUS_SEND_RECEIVED).also(chat::add)

    fun photo(chat: DemoConversation): Message =
        Message(chat, "https://example.invalid/demo.jpg|12345|500|400", Message.ENCRYPTION_NONE, Message.STATUS_SEND_RECEIVED).also {
            it.setType(Message.TYPE_IMAGE)
            // The filename supplies the MIME type; no file is created or opened for demo media.
            it.setRelativeFilePath(Message.StorageLocation(java.io.File("demo.jpg"), false))
            chat.add(it)
        }

    fun remove(chat: DemoConversation, message: Message) { chat.remove(message) }
}

/** Lives only in the process; no Activity, Context, or persistent storage is retained. */
internal object DemoMemory {
    private var session: DemoSession? = null
    fun get(): DemoSession = session ?: DemoSession().also { session = it }
    fun clear() { session = null }
}
