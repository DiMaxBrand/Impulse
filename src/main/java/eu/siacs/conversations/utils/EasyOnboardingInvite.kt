package eu.siacs.conversations.utils

import android.os.Parcel
import android.os.Parcelable
import com.google.common.base.Strings
import de.gultsch.common.MiniUri
import eu.siacs.conversations.xmpp.Jid
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class EasyOnboardingInvite : Parcelable {

    private val domain: Jid
    private val uri: MiniUri.Xmpp
    private val landingUrl: HttpUrl?

    constructor(domain: Jid, uri: MiniUri.Xmpp) {
        this.domain = domain
        this.uri = uri
        this.landingUrl = null
    }

    constructor(domain: Jid, uri: MiniUri.Xmpp, landingUrl: HttpUrl) {
        this.domain = domain
        this.uri = uri
        this.landingUrl = landingUrl
    }

    protected constructor(parcel: Parcel) {
        this.domain = Jid.ofDomain(parcel.readString())
        val parsed = MiniUri.tryParse(parcel.readString())
        if (parsed is MiniUri.Xmpp) {
            this.uri = parsed
        } else {
            throw IllegalStateException("Illegal XMPP uri in parcel")
        }
        val landingUrlStr = parcel.readString()
        this.landingUrl = landingUrlStr?.takeIf { it.isNotEmpty() }?.toHttpUrlOrNull()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(domain.toString())
        dest.writeString(uri.asUri().toString())
        dest.writeString(landingUrl?.toString())
    }

    override fun describeContents(): Int = 0

    fun getDomain(): String = domain.toString()

    fun getShareableLink(): HttpUrl? {
        if (landingUrl != null) {
            return landingUrl
        }
        return uri.asInvitationUri().asHttpUrl()
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<EasyOnboardingInvite> =
            object : Parcelable.Creator<EasyOnboardingInvite> {
                override fun createFromParcel(parcel: Parcel): EasyOnboardingInvite =
                    EasyOnboardingInvite(parcel)

                override fun newArray(size: Int): Array<EasyOnboardingInvite?> =
                    arrayOfNulls(size)
            }
    }
}
