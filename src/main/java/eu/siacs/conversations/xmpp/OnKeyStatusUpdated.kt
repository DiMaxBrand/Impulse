package eu.siacs.conversations.xmpp

import eu.siacs.conversations.crypto.axolotl.AxolotlService

fun interface OnKeyStatusUpdated {
    fun onKeyStatusUpdated(report: AxolotlService.FetchStatus)
}
