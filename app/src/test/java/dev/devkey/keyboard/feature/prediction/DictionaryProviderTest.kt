package dev.devkey.keyboard.feature.prediction

import dev.devkey.keyboard.suggestion.engine.Suggest
import dev.devkey.keyboard.testutil.loadAnySoftKeyboardDictionaryWithWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class DictionaryProviderTest {

    @Test
    fun `missing donor dictionary returns empty suggestions`() {
        val provider = DictionaryProvider(null)
        val result = provider.getSuggestions("hello")
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `missing donor dictionary returns false for isValidWord`() {
        val provider = DictionaryProvider(null)
        assertFalse(provider.isValidWord("hello"))
    }

    @Test
    fun `empty word returns empty suggestions`() {
        val provider = DictionaryProvider(null)
        assertEquals(emptyList<String>(), provider.getSuggestions(""))
    }

    @Test
    fun `empty word returns false for isValidWord`() {
        val provider = DictionaryProvider(null)
        assertFalse(provider.isValidWord(""))
    }

    @Test
    fun `donor dictionary provides suggestions validity and fuzzy corrections`() {
        val provider = DictionaryProvider(null)
        provider.donorDictionary = loadAnySoftKeyboardDictionaryWithWords(
            "hello" to 1000,
            "help" to 900,
            "the" to 2000,
        )

        assertTrue(provider.hasDictionary())
        assertEquals("anysoftkeyboard_language_pack", provider.activeDictionarySource())
        assertEquals(listOf("hello", "help"), provider.getSuggestions("he", maxResults = 2))
        assertEquals(listOf("the"), provider.getFuzzyCorrections("teh", maxResults = 1))
        assertTrue(provider.isValidWord("hello"))
        assertFalse(provider.isValidWord("helo"))
    }

    @Test
    fun `legacy suggest is not consulted for smart text`() {
        val legacySuggest = mock<Suggest>()
        val provider = DictionaryProvider(legacySuggest)

        provider.updateSuggest(legacySuggest)

        assertEquals(emptyList<String>(), provider.getSuggestions("hello"))
        assertEquals(emptyList<String>(), provider.getFuzzyCorrections("helo"))
        assertFalse(provider.isValidWord("hello"))
        verifyNoInteractions(legacySuggest)
    }
}
