package dev.devkey.keyboard.feature.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutocorrectEngineTest {

    private lateinit var engine: AutocorrectEngine
    private lateinit var fakeDictProvider: FakeDictionaryProvider

    @Before
    fun setUp() {
        fakeDictProvider = FakeDictionaryProvider()
        engine = AutocorrectEngine(fakeDictProvider)
    }

    // --- OFF returns None ---

    @Test
    fun `OFF aggressiveness always returns None`() {
        engine.aggressiveness = AutocorrectEngine.Aggressiveness.OFF
        fakeDictProvider.fuzzyCorrections = listOf("the")
        fakeDictProvider.validWords = setOf("the")

        val result = engine.getCorrection("teh", emptySet())
        assertEquals(AutocorrectResult.None, result)
    }

    @Test
    fun `empty word returns None`() {
        val result = engine.getCorrection("", emptySet())
        assertEquals(AutocorrectResult.None, result)
    }

    @Test
    fun `alpha numeric word returns None`() {
        engine.aggressiveness = AutocorrectEngine.Aggressiveness.AGGRESSIVE
        fakeDictProvider.fuzzyCorrections = listOf("abc")

        val result = engine.getCorrection("abc1", emptySet())
        assertEquals(AutocorrectResult.None, result)
    }

    // --- Learned words skip ---

    @Test
    fun `learned word is not corrected`() {
        fakeDictProvider.fuzzyCorrections = listOf("the")
        val result = engine.getCorrection("teh", setOf("teh"))
        assertEquals(AutocorrectResult.None, result)
    }

    @Test
    fun `learned word check is case-insensitive`() {
        fakeDictProvider.fuzzyCorrections = listOf("the")
        val result = engine.getCorrection("Teh", setOf("teh"))
        assertEquals(AutocorrectResult.None, result)
    }

    // --- Valid word skip ---

    @Test
    fun `valid dictionary word is not corrected`() {
        fakeDictProvider.validWords = setOf("hello")
        val result = engine.getCorrection("hello", emptySet())
        assertEquals(AutocorrectResult.None, result)
    }

    // --- Fuzzy matching ---

    @Test
    fun `fuzzy match within edit distance 2 gets suggestion`() {
        engine.aggressiveness = AutocorrectEngine.Aggressiveness.MILD
        fakeDictProvider.fuzzyCorrections = listOf("the")
        val result = engine.getCorrection("teh", emptySet())
        assertTrue(result is AutocorrectResult.Suggestion)
        assertEquals("the", (result as AutocorrectResult.Suggestion).correction)
    }

    @Test
    fun `no fuzzy matches returns None`() {
        fakeDictProvider.fuzzyCorrections = emptyList()
        val result = engine.getCorrection("abcde", emptySet())
        assertEquals(AutocorrectResult.None, result)
    }

    // --- autoApply flag ---

    @Test
    fun `MILD sets autoApply to false`() {
        engine.aggressiveness = AutocorrectEngine.Aggressiveness.MILD
        fakeDictProvider.fuzzyCorrections = listOf("the")
        val result = engine.getCorrection("teh", emptySet()) as AutocorrectResult.Suggestion
        assertFalse(result.autoApply)
    }

    @Test
    fun `AGGRESSIVE sets autoApply to true`() {
        engine.aggressiveness = AutocorrectEngine.Aggressiveness.AGGRESSIVE
        fakeDictProvider.fuzzyCorrections = listOf("the")
        val result = engine.getCorrection("teh", emptySet()) as AutocorrectResult.Suggestion
        assertTrue(result.autoApply)
    }

    // --- No corrections ---

    @Test
    fun `no fuzzy corrections returns None`() {
        fakeDictProvider.fuzzyCorrections = emptyList()
        val result = engine.getCorrection("xyzzy", emptySet())
        assertEquals(AutocorrectResult.None, result)
    }
}

/** Test double that returns canned suggestions without needing Suggest/JNI. */
class FakeDictionaryProvider : DictionaryProvider(null) {
    var suggestions: List<String> = emptyList()
    var fuzzyCorrections: List<String> = emptyList()
    var validWords: Set<String> = emptySet()

    override fun getSuggestions(word: String, maxResults: Int): List<String> =
        suggestions.take(maxResults)

    override fun getFuzzyCorrections(word: String, maxResults: Int): List<String> =
        fuzzyCorrections.take(maxResults)

    override fun isValidWord(word: String): Boolean = word in validWords
}
