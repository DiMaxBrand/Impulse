package eu.siacs.conversations.utils

import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.base.Strings
import com.google.common.collect.Iterables
import com.google.common.io.BaseEncoding

object AsciiArmor {
    @JvmStatic
    fun decode(input: String?): ByteArray {
        val lines = Splitter.on('\n').splitToList(Strings.nullToEmpty(input).trim())
        if (lines.size == 1) {
            val line = lines[0]
            if (line.length > 1) {
                val end = line.lastIndexOf('=')
                if (end >= 1) {
                    return BaseEncoding.base64().decode(line.substring(0, end))
                }
            }
        }
        val withoutChecksum = if (Iterables.getLast(lines)[0] == '=') {
            Joiner.on("").join(lines.subList(0, lines.size - 1))
        } else {
            Joiner.on("").join(lines)
        }
        return BaseEncoding.base64().decode(withoutChecksum)
    }
}
