/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.ui.util

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.common.collect.Iterables
import de.gultsch.common.Linkify
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.persistance.FileBackend
import eu.siacs.conversations.ui.ConversationsActivity
import eu.siacs.conversations.ui.XmppActivity
import java.util.Arrays

object ShareUtil {

    private val SCHEMES_COPY_PATH_ONLY: Collection<String> = Arrays.asList("xmpp", "mailto", "tel")

    @JvmStatic
    fun share(activity: XmppActivity, message: Message) {
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        if (message.isGeoUri) {
            intent.putExtra(Intent.EXTRA_TEXT, message.body)
            intent.type = "text/plain"
        } else if (!message.isFileOrImage) {
            intent.putExtra(Intent.EXTRA_TEXT, message.body)
            intent.type = "text/plain"
            intent.putExtra(
                ConversationsActivity.EXTRA_AS_QUOTE,
                message.status == Message.STATUS_RECEIVED
            )
        } else {
            val file = activity.xmppConnectionService.fileBackend.getFile(message)
            val uri = FileBackend.getUriForFile(activity, file)
                .buildUpon()
                .appendQueryParameter("uuid", message.getUuid())
                .build()
            try {
                intent.putExtra(Intent.EXTRA_STREAM, uri)
            } catch (e: SecurityException) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.no_permission_to_access_x, file.absolutePath),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.type = ViewUtil.nullToWildcard(message.mimeType)
        }
        try {
            activity.startActivity(Intent.createChooser(intent, activity.getText(R.string.share_with)))
        } catch (e: ActivityNotFoundException) {
            // This should happen only on faulty androids because normally chooser is always available
            Toast.makeText(activity, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun copyToClipboard(activity: XmppActivity, message: Message) {
        if (activity.copyTextToClipboard(message.body, R.string.message)
            && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            Toast.makeText(activity, R.string.message_copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun copyUrlToClipboard(activity: XmppActivity, message: Message) {
        val url: String?
        @StringRes val resId: Int
        if (message.isGeoUri) {
            resId = R.string.location
            url = message.body
        } else if (message.hasFileOnRemoteHost()) {
            resId = R.string.file_url
            url = message.fileParams.url
        } else {
            val fileParams = message.fileParams
            url = if (fileParams != null && fileParams.url != null) fileParams.url else message.body.trim()
            resId = R.string.file_url
        }
        if (activity.copyTextToClipboard(url, resId)
            && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            Toast.makeText(activity, R.string.url_copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun copyLinkToClipboard(activity: XmppActivity, message: Message) {
        val firstUri = Iterables.getFirst(Linkify.getLinks(message.body), null) ?: return
        val clip: String?
        clip = if (SCHEMES_COPY_PATH_ONLY.contains(firstUri.scheme)) {
            firstUri.path
        } else {
            firstUri.raw
        }
        @StringRes val label: Int = when (firstUri.scheme) {
            "http", "https", "gemini" -> R.string.web_address
            "xmpp" -> R.string.account_settings_jabber_id
            else -> R.string.uri
        }
        @StringRes val toast: Int = when (firstUri.scheme) {
            "http", "https", "gemini", "web+ap" -> R.string.url_copied_to_clipboard
            "xmpp" -> R.string.jabber_id_copied_to_clipboard
            "tel" -> R.string.copied_phone_number
            "mailto" -> R.string.copied_email_address
            else -> R.string.uri_copied_to_clipboard
        }
        if (activity.copyTextToClipboard(clip, label)
            && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            Toast.makeText(activity, toast, Toast.LENGTH_SHORT).show()
        }
    }
}
