/*
 * Copyright (c) 2018-2019, Daniel Gultsch All rights reserved.
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

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.LruCache
import androidx.annotation.ColorInt
import com.google.android.material.color.MaterialColors
import eu.siacs.conversations.xmpp.Jid
import java.util.regex.Pattern

object IrregularUnicodeDetector {

    private val NORMALIZATION_MAP: Map<Character.UnicodeBlock, Character.UnicodeBlock> =
        mapOf(Character.UnicodeBlock.LATIN_1_SUPPLEMENT to Character.UnicodeBlock.BASIC_LATIN)

    private val CACHE = LruCache<Jid, PatternTuple>(4096)

    private val AMBIGUOUS_CYRILLIC = listOf(
        "а", "г", "е", "ѕ", "і", "ј", "ķ", "ԛ", "о", "р", "с", "у", "х"
    )

    private fun normalize(block: Character.UnicodeBlock): Character.UnicodeBlock {
        return NORMALIZATION_MAP[block] ?: block
    }

    @JvmStatic
    fun style(context: Context, jid: Jid): Spannable {
        return style(
            jid,
            MaterialColors.getColor(
                context,
                androidx.appcompat.R.attr.colorError,
                "colorError not found"
            )
        )
    }

    private fun style(jid: Jid, @ColorInt color: Int): Spannable {
        val patternTuple = find(jid)
        val builder = SpannableStringBuilder()
        if (jid.local != null && patternTuple.local != null) {
            val local = SpannableString(jid.local)
            colorize(local, patternTuple.local, color)
            builder.append(local)
            builder.append('@')
        }
        if (jid.domain != null) {
            val labels = jid.domain.toString().split("\\.")
            for (i in labels.indices) {
                val spannableString = SpannableString(labels[i])
                colorize(spannableString, patternTuple.domain[i], color)
                if (i != 0) {
                    builder.append('.')
                }
                builder.append(spannableString)
            }
        }
        if (builder.isNotEmpty() && jid.resource != null) {
            builder.append('/')
            builder.append(jid.resource)
        }
        return builder
    }

    private fun colorize(spannableString: SpannableString, pattern: Pattern, @ColorInt color: Int) {
        val matcher = pattern.matcher(spannableString)
        while (matcher.find()) {
            if (matcher.start() < matcher.end()) {
                spannableString.setSpan(
                    ForegroundColorSpan(color),
                    matcher.start(),
                    matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private fun mapCompat(word: String): MutableMap<Character.UnicodeBlock, MutableList<String>> {
        val map = mutableMapOf<Character.UnicodeBlock, MutableList<String>>()
        val length = word.length
        var offset = 0
        while (offset < length) {
            val codePoint = word.codePointAt(offset)
            offset += Character.charCount(codePoint)
            if (!Character.isLetter(codePoint)) continue
            val block = normalize(Character.UnicodeBlock.of(codePoint))
            val codePoints = map.getOrPut(block) { mutableListOf() }
            codePoints.add(String(Character.toChars(codePoint)))
        }
        return map
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun map(word: String): MutableMap<Character.UnicodeScript, MutableList<String>> {
        val map = mutableMapOf<Character.UnicodeScript, MutableList<String>>()
        val length = word.length
        var offset = 0
        while (offset < length) {
            val codePoint = word.codePointAt(offset)
            val script = Character.UnicodeScript.of(codePoint)
            if (script != Character.UnicodeScript.COMMON) {
                val codePoints = map.getOrPut(script) { mutableListOf() }
                codePoints.add(String(Character.toChars(codePoint)))
            }
            offset += Character.charCount(codePoint)
        }
        return map
    }

    private fun eliminateFirstAndGetCodePointsCompat(
        map: MutableMap<Character.UnicodeBlock, MutableList<String>>
    ): Set<String> {
        return eliminateFirstAndGetCodePoints(map, Character.UnicodeBlock.BASIC_LATIN)
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun eliminateFirstAndGetCodePoints(
        map: MutableMap<Character.UnicodeScript, MutableList<String>>
    ): Set<String> {
        return eliminateFirstAndGetCodePoints(map, Character.UnicodeScript.COMMON)
    }

    private fun <T> eliminateFirstAndGetCodePoints(
        map: MutableMap<T, MutableList<String>>,
        defaultPick: T
    ): Set<String> {
        var pick = defaultPick
        var size = 0
        for ((key, value) in map) {
            if (value.size > size) {
                size = value.size
                pick = key
            }
        }
        map.remove(pick)
        val all = mutableSetOf<String>()
        for (codePoints in map.values) {
            all.addAll(codePoints)
        }
        return all
    }

    private fun findIrregularCodePoints(word: String): Set<String> {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            val map = mapCompat(word)
            val set = asSet(map)
            if (containsOnlyAmbiguousCyrillic(set)) {
                set
            } else {
                eliminateFirstAndGetCodePointsCompat(map)
            }
        } else {
            val map = map(word)
            val set = asSet(map)
            if (containsOnlyAmbiguousCyrillic(set)) {
                set
            } else {
                eliminateFirstAndGetCodePoints(map)
            }
        }
    }

    private fun asSet(map: Map<*, List<String>>): Set<String> {
        val flat = mutableSetOf<String>()
        for (value in map.values) {
            flat.addAll(value)
        }
        return flat
    }

    private fun containsOnlyAmbiguousCyrillic(codePoints: Collection<String>): Boolean {
        for (codePoint in codePoints) {
            if (!AMBIGUOUS_CYRILLIC.contains(codePoint)) return false
        }
        return true
    }

    private fun find(jid: Jid): PatternTuple {
        synchronized(CACHE) {
            var pattern = CACHE.get(jid)
            if (pattern != null) return pattern
            pattern = PatternTuple.of(jid)
            CACHE.put(jid, pattern)
            return pattern
        }
    }

    private fun create(codePoints: Set<String>): Pattern {
        val pattern = StringBuilder()
        for (codePoint in codePoints) {
            if (pattern.isNotEmpty()) {
                pattern.append('|')
            }
            pattern.append(Pattern.quote(codePoint))
        }
        return Pattern.compile(pattern.toString())
    }

    private class PatternTuple(val local: Pattern?, val domain: List<Pattern>) {
        companion object {
            fun of(jid: Jid): PatternTuple {
                val localPattern = if (jid.local != null)
                    create(findIrregularCodePoints(jid.local))
                else
                    null
                val domain = jid.domain.toString()
                val domainPatterns = mutableListOf<Pattern>()
                for (label in domain.split("\\.")) {
                    domainPatterns.add(create(findIrregularCodePoints(label)))
                }
                return PatternTuple(localPattern, domainPatterns)
            }
        }
    }
}
