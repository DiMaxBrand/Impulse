package eu.siacs.conversations.ui.util

import eu.siacs.conversations.Config
import eu.siacs.conversations.utils.UIHelper

object QuoteHelper {

    const val QUOTE_CHAR = '>'
    const val QUOTE_END_CHAR = '<' // used for one check, not for actual quoting
    const val QUOTE_ALT_CHAR = '»'
    const val QUOTE_ALT_END_CHAR = '«'

    @JvmStatic
    fun isPositionQuoteCharacter(body: CharSequence, pos: Int): Boolean {
        // second part of logical check actually goes against the logic indicated in the method name, since it also checks for context
        // but it's very useful
        return body[pos] == QUOTE_CHAR || isPositionAltQuoteStart(body, pos)
    }

    @JvmStatic
    fun isPositionQuoteEndCharacter(body: CharSequence, pos: Int): Boolean =
        body[pos] == QUOTE_END_CHAR

    @JvmStatic
    fun isPositionAltQuoteCharacter(body: CharSequence, pos: Int): Boolean =
        body[pos] == QUOTE_ALT_CHAR

    @JvmStatic
    fun isPositionAltQuoteEndCharacter(body: CharSequence, pos: Int): Boolean =
        body[pos] == QUOTE_ALT_END_CHAR

    @JvmStatic
    fun isPositionAltQuoteStart(body: CharSequence, pos: Int): Boolean =
        isPositionAltQuoteCharacter(body, pos)
                && isPositionPrecededByPreQuote(body, pos)
                && !isPositionFollowedByAltQuoteEnd(body, pos)

    @JvmStatic
    fun isPositionFollowedByQuoteChar(body: CharSequence, pos: Int): Boolean =
        body.length > pos + 1 && isPositionQuoteCharacter(body, pos + 1)

    /**
     * 'Prequote' means anything we require or can accept in front of a QuoteChar.
     */
    @JvmStatic
    fun isPositionPrecededByPreQuote(body: CharSequence, pos: Int): Boolean =
        UIHelper.isPositionPrecededByLineStart(body, pos)

    @JvmStatic
    fun isPositionQuoteStart(body: CharSequence, pos: Int): Boolean =
        (isPositionQuoteCharacter(body, pos)
                && isPositionPrecededByPreQuote(body, pos)
                && (UIHelper.isPositionFollowedByQuoteableCharacter(body, pos)
                || isPositionFollowedByQuoteChar(body, pos)))

    @JvmStatic
    fun bodyContainsQuoteStart(body: CharSequence): Boolean {
        for (i in body.indices) {
            if (isPositionQuoteStart(body, i)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun isPositionFollowedByAltQuoteEnd(body: CharSequence, pos: Int): Boolean {
        if (body.length <= pos + 1 || Character.isWhitespace(body[pos + 1])) {
            return false
        }
        var previousWasWhitespace = false
        for (i in pos + 1 until body.length) {
            val c = body[i]
            if (c == '\n' || isPositionAltQuoteCharacter(body, i)) {
                return false
            } else if (isPositionAltQuoteEndCharacter(body, i) && !previousWasWhitespace) {
                return true
            } else {
                previousWasWhitespace = Character.isWhitespace(c)
            }
        }
        return false
    }

    @JvmStatic
    fun isNestedTooDeeply(line: CharSequence): Boolean {
        if (isPositionQuoteStart(line, 0)) {
            var nestingDepth = 1
            for (i in 1 until line.length) {
                if (isPositionQuoteCharacter(line, i)) {
                    nestingDepth++
                } else if (line[i] != ' ') {
                    break
                }
            }
            return nestingDepth >= Config.QUOTING_MAX_DEPTH
        }
        return false
    }

    @JvmStatic
    fun replaceAltQuoteCharsInText(text: String): String {
        var result = text
        var i = 0
        while (i < result.length) {
            if (isPositionAltQuoteStart(result, i)) {
                result = result.substring(0, i) + QUOTE_CHAR + result.substring(i + 1)
            }
            i++
        }
        return result
    }
}
