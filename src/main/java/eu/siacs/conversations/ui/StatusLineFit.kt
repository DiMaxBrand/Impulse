package eu.siacs.conversations.ui

internal data class StatusLineFit(val width: Int, val statusY: Int, val textY: Int = 0)

internal fun fitStatusLine(
    textWidth: Int, textHeight: Int, lastRight: Float, lastTop: Float, lastBottom: Float,
    statusWidth: Int, statusHeight: Int, maxWidth: Int, gap: Int, rtl: Boolean,
    verticalTolerance: Float,
): StatusLineFit {
    val required = kotlin.math.ceil(lastRight).toInt() + gap + statusWidth
    // On a single line the row can grow taller without covering preceding text. For multiline
    // text, a taller footer would overlap the previous line, so keep it below in that case.
    val shared = !rtl && required <= maxWidth &&
        (lastTop <= 0f || statusHeight <= lastBottom - lastTop + verticalTolerance)
    val textY = if (shared && lastTop <= 0f) (statusHeight - kotlin.math.ceil(lastBottom).toInt()).coerceAtLeast(0) else 0
    return StatusLineFit(
        maxOf(textWidth, statusWidth, if (shared) required else 0).coerceAtMost(maxWidth),
        if (shared) maxOf(0, kotlin.math.ceil(lastBottom).toInt() + textY - statusHeight) else textHeight,
        textY,
    )
}
