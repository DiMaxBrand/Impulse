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

import com.google.common.base.Joiner
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import net.fellbaum.jemoji.EmojiManager
import java.util.regex.Pattern

object Emoticons {

    private const val MAX_EMOJIS = 42

    private const val VARIATION_16 = 0xFE0F
    private const val VARIATION_15 = 0xFE0E
    private val VARIATION_16_STRING = String(charArrayOf(VARIATION_16.toChar()))
    private val VARIATION_15_STRING = String(charArrayOf(VARIATION_15.toChar()))

    private val TEXT_DEFAULT_TO_VS16: Set<String> = ImmutableSet.of(
        "❤",
        "✔",
        "✖",
        "➕",
        "➖",
        "➗",
        "⭐",
        "⚡",
        "🎖",
        "🏆",
        "🥇",
        "🥈",
        "🥉",
        "👑",
        "⚓",
        "⛵",
        "✈",
        "⚖",
        "⛑",
        "⚒",
        "⛏",
        "☎",
        "⛄",
        "⛅",
        "⚠",
        "⚛",
        "✡",
        "☮",
        "☯",
        "☀",
        "⬅",
        "➡",
        "⬆",
        "⬇"
    )

    private val CACHE = CacheBuilder.newBuilder()
        .maximumSize(256)
        .build(object : CacheLoader<CharSequence, Pattern>() {
            override fun load(key: CharSequence): Pattern = generatePattern(key)
        })

    @JvmStatic
    fun normalizeToVS16(input: String): String {
        return if (TEXT_DEFAULT_TO_VS16.contains(input) && !input.endsWith(VARIATION_15_STRING))
            input + VARIATION_16_STRING
        else
            input
    }

    @JvmStatic
    fun existingVariant(original: String, existing: Set<String>): String {
        if (existing.contains(original) || original.endsWith(VARIATION_15_STRING)) {
            return original
        }
        val variant = if (original.endsWith(VARIATION_16_STRING))
            original.substring(0, original.length - 1)
        else
            original + VARIATION_16_STRING
        return if (existing.contains(variant)) variant else original
    }

    @JvmStatic
    fun getEmojiPattern(input: CharSequence): Pattern {
        return CACHE.getUnchecked(input)
    }

    private fun generatePattern(input: CharSequence): Pattern {
        val emojis = EmojiManager.extractEmojis(CharSequences.nullToEmpty(input))
        return Pattern.compile(
            Joiner.on('|').join(
                Iterables.transform(
                    Iterables.limit(emojis, MAX_EMOJIS)
                ) { e -> Pattern.quote(e.emoji) }
            )
        )
    }

    @JvmStatic
    fun isEmoji(input: String): Boolean = EmojiManager.isEmoji(input)

    @JvmStatic
    fun isOnlyEmoji(input: String): Boolean = EmojiManager.removeAllEmojis(input).isEmpty()
}
