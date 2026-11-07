package com.arflix.tv.ui.screens.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsOverviewFitTest {

    /** Stand-in for the TextMeasurer: a text fits when it has at most [max] characters. */
    private fun maxChars(max: Int): (String) -> Boolean = { it.length <= max }

    private val overview =
        "A young wizard joins a school of magic. He makes two friends, Ron and Hermione. " +
            "Together they uncover a dark secret beneath the castle."

    @Test
    fun `whole text is kept when it fits`() {
        assertEquals(overview, fitOverviewToWholeSentences(overview, maxChars(1_000)))
    }

    @Test
    fun `keeps only the whole sentences that fit`() {
        val result = fitOverviewToWholeSentences(overview, maxChars(90))
        assertEquals(
            "A young wizard joins a school of magic. He makes two friends, Ron and Hermione.",
            result
        )
    }

    @Test
    fun `keeps a single sentence when the second does not fit`() {
        val result = fitOverviewToWholeSentences(overview, maxChars(50))
        assertEquals("A young wizard joins a school of magic.", result)
    }

    @Test
    fun `cuts the first sentence at the last clause boundary that fits`() {
        val text = "In a city ruled by fear, a lone detective, haunted by his past, hunts a killer nobody has seen."
        val result = fitOverviewToWholeSentences(text, maxChars(45))
        assertEquals("In a city ruled by fear, a lone detective.", result)
    }

    @Test
    fun `dash is a clause boundary too`() {
        val text = "Two rivals meet again — and this time only one of them will walk away from the fight alive."
        val result = fitOverviewToWholeSentences(text, maxChars(30))
        assertEquals("Two rivals meet again.", result)
    }

    @Test
    fun `falls back to whole words when no clause boundary fits`() {
        val text = "An astronaut stranded on a distant planet fights to survive until rescue arrives."
        val result = fitOverviewToWholeSentences(text, maxChars(30))
        assertEquals("An astronaut stranded on a.", result)
        assertTrue(result.length <= 30)
    }

    @Test
    fun `never ends with an ellipsis`() {
        val text = "Something strange is happening in town... Nobody knows what it is. The sheriff wants answers…"
        for (max in listOf(20, 45, 70, 95, 1_000)) {
            val result = fitOverviewToWholeSentences(text, maxChars(max))
            assertFalse("max=$max -> $result", result.endsWith("…"))
            assertFalse("max=$max -> $result", result.endsWith(".."))
            if (result.isNotEmpty()) {
                assertTrue("max=$max -> $result", result.last() in setOf('.', '!', '?'))
            }
        }
        assertEquals(
            "Something strange is happening in town... Nobody knows what it is. The sheriff wants answers.",
            fitOverviewToWholeSentences(text, maxChars(1_000))
        )
    }

    @Test
    fun `adds a full stop when the text has none`() {
        assertEquals("A heist goes wrong.", fitOverviewToWholeSentences("A heist goes wrong", maxChars(100)))
    }

    @Test
    fun `abbreviations and initials do not split sentences`() {
        val text = "Dr. Grant and J. R. Hammond visit the park. Then the power fails."
        assertEquals(
            listOf("Dr. Grant and J. R. Hammond visit the park.", "Then the power fails."),
            splitOverviewSentences(text)
        )
        assertEquals(
            "Dr. Grant and J. R. Hammond visit the park.",
            fitOverviewToWholeSentences(text, maxChars(50))
        )
    }

    @Test
    fun `question and exclamation marks end sentences and quotes stay attached`() {
        val text = "\"Who are you?\" she asks. He laughs! Then he leaves without a word."
        assertEquals(
            listOf("\"Who are you?\" she asks.", "He laughs!", "Then he leaves without a word."),
            splitOverviewSentences(text)
        )
        assertEquals("\"Who are you?\" she asks. He laughs!", fitOverviewToWholeSentences(text, maxChars(40)))
    }

    @Test
    fun `blank input gives empty text`() {
        assertEquals("", fitOverviewToWholeSentences("   ", maxChars(10)))
    }

    @Test
    fun `whitespace and newlines are collapsed`() {
        assertEquals(
            "One line. Two line.",
            fitOverviewToWholeSentences("One line.\n\nTwo   line.", maxChars(100))
        )
    }

    @Test
    fun `end with sentence stop handles ellipsis inside closing quote`() {
        assertEquals("He said \"wait.\"", endWithSentenceStop("He said \"wait…\""))
        assertEquals("Run!", endWithSentenceStop("Run!"))
        assertEquals("A clause.", endWithSentenceStop("A clause,"))
    }
}

