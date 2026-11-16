package eu.siacs.conversations.ui

import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.Contact
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.xmpp.Jid
import im.conversations.android.xmpp.model.stanza.Presence

private class DemoContact(jid: Jid, account: Account, name: String) : Contact(jid) {
    init { setAccount(account); setServerName(name) }
    override fun getPresences(): MutableList<Presence> = mutableListOf()
    override fun isBlocked(): Boolean = false
    override fun isDomainBlocked(): Boolean = false
}

internal class DemoConversation(account: Account, name: String, jid: Jid) :
    Conversation(name, account, jid, Conversational.MODE_SINGLE) {
    private val demoName = name
    private val demoContact = DemoContact(jid, account, name)
    override fun getContact(): Contact = demoContact
    val demoStatuses = androidx.compose.runtime.mutableStateMapOf<Boolean, ReplyStatuses.Status>()
    override fun getName(): CharSequence = demoName
    override fun isWithStranger(): Boolean = false
}

/** All state disappears when this object is discarded. Never pass these entities to app services. */
internal class DemoSession(internal val account: Account, internal val language: String = java.util.Locale.getDefault().language) {
    internal val work = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private val files = mutableSetOf<java.io.File>()
    fun own(file: java.io.File): java.io.File = file.also { files.add(it) }
    fun discard(file: java.io.File) { files.remove(file); file.delete() }
    fun close() { work.coroutineContext[kotlinx.coroutines.Job]?.cancel(); files.forEach { it.delete() }; files.clear() }
    fun audio(chat: DemoConversation, file: java.io.File, duration: Long): Message =
        Message(chat, "https://example.invalid/audio|${file.length()}|0|0|$duration", Message.ENCRYPTION_NONE, Message.STATUS_SEND_RECEIVED).also {
            it.setType(Message.TYPE_FILE)
            it.setRelativeFilePath(Message.StorageLocation(file, false))
            chat.add(it)
        }
    private val ru = language == "ru"
    val chats: List<DemoConversation> = listOf(
        DemoConversation(account, if (ru) "Алексей" else "Alex", Jid.of("alex@example.invalid")),
        DemoConversation(account, if (ru) "Саша" else "Sam", Jid.of("sam@example.invalid")),
        DemoConversation(account, if (ru) "Таня" else "Taylor", Jid.of("taylor@example.invalid")),
    )

    init {
        val chat = chats[0]
        val now = System.currentTimeMillis()
        // Each pair is an incoming message followed by an outgoing reply.
        val exchanges = if (ru) listOf(
            "Привет! Как дела?" to "Привет! Всё хорошо, спасибо. А у тебя?",
            "Тоже отлично. Как прошёл день?" to "Неплохо, наконец закончил дела.",
            "Здорово! Видел новое обновление?" to "Да, вчера установил.",
            "Как тебе новый интерфейс?" to "Нравится, особенно анимации.",
            "А я ещё пробую разные темы." to "Какую пока выбрал?",
            "Пока светлую, но тёмная тоже классная." to "Я меняю в зависимости от времени суток.",
            "Удобно! А батарея как?" to "Пока держится хорошо.",
            "Я обновил телефон на выходных." to "Заметил что-нибудь интересное?",
            "Камера стала запускаться быстрее." to "Надо будет тоже проверить.",
            "Кстати, видел тот проект?" to "Да, добавил в закладки.",
            "Там скоро обещают новые функции." to "Будет интересно попробовать.",
            "Пришлю ссылку, когда найду." to "Хорошо, не спеши.",
            "Как твой новый телефон?" to "Отлично, экран очень нравится.",
            "А размер удобный?" to "Да, уже привык.",
            "Я всё ещё выбираю себе чехол." to "Покажи потом, что нашёл.",
            "Конечно! Кстати, фотографии получились?" to "Да, скину чуть позже.",
            "Договорились." to "Как погода у тебя?",
            "Солнечно, наконец-то!" to "У нас тоже, можно погулять.",
            "Может, встретимся на выходных?" to "Давай, я свободен в субботу.",
            "Отлично, ближе к делу напишу." to "Хорошо!",
            "Ещё хотел спросить про настройки." to "Да, что именно?",
            "Не могу найти один пункт." to "Кажется, он в настройках уведомлений.",
            "Сейчас посмотрю." to "Открой настройки на телефоне.",
            "Нашёл раздел уведомлений." to "Отлично, там должна быть нужная опция.",
        ) else listOf(
            "Hey! How are you?" to "Hi! I'm doing well, thanks. How about you?",
            "Good too. How was your day?" to "Pretty good, I finally finished everything.",
            "Nice! Have you seen the update?" to "Yes, I installed it yesterday.",
            "What do you think of the new interface?" to "I like it, especially the animations.",
            "I'm still trying different themes." to "Which one have you picked so far?",
            "The light one, but dark looks great too." to "I switch depending on the time of day.",
            "Nice! How's the battery?" to "It's holding up well so far.",
            "I updated my phone over the weekend." to "Have you noticed anything interesting?",
            "The camera opens faster now." to "I'll have to check that too.",
            "By the way, did you see that project?" to "Yes, I bookmarked it.",
            "They're promising new features soon." to "It'll be fun to try them.",
            "I'll send you the link when I find it." to "Sounds good, no rush.",
            "How's your new phone?" to "Great, I really like the screen.",
            "Is the size comfortable?" to "Yes, I'm used to it now.",
            "I'm still picking a case for mine." to "Show me what you find later.",
            "Sure! Did the photos turn out okay?" to "Yes, I'll send them a bit later.",
            "Sounds good." to "How's the weather there?",
            "Sunny, finally!" to "Here too, perfect for a walk.",
            "Want to meet up this weekend?" to "Sure, I'm free on Saturday.",
            "Great, I'll message you closer to the day." to "Sounds good!",
            "I also wanted to ask about a setting." to "Sure, which one?",
            "I can't find an option." to "I think it's under notification settings.",
            "I'll have a look." to "Open the settings on your phone.",
            "I found the notifications section." to "Great, the option should be there.",
        )
        val messages = exchanges.flatMap { (incoming, outgoing) -> listOf(incoming to false, outgoing to true) } +
            if (ru) listOf("Можешь проверить, включена ли эта опция?" to true, "Проверяю настройки на телефоне." to false)
            else listOf("Can you check whether that option is on?" to true, "I'm checking the settings on my phone." to false)
        messages.forEachIndexed { index, (body, outgoing) ->
            chat.add(Message(chat, body, Message.ENCRYPTION_NONE,
                if (outgoing) Message.STATUS_SEND_RECEIVED else Message.STATUS_RECEIVED).apply {
                setTime(now - (messages.size - index) * 60_000L)
            })
        }
        (if (ru) listOf("А", "У", "Хорошо!") else listOf("A", "U", "Okay!")).forEachIndexed { index, text ->
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
    fun get(account: Account, language: String): DemoSession {
        if (session?.account !== account || session?.language != language) { clear(); session = DemoSession(account, language) }
        return requireNotNull(session)
    }
    fun clear() { session?.close(); session = null }
}
