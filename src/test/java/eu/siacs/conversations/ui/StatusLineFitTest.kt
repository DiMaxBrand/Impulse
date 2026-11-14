package eu.siacs.conversations.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusLineFitTest {
    @Test fun singleLetterExpandsToIncludeTheWholeStatus() {
        val fit = fitStatusLine(10, 24, 10f, 0f, 24f, 60, 18, 300, 8, false, 0f)
        assertEquals(78, fit.width)
        assertEquals(6, fit.statusY)
    }

    @Test fun singleLineCanShareWithATallerFooter() {
        val fit = fitStatusLine(15, 18, 15f, 0f, 18f, 60, 24, 300, 8, false, 0f)
        assertEquals(83, fit.width)
        assertEquals(0, fit.statusY)
        assertEquals(6, fit.textY)
    }

    @Test fun shortLastLineSharesButAnEditedLabelCanPushTheFooterBelow() {
        val fits = fitStatusLine(300, 48, 210f, 24f, 48f, 82, 18, 300, 8, false, 0f)
        val edited = fitStatusLine(300, 48, 210f, 24f, 48f, 83, 18, 300, 8, false, 0f)
        assertEquals(30, fits.statusY)
        assertEquals(48, edited.statusY)
        assertTrue(fits.width <= 300 && edited.width <= 300)
    }

    @Test fun tallFooterAndRtlUseTheirOwnLine() {
        assertEquals(48, fitStatusLine(300, 48, 100f, 24f, 48f, 60, 30, 300, 8, false, 0f).statusY)
        assertEquals(48, fitStatusLine(300, 48, 100f, 24f, 48f, 60, 18, 300, 8, true, 0f).statusY)
    }
}
