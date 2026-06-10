/*
 * Copyright (c) 2017, Daniel Gultsch All rights reserved.
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

package eu.siacs.conversations.utils

import com.google.common.base.Strings
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.http.AesGcmURL
import eu.siacs.conversations.http.URL
import eu.siacs.conversations.ui.util.QuoteHelper
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

object MessageUtils {

    private val LTR_RTL = Pattern.compile("(‎[^‏]*‏){3,}")

    @JvmStatic
    fun prepareQuote(message: Message): String {
        // For media messages, return a human-readable placeholder instead of the raw URL
        when (message.type) {
            Message.TYPE_IMAGE -> return "📷 Photo"
            Message.TYPE_FILE -> {
                val fileParams = message.fileParams
                val url = fileParams?.url
                if (url != null) {
                    val name = url.substringAfterLast('/').substringBefore('?').ifEmpty { null }
                    if (name != null) return "📎 $name"
                }
                return "📎 File"
            }
            Message.TYPE_PRIVATE_FILE -> return "📎 File"
            Message.TYPE_RTP_SESSION -> return "📞 Call"
        }

        val builder = StringBuilder()
        val body: String
        if (message.hasMeCommand()) {
            val nick: String
            if (message.status == Message.STATUS_RECEIVED) {
                nick = if (message.conversation.getMode() == Conversational.MODE_MULTI) {
                    Strings.nullToEmpty(message.counterpart.resource)
                } else {
                    message.contact.publicDisplayName
                }
            } else {
                nick = UIHelper.getMessageDisplayName(message)
            }
            body = nick + " " + message.body.substring(Message.ME_COMMAND.length)
        } else {
            body = message.body
        }
        for (line in body.split("\n")) {
            if (line.isEmpty() || QuoteHelper.isNestedTooDeeply(line)) {
                continue
            }
            if (builder.isNotEmpty()) {
                builder.append('\n')
            }
            builder.append(line.trim())
        }
        return builder.toString()
    }

    @JvmStatic
    fun replyPreview(message: Message): String {
        return when (message.type) {
            Message.TYPE_IMAGE -> "📷 Photo"
            Message.TYPE_FILE, Message.TYPE_PRIVATE_FILE -> {
                val url = message.fileParams?.url
                val name = url?.substringAfterLast('/')?.substringBefore('?')?.ifEmpty { null }
                if (name != null) "📎 $name" else "📎 File"
            }
            Message.TYPE_RTP_SESSION -> "📞 Call"
            else -> message.body.trim().let {
                if (it.length > 120) it.take(120) + "…" else it
            }
        }
    }

    @JvmStatic
    fun treatAsDownloadable(body: String, oob: Boolean): Boolean {
        val lines = body.split("\n")
        if (lines.isEmpty()) {
            return false
        }
        for (line in lines) {
            if (line.contains("\\s+")) {
                return false
            }
        }
        val uri: URI
        try {
            uri = URI(lines[0])
        } catch (e: URISyntaxException) {
            return false
        }
        if (!URL.WELL_KNOWN_SCHEMES.contains(uri.scheme)) {
            return false
        }
        val ref = uri.fragment
        val protocol = uri.scheme
        val encrypted = ref != null && AesGcmURL.IV_KEY.matcher(ref).matches()
        val followedByDataUri = lines.size == 2 && lines[1].startsWith("data:")
        val validAesGcm =
            AesGcmURL.PROTOCOL_NAME.equals(protocol, ignoreCase = true)
                && encrypted
                && (lines.size == 1 || followedByDataUri)
        val validProtocol =
            "http".equals(protocol, ignoreCase = true) || "https".equals(protocol, ignoreCase = true)
        val validOob = validProtocol && (oob || encrypted) && lines.size == 1
        return validAesGcm || validOob
    }

    @JvmStatic
    fun filterLtrRtl(body: String): String {
        return LTR_RTL.matcher(body).replaceFirst(CharSequences.EMPTY_STRING)
    }

    @JvmStatic
    fun unInitiatedButKnownSize(message: Message): Boolean {
        return message.type == Message.TYPE_TEXT
            && message.transferable == null
            && message.isOOb
            && message.fileParams.size != null
            && message.fileParams.url != null
    }
}
