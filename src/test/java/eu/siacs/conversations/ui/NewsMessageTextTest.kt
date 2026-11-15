package eu.siacs.conversations.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NewsMessageTextTest {
    @Test fun selectsEitherOrderAndSupportsRussianMarker() {
        val post = "EN: Hello\nsecond line\nРУС: Привет\nвторая строка"
        assertEquals("Привет\nвторая строка", NewsMessageText.select(post, "ru"))
        assertEquals("Hello\nsecond line", NewsMessageText.select(post, "de"))
        assertEquals("Hello", NewsMessageText.select("RU: Привет\nEN: Hello", "en"))
    }

    @Test fun keepsOrdinaryOrIncompletePostsIntact() {
        for (post in listOf("Hello", "EN: Hello", "Intro\nEN: Hello\nRU: Привет", "EN: Hello\nRU:")) {
            assertEquals(post, NewsMessageText.select(post, "ru"))
        }
    }
}
