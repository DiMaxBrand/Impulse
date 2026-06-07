package eu.siacs.conversations.generator

import android.os.Bundle
import android.util.Base64
import android.util.Log
import eu.siacs.conversations.Config
import eu.siacs.conversations.crypto.axolotl.AxolotlService
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xml.Element
import eu.siacs.conversations.xml.Namespace
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.forms.Data
import im.conversations.android.xmpp.model.stanza.Iq
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.ecc.ECPublicKey
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord

open class IqGenerator(service: XmppConnectionService) : AbstractGenerator(service) {

    protected fun publish(node: String, item: Element, options: Bundle?): Iq {
        val packet = Iq(Iq.Type.SET)
        val pubsub = packet.addChild("pubsub", Namespace.PUB_SUB)
        val publish = pubsub.addChild("publish")
        publish.setAttribute("node", node)
        publish.addChild(item)
        if (options != null) {
            val publishOptions = pubsub.addChild("publish-options")
            publishOptions.addChild(Data.create(Namespace.PUB_SUB_PUBLISH_OPTIONS, options))
        }
        return packet
    }

    protected fun publish(node: String, item: Element): Iq {
        return publish(node, item, null)
    }

    private fun retrieve(node: String, item: Element?): Iq {
        val packet = Iq(Iq.Type.GET)
        val pubsub = packet.addChild("pubsub", Namespace.PUB_SUB)
        val items = pubsub.addChild("items")
        items.setAttribute("node", node)
        if (item != null) {
            items.addChild(item)
        }
        return packet
    }

    fun retrieveDeviceIds(to: Jid?): Iq {
        val packet = retrieve(AxolotlService.PEP_DEVICE_LIST, null)
        if (to != null) {
            packet.setTo(to)
        }
        return packet
    }

    fun retrieveBundlesForDevice(to: Jid, deviceid: Int): Iq {
        val packet = retrieve("${AxolotlService.PEP_BUNDLES}:$deviceid", null)
        packet.setTo(to)
        return packet
    }

    fun retrieveVerificationForDevice(to: Jid, deviceid: Int): Iq {
        val packet = retrieve("${AxolotlService.PEP_VERIFICATION}:$deviceid", null)
        packet.setTo(to)
        return packet
    }

    fun publishDeviceIds(ids: Set<Int>, publishOptions: Bundle): Iq {
        val item = Element("item")
        item.setAttribute("id", "current")
        val list = item.addChild("list", AxolotlService.PEP_PREFIX)
        for (id in ids) {
            val device = Element("device")
            device.setAttribute("id", id)
            list.addChild(device)
        }
        return publish(AxolotlService.PEP_DEVICE_LIST, item, publishOptions)
    }

    fun publishBundles(
        signedPreKeyRecord: SignedPreKeyRecord,
        identityKey: IdentityKey,
        preKeyRecords: Set<PreKeyRecord>,
        deviceId: Int,
        publishOptions: Bundle
    ): Iq {
        val item = Element("item")
        item.setAttribute("id", "current")
        val bundle = item.addChild("bundle", AxolotlService.PEP_PREFIX)
        val signedPreKeyPublic = bundle.addChild("signedPreKeyPublic")
        signedPreKeyPublic.setAttribute("signedPreKeyId", signedPreKeyRecord.id)
        val publicKey: ECPublicKey = signedPreKeyRecord.keyPair.publicKey
        signedPreKeyPublic.setContent(Base64.encodeToString(publicKey.serialize(), Base64.NO_WRAP))
        val signedPreKeySignature = bundle.addChild("signedPreKeySignature")
        signedPreKeySignature.setContent(
            Base64.encodeToString(signedPreKeyRecord.signature, Base64.NO_WRAP)
        )
        val identityKeyElement = bundle.addChild("identityKey")
        identityKeyElement.setContent(
            Base64.encodeToString(identityKey.serialize(), Base64.NO_WRAP)
        )

        val prekeys = bundle.addChild("prekeys", AxolotlService.PEP_PREFIX)
        for (preKeyRecord in preKeyRecords) {
            val prekey = prekeys.addChild("preKeyPublic")
            prekey.setAttribute("preKeyId", preKeyRecord.id)
            prekey.setContent(
                Base64.encodeToString(
                    preKeyRecord.keyPair.publicKey.serialize(),
                    Base64.NO_WRAP
                )
            )
        }

        return publish("${AxolotlService.PEP_BUNDLES}:$deviceId", item, publishOptions)
    }

    fun publishVerification(
        signature: ByteArray,
        certificates: Array<X509Certificate>,
        deviceId: Int
    ): Iq {
        val item = Element("item")
        item.setAttribute("id", "current")
        val verification = item.addChild("verification", AxolotlService.PEP_PREFIX)
        val chain = verification.addChild("chain")
        for (i in certificates.indices) {
            try {
                val certificate = chain.addChild("certificate")
                certificate.setContent(
                    Base64.encodeToString(certificates[i].encoded, Base64.NO_WRAP)
                )
                certificate.setAttribute("index", i)
            } catch (e: CertificateEncodingException) {
                Log.d(Config.LOGTAG, "could not encode certificate")
            }
        }
        verification
            .addChild("signature")
            .setContent(Base64.encodeToString(signature, Base64.NO_WRAP))
        return publish("${AxolotlService.PEP_VERIFICATION}:$deviceId", item)
    }

    fun requestPubsubConfiguration(jid: Jid, node: String): Iq {
        return pubsubConfiguration(jid, node, null)
    }

    fun publishPubsubConfiguration(jid: Jid, node: String, data: Data): Iq {
        return pubsubConfiguration(jid, node, data)
    }

    private fun pubsubConfiguration(jid: Jid, node: String, data: Data?): Iq {
        val packet = Iq(if (data == null) Iq.Type.GET else Iq.Type.SET)
        packet.setTo(jid)
        val pubsub = packet.addChild("pubsub", "http://jabber.org/protocol/pubsub#owner")
        val configure = pubsub.addChild("configure").setAttribute("node", node)
        if (data != null) {
            configure.addChild(data)
        }
        return packet
    }
}
