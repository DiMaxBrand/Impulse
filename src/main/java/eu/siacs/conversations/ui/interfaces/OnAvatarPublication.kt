package eu.siacs.conversations.ui.interfaces

import androidx.annotation.StringRes

interface OnAvatarPublication {

    fun onAvatarPublicationSucceeded()

    fun onAvatarPublicationFailed(@StringRes res: Int)
}
