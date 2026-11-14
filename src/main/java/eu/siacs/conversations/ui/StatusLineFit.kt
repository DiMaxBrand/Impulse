package eu.siacs.conversations.ui

internal data class StatusLineFit(val width: Int, val statusY: Int)

internal fun fitStatusLine(
    textWidth: Int, textHeight: Int, lastRight: Float, lastTop: Float, lastBottom: Float,
    statusWidth: Int, statusHeight: Int, maxWidth: Int, gap: Int, rtl: Boolean,
    verticalTolerance: Float,
): StatusLineFit {
    val required = kotlin.math.ceil(lastRight).toInt() + gap + statusWidth
    val shared = !rtl && required <= maxWidth && statusHeight <= lastBottom - lastTop + verticalTolerance
    return StatusLineFit(
        maxOf(textWidth, statusWidth, if (shared) required else 0).coerceAtMost(maxWidth),
        if (shared) maxOf(0, kotlin.math.ceil(lastBottom).toInt() - statusHeight) else textHeight,
    )
}
