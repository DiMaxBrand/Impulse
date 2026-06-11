package eu.siacs.conversations.xmpp.manager

import android.util.Log
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableMap
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.ReceiptRequest
import eu.siacs.conversations.generator.AbstractGenerator
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xml.Element
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import eu.siacs.conversations.xmpp.mam.MamReference
import eu.siacs.conversations.utils.Random.SECURE_RANDOM
import im.conversations.android.xmpp.Range
import im.conversations.android.xmpp.model.mam.Fin
import im.conversations.android.xmpp.model.mam.Preferences
import im.conversations.android.xmpp.model.stanza.Iq
import java.math.BigInteger
import java.time.Instant
import java.util.ArrayList
import java.util.HashSet
import java.util.concurrent.TimeoutException

class MessageArchiveManager(
    private val mXmppConnectionService: XmppConnectionService,
    connection: XmppConnection
) : AbstractManager(mXmppConnectionService.applicationContext, connection) {

    private var currentArchivingPreferences: Preferences.Default? = null

    private val queries = HashSet<Query>()

    fun catchup() {
        synchronized(this.queries) {
            this.queries.clear()
        }
        val mamReferenceByMessage =
            MamReference.max(
                mXmppConnectionService.databaseBackend.getLastMessageReceived(account),
                mXmppConnectionService.databaseBackend.getLastClearDate(account)
            )
        val messageDeletion =
            AppSettings(mXmppConnectionService).automaticMessageDeletionInstant
        val mamReference: MamReference
        if (messageDeletion.isPresent) {
            mamReference =
                MamReference.max(mamReferenceByMessage, messageDeletion.get().toEpochMilli())!!
        } else {
            mamReference = mamReferenceByMessage!!
        }
        val endCatchup = connection.lastSessionEstablished
        val query: Query
        if (mamReference.timestamp == 0L) {
            return
        } else if (endCatchup - mamReference.timestamp >= Config.MAM_MAX_CATCHUP) {
            val startCatchup = endCatchup - Config.MAM_MAX_CATCHUP
            val conversations = mXmppConnectionService.conversations
            for (conversation in conversations) {
                if (conversation.getMode() == Conversational.MODE_SINGLE
                    && conversation.getAccount() === account
                    && startCatchup > conversation.lastMessageTransmitted.timestamp
                ) {
                    this.query(conversation, startCatchup, true)
                }
            }
            query = Query(MamReference(startCatchup), 0)
        } else {
            query = Query(mamReference, 0)
        }
        synchronized(this.queries) {
            this.queries.add(query)
        }
        this.execute(query)
    }

    fun catchupMUC(conversation: Conversation) {
        if (conversation.lastMessageTransmitted.timestamp < 0
            && conversation.countMessages() == 0
        ) {
            query(conversation, MamReference(0), 0, true)
        } else {
            query(conversation, conversation.lastMessageTransmitted, 0, true)
        }
    }

    fun query(conversation: Conversation): Query? {
        return if (conversation.lastMessageTransmitted.timestamp < 0
            && conversation.countMessages() == 0
        ) {
            query(conversation, MamReference(0), System.currentTimeMillis(), false)
        } else {
            query(
                conversation,
                conversation.lastMessageTransmitted,
                conversation.getAccount().xmppConnection.lastSessionEstablished,
                false
            )
        }
    }

    fun isCatchingUp(conversation: Conversation): Boolean {
        val account: Account = conversation.getAccount()
        if (account.xmppConnection.isWaitingForSmCatchup) {
            return true
        } else {
            synchronized(this.queries) {
                for (query in this.queries) {
                    if (query.isCatchup()
                        && ((conversation.getMode() == Conversational.MODE_SINGLE
                                && query.getWith() == null)
                                || query.conversation === conversation)
                    ) {
                        return true
                    }
                }
            }
            return false
        }
    }

    fun query(conversation: Conversation, end: Long, allowCatchup: Boolean): Query? {
        return this.query(
            conversation, conversation.lastMessageTransmitted, end, allowCatchup
        )
    }

    fun query(
        conversation: Conversation,
        start: MamReference,
        end: Long,
        allowCatchup: Boolean
    ): Query? {
        synchronized(this.queries) {
            val deletion =
                AppSettings(mXmppConnectionService).automaticMessageDeletionInstant
            val query: Query

            val startActual: MamReference
            if (deletion.isPresent) {
                startActual = MamReference.max(start, deletion.get().toEpochMilli())!!
            } else {
                startActual = start
            }
            if (start.timestamp == 0L) {
                query = Query(conversation, startActual, end, false)
                query.reference = conversation.firstMamReference
            } else {
                if (allowCatchup) {
                    val maxCatchup =
                        MamReference.max(
                            startActual,
                            System.currentTimeMillis() - Config.MAM_MAX_CATCHUP
                        )!!
                    if (maxCatchup.greaterThan(startActual)) {
                        val reverseCatchup =
                            Query(
                                conversation,
                                startActual,
                                maxCatchup.timestamp,
                                false
                            )
                        this.queries.add(reverseCatchup)
                        this.execute(reverseCatchup)
                    }
                    query = Query(conversation, maxCatchup, end, true)
                } else {
                    query = Query(conversation, startActual, end, false)
                }
            }
            if (end != 0L && start.greaterThan(end)) {
                return null
            }
            this.queries.add(query)
            this.execute(query)
            return query
        }
    }

    private fun execute(
        service: Jid,
        query: im.conversations.android.xmpp.model.mam.Query
    ): ListenableFuture<Fin> {
        val iq = Iq(Iq.Type.SET, service, query)
        return Futures.transform(
            this.connection.sendIqPacket(iq),
            { response: Iq ->
                val fin = response.getOnlyExtension(Fin::class.java)
                    ?: run {
                        Log.d(Config.LOGTAG, "response: $response")
                        throw IllegalStateException(
                            "Iq response to MAM query did not contain fin"
                        )
                    }
                fin
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun execute(query: Query) {
        val conversation = query.conversation
        if (conversation != null && conversation.status == Conversation.STATUS_ARCHIVED) {
            throw IllegalStateException("Attempted to run MAM query for archived conversation")
        }

        Log.d(
            Config.LOGTAG,
            account.jid.asBareJid().toString() + ": running mam query " + query
        )

        val service: Jid = if (query.muc()) {
            query.getWith()!!
        } else {
            account.jid.asBareJid()
        }

        val future = execute(service, toQuery(query))

        Futures.addCallback(
            future,
            object : FutureCallback<Fin> {
                override fun onSuccess(result: Fin) {
                    val running: Boolean
                    synchronized(queries) {
                        running = queries.contains(query)
                    }
                    if (running) {
                        processFin(query, result)
                    } else {
                        Log.d(
                            Config.LOGTAG,
                            "${account.jid.asBareJid()}: ignoring MAM iq result because query had been killed"
                        )
                    }
                }

                override fun onFailure(t: Throwable) {
                    if (t is TimeoutException) {
                        synchronized(queries) {
                            queries.remove(query)
                            if (query.hasCallback()) {
                                query.callback(false)
                            }
                        }
                    } else {
                        Log.d(
                            Config.LOGTAG,
                            account.jid.asBareJid().toString() + ": error executing mam",
                            t
                        )
                        try {
                            finalizeQuery(query, true)
                        } catch (e: IllegalStateException) {
                            // ignored
                        }
                    }
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun finalizeQuery(query: Query, done: Boolean) {
        synchronized(this.queries) {
            if (!this.queries.remove(query)) {
                throw IllegalStateException("Unable to remove query from queries")
            }
        }
        val conversation = query.conversation
        if (conversation != null) {
            conversation.sort()
            conversation.setHasMessagesLeftOnServer(!done)
            val displayState = conversation.displayState
            if (displayState != null) {
                mXmppConnectionService.markReadUpToStanzaId(conversation, displayState)
            }
        } else {
            for (tmp in this.mXmppConnectionService.conversations) {
                if (tmp.getAccount() === account) {
                    tmp.sort()
                    val displayState = tmp.displayState
                    if (displayState != null) {
                        mXmppConnectionService.markReadUpToStanzaId(tmp, displayState)
                    }
                }
            }
        }
        if (query.hasCallback()) {
            query.callback(done)
        } else {
            this.mXmppConnectionService.updateConversationUi()
        }
    }

    fun inCatchup(): Boolean {
        synchronized(this.queries) {
            for (query in queries) {
                if (query.isCatchup() && query.getWith() == null) {
                    return true
                }
            }
        }
        return false
    }

    fun isCatchupInProgress(conversation: Conversation): Boolean {
        synchronized(this.queries) {
            for (query in queries) {
                if (query.isCatchup()) {
                    val with: Jid? = if (query.getWith() == null) null else query.getWith()!!.asBareJid()
                    if ((conversation.getMode() == Conversational.MODE_SINGLE && with == null)
                        || (conversation.getAddress().asBareJid() == with)
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun queryInProgress(
        conversation: Conversation,
        callback: XmppConnectionService.OnMoreMessagesLoaded?
    ): Boolean {
        synchronized(this.queries) {
            for (query in queries) {
                if (query.conversation === conversation) {
                    if (!query.hasCallback() && callback != null) {
                        query.callback = callback
                    }
                    return true
                }
            }
            return false
        }
    }

    fun queryInProgress(conversation: Conversation): Boolean {
        return queryInProgress(conversation, null)
    }

    private fun processFin(query: Query, fin: Fin) {
        val complete = fin.getAttributeAsBoolean("complete")
        val set: Element? = fin.findChild("set", "http://jabber.org/protocol/rsm")
        val last: Element? = if (set == null) null else set.findChild("last")
        val count: String? = if (set == null) null else set.findChildContent("count")
        val first: Element? = if (set == null) null else set.findChild("first")
        val relevant: Element? =
            if (query.pagingOrder == PagingOrder.NORMAL) last else first
        val abort =
            (!query.isCatchup() && query.totalCount >= Config.PAGE_SIZE)
                    || query.totalCount >= Config.MAM_MAX_MESSAGES
        if (query.conversation != null) {
            query.conversation!!.firstMamReference =
                if (first == null) null else first.content
        }
        if (complete || relevant == null || abort) {
            var done: Boolean
            if (query.isCatchup()) {
                done = false
            } else {
                if (count != null) {
                    try {
                        done = count.toInt() <= query.totalCount
                    } catch (e: NumberFormatException) {
                        done = false
                    }
                } else {
                    done = query.totalCount == 0
                }
            }
            done = done || (query.actualMessageCount == 0 && !query.isCatchup())
            this.finalizeQuery(query, done)

            Log.d(
                Config.LOGTAG,
                "${account.jid.asBareJid()}: finished mam after ${query.totalCount}(${query.actualMessageCount}) messages. messages left=${!done} count=$count"
            )
            if (query.isCatchup() && query.actualMessageCount > 0) {
                mXmppConnectionService.notificationService.finishBacklog(true, account)
            }
            processPostponed(query)
        } else {
            val nextQuery: Query
            if (query.pagingOrder == PagingOrder.NORMAL) {
                nextQuery = query.next(if (last == null) null else last.content)
            } else {
                nextQuery = query.prev(if (first == null) null else first.content)
            }
            this.execute(nextQuery)
            this.finalizeQuery(query, false)
            synchronized(this.queries) {
                this.queries.add(nextQuery)
            }
        }
    }

    fun kill(conversation: Conversation) {
        val toBeKilled = ArrayList<Query>()
        synchronized(this.queries) {
            for (q in queries) {
                if (q.conversation === conversation) {
                    toBeKilled.add(q)
                }
            }
        }
        for (q in toBeKilled) {
            kill(q)
        }
    }

    private fun kill(query: Query) {
        Log.d(Config.LOGTAG, account.jid.asBareJid().toString() + ": killing mam query prematurely")
        query.callback = null
        this.finalizeQuery(query, false)
        if (query.isCatchup() && query.actualMessageCount > 0) {
            mXmppConnectionService.notificationService.finishBacklog(true, account)
        }
        this.processPostponed(query)
    }

    private fun processPostponed(query: Query) {
        val account = account
        account.axolotlService.processPostponed()
        query.pendingReceiptRequests.removeAll(query.receiptRequests)
        Log.d(
            Config.LOGTAG,
            "${account.jid.asBareJid()}: found ${query.pendingReceiptRequests.size} pending receipt requests"
        )
        val deliveryReceiptManager = getManager(DeliveryReceiptManager::class.java)
        val iterator = query.pendingReceiptRequests.iterator()
        while (iterator.hasNext()) {
            val rr = iterator.next()
            deliveryReceiptManager.received(rr.jid, rr.id)
            iterator.remove()
        }
    }

    fun findQuery(id: String): Query? {
        synchronized(this.queries) {
            for (query in this.queries) {
                if (query.queryId == id) {
                    return query
                }
            }
            return null
        }
    }

    fun validFrom(query: Query, from: Jid?): Boolean {
        return if (query.muc()) {
            query.getWith() == from
        } else {
            (from == null) || account.jid.asBareJid() == from.asBareJid()
        }
    }

    fun fetchArchivingPreferences() {
        Futures.addCallback(
            archivingPreference,
            object : FutureCallback<Preferences.Default> {
                override fun onSuccess(result: Preferences.Default) {
                    this@MessageArchiveManager.currentArchivingPreferences = result
                }

                override fun onFailure(t: Throwable) {
                    this@MessageArchiveManager.currentArchivingPreferences = null
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    val archivingPreference: ListenableFuture<Preferences.Default>
        get() {
            val iq = Iq(Iq.Type.GET, Preferences())
            val future = connection.sendIqPacket(iq)
            return Futures.transform(
                future,
                { result: Iq ->
                    val pref = result.getOnlyExtension(Preferences::class.java)
                        ?: throw IllegalStateException("Server response did not contain pref")
                    pref.default
                },
                MoreExecutors.directExecutor()
            )
        }

    fun setArchivingPreference(preference: Preferences.Default): ListenableFuture<Void?> {
        val iq = Iq(Iq.Type.SET)
        val preferences = iq.addExtension(Preferences())
        preferences.setDefault(preference)
        val future = connection.sendIqPacket(iq)
        return Futures.transform(future, { _: Iq -> null }, MoreExecutors.directExecutor())
    }

    fun isMamPreferenceAlways(): Boolean {
        return this.currentArchivingPreferences == Preferences.Default.ALWAYS
    }

    fun hasFeature(): Boolean {
        return getManager(DiscoManager::class.java)
            .hasAccountFeature(Namespace.MESSAGE_ARCHIVE_MANAGEMENT)
    }

    enum class PagingOrder {
        NORMAL,
        REVERSE
    }

    class Query {
        var pendingReceiptRequests: HashSet<ReceiptRequest> = HashSet()
        var receiptRequests: HashSet<ReceiptRequest> = HashSet()
        var totalCount: Int = 0
            private set
        var actualMessageCount: Int = 0
            private set
        private var actualInThisQuery: Int = 0
        private var start: Long = 0
        private val end: Long
        val queryId: String
        var reference: String? = null
        var conversation: Conversation? = null
        var pagingOrder: PagingOrder = PagingOrder.NORMAL
        var callback: XmppConnectionService.OnMoreMessagesLoaded? = null
        private var catchup: Boolean = true

        internal constructor(conversation: Conversation, start: MamReference, end: Long, catchup: Boolean) : this(
            if (catchup) start else start.timeOnly(), end
        ) {
            this.conversation = conversation
            this.pagingOrder = if (catchup) PagingOrder.NORMAL else PagingOrder.REVERSE
            this.catchup = catchup
        }

        internal constructor(start: MamReference, end: Long) {
            if (start.reference != null) {
                this.reference = start.reference
            } else {
                this.start = start.timestamp
            }
            this.end = end
            this.queryId = BigInteger(50, SECURE_RANDOM).toString(32)
        }

        private fun page(reference: String?): Query {
            val query = Query(MamReference(this.start, reference), this.end)
            query.conversation = conversation
            query.totalCount = totalCount
            query.actualMessageCount = actualMessageCount
            query.pendingReceiptRequests = pendingReceiptRequests
            query.receiptRequests = receiptRequests
            query.callback = callback
            query.catchup = catchup
            return query
        }

        fun removePendingReceiptRequest(receiptRequest: ReceiptRequest) {
            if (!this.pendingReceiptRequests.remove(receiptRequest)) {
                this.receiptRequests.add(receiptRequest)
            }
        }

        fun addPendingReceiptRequest(receiptRequest: ReceiptRequest) {
            this.pendingReceiptRequests.add(receiptRequest)
        }

        fun safeToExtractTrueCounterpart(): Boolean {
            return muc()
        }

        fun next(reference: String?): Query {
            val query = page(reference)
            query.pagingOrder = PagingOrder.NORMAL
            return query
        }

        fun prev(reference: String?): Query {
            val query = page(reference)
            query.pagingOrder = PagingOrder.REVERSE
            return query
        }

        fun getWith(): Jid? {
            return if (conversation == null) null else conversation!!.getAddress().asBareJid()
        }

        fun muc(): Boolean {
            return conversation != null && conversation!!.getMode() == Conversational.MODE_MULTI
        }

        fun getStart(): Long = start

        fun isCatchup(): Boolean = catchup

        fun callback(done: Boolean) {
            callback?.let {
                it.onMoreMessagesLoaded(actualMessageCount, conversation)
                if (done) {
                    it.informUser(R.string.no_more_history_on_server)
                }
            }
        }

        fun getEnd(): Long = end

        fun incrementMessageCount() {
            this.totalCount++
        }

        fun incrementActualMessageCount() {
            this.actualInThisQuery++
            this.actualMessageCount++
        }

        fun getActualInThisQuery(): Int = this.actualInThisQuery

        override fun toString(): String {
            val builder = StringBuilder()
            if (this.muc()) {
                builder.append("to=")
                builder.append(this.getWith().toString())
            } else {
                builder.append("with=")
                if (this.getWith() == null) {
                    builder.append("*")
                } else {
                    builder.append(getWith().toString())
                }
            }
            if (this.start != 0L) {
                builder.append(", start=")
                builder.append(AbstractGenerator.getTimestamp(this.start))
            }
            if (this.end != 0L) {
                builder.append(", end=")
                builder.append(AbstractGenerator.getTimestamp(this.end))
            }
            builder.append(", order=").append(pagingOrder.toString())
            if (this.reference != null) {
                if (this.pagingOrder == PagingOrder.NORMAL) {
                    builder.append(", after=")
                } else {
                    builder.append(", before=")
                }
                builder.append(this.reference)
            }
            builder.append(", catchup=").append(catchup)
            return builder.toString()
        }

        fun hasCallback(): Boolean = this.callback != null

        fun isImplausibleFrom(from: Jid?): Boolean {
            return if (muc()) {
                if (from == null) {
                    true
                } else {
                    from.asBareJid() != getWith()
                }
            } else {
                false
            }
        }
    }

    sealed interface Reference

    @JvmRecord
    data class IdReference(val stanzaId: String) : Reference

    @JvmRecord
    data class InstantReference(val instant: Instant) : Reference

    @JvmRecord
    data class InstantIdReference(val instantReference: InstantReference, val idReference: IdReference) {
        init {
            Preconditions.checkNotNull(instantReference, "Every reference must have an instant")
        }
    }

    companion object {
        private fun toQuery(mam: Query): im.conversations.android.xmpp.model.mam.Query {
            val query = im.conversations.android.xmpp.model.mam.Query()
            query.setQueryId(mam.queryId)

            val filter = ImmutableMap.builder<String, Any>()

            if (!mam.muc() && mam.getWith() != null) {
                filter.put("with", mam.getWith().toString())
            }
            val start = mam.getStart()
            val end = mam.getEnd()
            if (start != 0L) {
                filter.put("start", AbstractGenerator.getTimestamp(start))
            }
            if (end != 0L) {
                filter.put("end", AbstractGenerator.getTimestamp(end))
            }

            query.setFilter(filter.build())

            if (mam.pagingOrder == PagingOrder.REVERSE) {
                query.setResultSet(
                    im.conversations.android.xmpp.model.rsm.Set.of(
                        Range(Range.Order.REVERSE, mam.reference), Config.PAGE_SIZE
                    )
                )
            } else if (mam.reference != null) {
                query.setResultSet(
                    im.conversations.android.xmpp.model.rsm.Set.of(
                        Range(Range.Order.NORMAL, mam.reference), Config.PAGE_SIZE
                    )
                )
            }
            return query
        }
    }
}
