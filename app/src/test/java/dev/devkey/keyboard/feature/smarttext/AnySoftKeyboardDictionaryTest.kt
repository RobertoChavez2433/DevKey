package dev.devkey.keyboard.feature.smarttext

import dev.devkey.keyboard.testutil.anySoftKeyboardWordlistBytes
import dev.devkey.keyboard.testutil.loadAnySoftKeyboardDictionaryWithWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class AnySoftKeyboardDictionaryTest {

    @Test
    fun `load parses AnySoftKeyboard combined wordlist metadata`() {
        val dictionary = AnySoftKeyboardDictionary()
        val stats = dictionary.load(
            ByteArrayInputStream(
                anySoftKeyboardWordlistBytes("hello" to 1000, "world" to 500)
            )
        )

        assertEquals(2, dictionary.size)
        assertEquals(2, stats.wordCount)
        assertEquals("anysoftkeyboard_language_pack", stats.metadata.sourceName)
        assertEquals("en", stats.metadata.locale)
        assertEquals("English", stats.metadata.description)
        assertEquals(54, stats.metadata.version)
        assertTrue("Expected compressed artifact byte count", stats.sourceBytesRead > 0)
        assertTrue(dictionary.isValidWord("hello"))
        assertTrue(dictionary.isValidWord("World"))
    }

    @Test
    fun `getSuggestions returns frequency ordered completions`() {
        val dictionary = loadAnySoftKeyboardDictionaryWithWords(
            "hello" to 1000,
            "help" to 900,
            "held" to 800,
            "zebra" to 700,
        )

        val suggestions = dictionary.getSuggestions("he", maxResults = 3)

        assertEquals(
            listOf("hello", "help", "held"),
            suggestions.map { it.first },
        )
    }

    @Test
    fun `getSuggestions filters exact match`() {
        val dictionary = loadAnySoftKeyboardDictionaryWithWords(
            "hello" to 1000,
            "help" to 900,
        )

        val suggestions = dictionary.getSuggestions("hello", maxResults = 3)

        assertTrue(suggestions.none { it.first == "hello" })
    }

    @Test
    fun `getFuzzyCorrections covers common typos`() {
        val dictionary = loadAnySoftKeyboardDictionaryWithWords(
            "hello" to 3000,
            "the" to 4000,
            "there" to 1500,
        )

        assertEquals("hello", dictionary.getFuzzyCorrections("helo").first().first)
        assertEquals("the", dictionary.getFuzzyCorrections("teh").first().first)
        assertEquals("hello", dictionary.getFuzzyCorrections("helloo").first().first)
    }

    @Test
    fun `isValidWord rejects unknown and structured tokens`() {
        val dictionary = loadAnySoftKeyboardDictionaryWithWords("hello" to 1000)

        assertTrue(dictionary.isValidWord("hello"))
        assertFalse(dictionary.isValidWord("unknown"))
        assertFalse(dictionary.isValidWord("abc1"))
    }

    @Test
    fun `editDistance supports insertion deletion substitution and transpose distance`() {
        val dictionary = AnySoftKeyboardDictionary()

        assertEquals(0, dictionary.editDistance("hello", "hello"))
        assertEquals(1, dictionary.editDistance("hell", "hello"))
        assertEquals(1, dictionary.editDistance("hello", "hallo"))
        assertEquals(2, dictionary.editDistance("teh", "the"))
    }
}