class DetailsPersonalHelpersTest {

    @Test
    fun `shows add auto-play after Watchlist and movies cannot focus it`() {
        assertEquals(PERSONAL_WATCHLIST_BUTTON_INDEX, personalNextButtonIndex(PERSONAL_WATCHED_BUTTON_INDEX, true, isShow = true))
        assertEquals(PERSONAL_AUTOPLAY_BUTTON_INDEX, personalNextButtonIndex(PERSONAL_WATCHLIST_BUTTON_INDEX, true, isShow = true))
        assertEquals(PERSONAL_AUTOPLAY_BUTTON_INDEX, personalNextButtonIndex(PERSONAL_AUTOPLAY_BUTTON_INDEX, true, isShow = true))
        assertEquals(PERSONAL_WATCHLIST_BUTTON_INDEX, personalNextButtonIndex(PERSONAL_AUTOPLAY_BUTTON_INDEX, false, isShow = true))
        assertEquals(PERSONAL_PLAY_BUTTON_INDEX, personalNextButtonIndex(PERSONAL_AUTOPLAY_BUTTON_INDEX, true, isShow = false))
    }

    @Test
    fun `movie button row follows Play Watched Watchlist`() {
        assertEquals(PERSONAL_WATCHED_BUTTON_INDEX, personalNextButtonIndex(PERSONAL_PLAY_BUTTON_INDEX, moveRight = true))
        assertEquals(PERSONAL_PLAY_BUTTON_INDEX, personalNextButtonIndex(PERSONAL_PLAY_BUTTON_INDEX, moveRight = false))
        assertEquals(PERSONAL_WATCHLIST_BUTTON_INDEX, personalNextButtonIndex(PERSONAL_WATCHED_BUTTON_INDEX, moveRight = true))
        assertEquals(PERSONAL_WATCHLIST_BUTTON_INDEX, personalNextButtonIndex(PERSONAL_WATCHLIST_BUTTON_INDEX, moveRight = true))
        assertEquals(PERSONAL_WATCHED_BUTTON_INDEX, personalNextButtonIndex(PERSONAL_WATCHLIST_BUTTON_INDEX, moveRight = false))
        assertEquals(PERSONAL_PLAY_BUTTON_INDEX, personalNextButtonIndex(PERSONAL_WATCHED_BUTTON_INDEX, moveRight = false))
        // Indices of removed buttons never stick.
        for (removed in listOf(1, 2, 5, 6)) {
            assertEquals(PERSONAL_PLAY_BUTTON_INDEX, personalNextButtonIndex(removed, moveRight = true))
        }
    }

    @Test
    fun `backdrop asks for the 1280 wide TMDB image instead of original`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w1280/abc.jpg",
            personalDetailsBackdropUrl("https://image.tmdb.org/t/p/original/abc.jpg")
        )
        // Already smaller: kept.
        assertEquals(
            "https://image.tmdb.org/t/p/w780/abc.jpg",
            personalDetailsBackdropUrl("https://image.tmdb.org/t/p/w780/abc.jpg")
        )
        // Not TMDB: unchanged.
        assertEquals("https://example.com/art.jpg", personalDetailsBackdropUrl("https://example.com/art.jpg"))
        assertEquals(null, personalDetailsBackdropUrl(null))
    }
}
