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

object ImStyleParser {

    private val KEYWORDS = listOf('*', '_', '~', '`')
    private val NO_SUB_PARSING_KEYWORDS = listOf('`')
    private val BLOCK_KEYWORDS = listOf('`')
    private const val ALLOW_EMPTY = false
    private const val PARSE_HIGHER_ORDER_END = true

    @JvmStatic
    fun parse(text: CharSequence): List<Style> {
        return parse(text, 0, text.length - 1)
    }

    @JvmStatic
    fun parse(text: CharSequence, start: Int, end: Int): List<Style> {
        val styles = mutableListOf<Style>()
        var i = start
        while (i <= end) {
            val c = text[i]
            if (KEYWORDS.contains(c)
                && precededByWhiteSpace(text, i, start)
                && !followedByWhitespace(text, i, end)
            ) {
                if (BLOCK_KEYWORDS.contains(c) && isCharRepeatedTwoTimes(text, c, i + 1, end)) {
                    val to = seekEndBlock(text, c, i + 3, end)
                    if (to != -1 && (to != i + 5 || ALLOW_EMPTY)) {
                        val keyword = c.toString() + c + c
                        styles.add(Style(keyword, i, to))
                        i = to
                        i++
                        continue
                    }
                }
                val to = seekEnd(text, c, i + 1, end)
                if (to != -1 && (to != i + 1 || ALLOW_EMPTY)) {
                    styles.add(Style(c, i, to))
                    if (!NO_SUB_PARSING_KEYWORDS.contains(c)) {
                        styles.addAll(parse(text, i + 1, to - 1))
                    }
                    i = to
                }
            }
            i++
        }
        return styles
    }

    private fun isCharRepeatedTwoTimes(text: CharSequence, c: Char, index: Int, end: Int): Boolean {
        return index + 1 <= end && text[index] == c && text[index + 1] == c
    }

    private fun precededByWhiteSpace(text: CharSequence, index: Int, start: Int): Boolean {
        return index == start || Character.isWhitespace(text[index - 1])
    }

    private fun followedByWhitespace(text: CharSequence, index: Int, end: Int): Boolean {
        return index >= end || Character.isWhitespace(text[index + 1])
    }

    private fun seekEnd(text: CharSequence, needle: Char, start: Int, end: Int): Int {
        for (i in start..end) {
            val c = text[i]
            if (c == needle && !Character.isWhitespace(text[i - 1])) {
                return if (!PARSE_HIGHER_ORDER_END || followedByWhitespace(text, i, end)) {
                    i
                } else {
                    val higherOrder = seekHigherOrderEndWithoutNewBeginning(text, needle, i + 1, end)
                    if (higherOrder != -1) higherOrder else i
                }
            } else if (c == '\n') {
                return -1
            }
        }
        return -1
    }

    private fun seekHigherOrderEndWithoutNewBeginning(
        text: CharSequence,
        needle: Char,
        start: Int,
        end: Int
    ): Int {
        for (i in start..end) {
            val c = text[i]
            if (c == needle
                && precededByWhiteSpace(text, i, start)
                && !followedByWhitespace(text, i, end)
            ) {
                return -1 // new beginning
            } else if (c == needle
                && !Character.isWhitespace(text[i - 1])
                && followedByWhitespace(text, i, end)
            ) {
                return i
            } else if (c == '\n') {
                return -1
            }
        }
        return -1
    }

    private fun seekEndBlock(text: CharSequence, needle: Char, start: Int, end: Int): Int {
        for (i in start..end) {
            val c = text[i]
            if (c == needle && isCharRepeatedTwoTimes(text, needle, i + 1, end)) {
                return i + 2
            }
        }
        return -1
    }

    class Style {
        val keyword: String
        val start: Int
        val end: Int

        constructor(character: Char, start: Int, end: Int) : this(character.toString(), start, end)

        constructor(keyword: String, start: Int, end: Int) {
            this.keyword = keyword
            this.start = start
            this.end = end
        }
    }
}
