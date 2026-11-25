package eu.siacs.conversations.ui.glass

/** Auto renders the glass surface 11 fps below the panel; a manual value is an exact cap. */
internal fun glassSurfaceIntervalNanos(refreshRate: Float, requestedRate: Float): Long {
    val rate = refreshRate.takeIf { it.isFinite() && it > 0f } ?: 60f
    val target = if (requestedRate > 0f) {
        requestedRate.coerceAtMost(rate)
    } else {
        (rate - 11f).coerceAtLeast(1f)
    }
    return (1_000_000_000.0 / target).toLong().coerceAtLeast(1L)
}
