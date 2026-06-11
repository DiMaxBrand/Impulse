package eu.siacs.conversations.xmpp.manager

import android.content.Context
import android.util.Log
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import eu.siacs.conversations.Config
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.XmppConnection
import im.conversations.android.xmpp.IqErrorException
import im.conversations.android.xmpp.model.error.Condition
import im.conversations.android.xmpp.model.stanza.Iq
import im.conversations.android.xmpp.model.vcard.BinaryValue
import im.conversations.android.xmpp.model.vcard.Photo
import im.conversations.android.xmpp.model.vcard.VCard
import java.time.Duration
import java.util.Objects

class VCardManager(context: Context, connection: XmppConnection) :
    AbstractManager(context, connection) {

    private val photoExceptionCache: Cache<Jid, Exception> =
        CacheBuilder.newBuilder()
            .maximumSize(24_576)
            .expireAfterWrite(Duration.ofHours(36))
            .build()

    fun retrieve(address: Jid): ListenableFuture<VCard> {
        val iq = Iq(Iq.Type.GET, VCard())
        iq.setTo(address)
        return Futures.transform(
            this.connection.sendIqPacket(iq),
            { result: Iq ->
                result.getExtension(VCard::class.java)
                    ?: throw IllegalStateException("Result did not include vCard")
            },
            MoreExecutors.directExecutor()
        )
    }

    fun retrievePhotoCacheException(address: Jid): ListenableFuture<ByteArray> {
        val existingException = this.photoExceptionCache.getIfPresent(address)
        if (existingException != null) {
            return Futures.immediateFailedFuture(existingException)
        }
        val future = retrievePhoto(address)
        return Futures.catchingAsync(
            future,
            Exception::class.java,
            { ex: Exception ->
                if (ex is IllegalStateException || ex is IqErrorException) {
                    photoExceptionCache.put(address, ex)
                }
                Futures.immediateFailedFuture(ex)
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun retrievePhoto(address: Jid): ListenableFuture<ByteArray> {
        val vCardFuture = retrieve(address)
        return Futures.transform(
            vCardFuture,
            { vCard: VCard ->
                val photo = vCard.photo
                    ?: throw IllegalStateException(
                        String.format("No photo in vCard of %s", address)
                    )
                val binaryValue = photo.binaryValue
                    ?: throw IllegalStateException(
                        String.format("Photo has no binary value in vCard of %s", address)
                    )
                binaryValue.asBytes()
            },
            MoreExecutors.directExecutor()
        )
    }

    fun publish(vCard: VCard): ListenableFuture<Void?> {
        return publish(account.jid.asBareJid(), vCard)
    }

    fun publish(address: Jid, vCard: VCard): ListenableFuture<Void?> {
        val iq = Iq(Iq.Type.SET, vCard)
        iq.setTo(address)
        return Futures.transform(
            connection.sendIqPacket(iq),
            { _: Iq -> null },
            MoreExecutors.directExecutor()
        )
    }

    fun deletePhoto(): ListenableFuture<Void?> {
        val vCardFuture = retrieve(account.jid.asBareJid())
        return Futures.transformAsync(
            vCardFuture,
            { vCard: VCard ->
                val photo = vCard.photo
                if (photo == null) {
                    return@transformAsync Futures.immediateFuture(null)
                }
                Log.d(
                    Config.LOGTAG,
                    "deleting photo from vCard. binaryValue=" + Objects.nonNull(photo.binaryValue)
                )
                photo.clearChildren()
                publish(vCard)
            },
            MoreExecutors.directExecutor()
        )
    }

    fun publishPhoto(address: Jid, type: String, image: ByteArray): ListenableFuture<Void?> {
        val retrieveFuture = this.retrieve(address)

        val caughtFuture =
            Futures.catchingAsync(
                retrieveFuture,
                IqErrorException::class.java,
                { ex: IqErrorException ->
                    val error = ex.error
                    if (error != null && error.condition is Condition.ItemNotFound) {
                        Futures.immediateFuture(null)
                    } else {
                        Futures.immediateFailedFuture(ex)
                    }
                },
                MoreExecutors.directExecutor()
            )

        return Futures.transformAsync(
            caughtFuture,
            { existing: VCard? ->
                val vCard: VCard
                if (existing == null) {
                    Log.d(Config.LOGTAG, "item-not-found. created fresh vCard")
                    vCard = VCard()
                } else {
                    vCard = existing
                }
                val photo = Photo()
                photo.setType(type)
                photo.addExtension(BinaryValue()).setContent(image)
                vCard.setExtension(photo)
                publish(address, vCard)
            },
            MoreExecutors.directExecutor()
        )
    }
}
