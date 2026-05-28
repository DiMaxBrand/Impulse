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

package eu.siacs.conversations.utils

import android.text.Spannable

object CharSequences {

    @JvmField
    val EMPTY_STRING: String = ""

    private fun getStartIndex(input: CharSequence): Int {
        val length = input.length
        var index = 0
        while (Character.isWhitespace(input[index])) {
            ++index
            if (index >= length) break
        }
        return index
    }

    private fun getEndIndex(input: CharSequence): Int {
        var index = input.length - 1
        while (Character.isWhitespace(input[index])) {
            --index
            if (index < 0) break
        }
        return index
    }

    @JvmStatic
    fun trim(input: CharSequence): CharSequence {
        val begin = getStartIndex(input)
        val end = getEndIndex(input)
        return if (begin > end) {
            ""
        } else {
            StylingHelper.subSequence(input, begin, end + 1)
        }
    }

    @JvmStatic
    fun split(charSequence: Spannable, c: Char): List<CharSequence> {
        val out = mutableListOf<CharSequence>()
        var begin = 0
        var i = 0
        while (i < charSequence.length) {
            if (charSequence[i] == c) {
                out.add(StylingHelper.subSequence(charSequence, begin, i))
                begin = ++i
            } else {
                i++
            }
        }
        if (begin < charSequence.length) {
            out.add(StylingHelper.subSequence(charSequence, begin, charSequence.length))
        }
        return out
    }

    @JvmStatic
    fun nullToEmpty(charSequence: CharSequence?): String {
        return charSequence?.toString() ?: ""
    }

    @JvmStatic
    fun isEmpty(charSequence: CharSequence?): Boolean {
        return charSequence == null || charSequence.isEmpty()
    }
}
