package eu.siacs.conversations.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseTextTest {
    @Test fun selectsTitlesOnSeparateOrSameLinesAndKeepsLegacyTitles() {
        val title = "RU: Импульс 1: Исправлено EN: Impulse 1: Fixed"
        assertEquals("Импульс 1: Исправлено", ReleaseText.title(title, "ru"))
        assertEquals("Impulse 1: Fixed", ReleaseText.title(title.replace(" EN:", "\nEN:"), "fr"))
        assertEquals("Impulse 1: Fixed", ReleaseText.title("Impulse 1: Fixed", "en"))
    }

    @Test fun selectsNotesAndRestrictsDeveloperAndGeneratedSections() {
        val notes = """
            ## Что нового
            Русский текст BUG-1234
            ## What's new
            English text BUG-1234
            ## Developer notes
            Internal detail
            ## What's Changed
            Generated changes
        """.trimIndent()
        assertEquals("## Что нового\n\nРусский текст BUG-1234", ReleaseText.notes(notes, "ru", false))
        assertEquals("## What's new\n\nEnglish text BUG-1234", ReleaseText.notes(notes, "de", false))
        val support = ReleaseText.notes(notes, "en", true)!!
        assertTrue(support.contains("Internal detail") && support.contains("Generated changes"))
        assertTrue(support.contains("## Developer notes"))
        assertFalse(support.contains("Русский текст"))
    }

    @Test fun handlesLegacyHeadingsAndUnstructuredBodies() {
        val legacy = "**RU**\nРусский\n\n**EN**\nEnglish\n\n**Developer notes**\nTechnical"
        assertEquals("## What's new\n\nEnglish", ReleaseText.notes(legacy, "en", false))
        assertEquals("## Что нового\n\nРусский", ReleaseText.notes(legacy, "ru", false))
        assertEquals("## What's new\n\nPlain notes", ReleaseText.notes("Plain notes\n## Developer notes\nTechnical", "en", false))
    }
}
