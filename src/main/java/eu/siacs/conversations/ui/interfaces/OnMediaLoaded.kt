package eu.siacs.conversations.ui.interfaces

import eu.siacs.conversations.ui.util.Attachment

fun interface OnMediaLoaded {

    fun onMediaLoaded(attachments: List<Attachment>)
}
