package eu.siacs.conversations.xmpp.jingle

import com.google.common.base.MoreObjects
import com.google.common.base.Preconditions
import eu.siacs.conversations.crypto.axolotl.AxolotlService
import java.util.concurrent.atomic.AtomicBoolean

class OmemoVerification {

    private val deviceIdWritten = AtomicBoolean(false)
    private val sessionFingerprintWritten = AtomicBoolean(false)
    private var deviceId: Int? = null
    private var sessionFingerprint: String? = null

    fun setDeviceId(id: Int?) {
        if (deviceIdWritten.compareAndSet(false, true)) {
            this.deviceId = id
            return
        }
        throw IllegalStateException("Device Id has already been set")
    }

    fun getDeviceId(): Int {
        Preconditions.checkNotNull(this.deviceId, "Device ID is null")
        return this.deviceId!!
    }

    fun hasDeviceId(): Boolean = this.deviceId != null

    fun setSessionFingerprint(fingerprint: String?) {
        Preconditions.checkNotNull(fingerprint, "Session fingerprint must not be null")
        if (sessionFingerprintWritten.compareAndSet(false, true)) {
            this.sessionFingerprint = fingerprint
            return
        }
        throw IllegalStateException("Session fingerprint has already been set")
    }

    fun getFingerprint(): String? = this.sessionFingerprint

    fun setOrEnsureEqual(omemoVerifiedPayload: AxolotlService.OmemoVerifiedPayload<*>) {
        setOrEnsureEqual(omemoVerifiedPayload.deviceId, omemoVerifiedPayload.fingerprint)
    }

    fun setOrEnsureEqual(deviceId: Int, sessionFingerprint: String?) {
        Preconditions.checkNotNull(sessionFingerprint, "Session fingerprint must not be null")
        if (this.deviceIdWritten.get() || this.sessionFingerprintWritten.get()) {
            if (this.sessionFingerprint == null) {
                throw IllegalStateException("No session fingerprint has been previously provided")
            }
            if (sessionFingerprint != this.sessionFingerprint) {
                throw SecurityException("Session Fingerprints did not match")
            }
            if (this.deviceId == null) {
                throw IllegalStateException("No Device Id has been previously provided")
            }
            if (this.deviceId != deviceId) {
                throw IllegalStateException("Device Ids did not match")
            }
        } else {
            this.setSessionFingerprint(sessionFingerprint)
            this.setDeviceId(deviceId)
        }
    }

    fun hasFingerprint(): Boolean = this.sessionFingerprint != null

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("deviceId", deviceId)
            .add("fingerprint", sessionFingerprint)
            .toString()
    }
}
