package dev.devkey.keyboard.feature.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryProviderTest {

    @Test
    fun `null suggest returns empty suggestions`() {
        val provider = DictionaryProvider(null)
        val result = provider.getSuggestions("hello")
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `null suggest returns false for isValidWord`() {
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
    fun `updateSuggest replaces reference`() {
        val provider = DictionaryProvider(null)
        // After update with null, still returns empty
        provider.updateSuggest(null)
        assertEquals(emptyList<String>(), provider.getSuggestions("test"))
    }
}
