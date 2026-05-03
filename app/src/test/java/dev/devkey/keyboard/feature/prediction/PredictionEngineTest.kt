package dev.devkey.keyboard.feature.prediction

import dev.devkey.keyboard.feature.command.InputMode
import dev.devkey.keyboard.feature.smarttext.AnySoftKeyboardSmartTextEngine
import dev.devkey.keyboard.feature.smarttext.SmartTextCorrectionLevel
import dev.devkey.keyboard.testutil.FakeLearnedWordDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PredictionEngineTest {

    private lateinit var fakeDictProvider: FakeDictionaryProvider
    private lateinit var learningEngine: LearningEngine
    private lateinit var predictionEngine: PredictionEngine
    private var correctionLevel = SmartTextCorrectionLevel.MILD

    @Before
    fun setUp() {
        fakeDictProvider = FakeDictionaryProvider()
        learningEngine = LearningEngine(FakeLearnedWordDao())
        correctionLevel = SmartTextCorrectionLevel.MILD
        predictionEngine = PredictionEngine(
            AnySoftKeyboardSmartTextEngine(
                fakeDictProvider,
                learningEngine,
                correctionLevel = { correctionLevel },
            )
        )
    }

    @Test
    fun `empty word returns empty predictions`() = runBlocking {
        val results = predictionEngine.predict("")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `autocorrect suggestion appears first`() = runBlocking {
        fakeDictProvider.suggestions = listOf("the", "them", "then")
        fakeDictProvider.fuzzyCorrections = listOf("the")

        learningEngine.initialize()
        val results = predictionEngine.predict("teh")

        assertTrue(results.isNotEmpty())
        assertTrue(results[0].isAutocorrect)
        assertEquals("the", results[0].word)
    }

    @Test
    fun `ordinary learned word does not suppress autocorrect suggestion`() = runBlocking {
        fakeDictProvider.fuzzyCorrections = listOf("the")

        learningEngine.initialize()
        learningEngine.onWordCommitted("teh", isCommand = false, contextApp = null)
        val results = predictionEngine.predict("teh")

        assertTrue(results.first().isAutocorrect)
        assertEquals("the", results.first().word)
    }

    @Test
    fun `custom word suppresses autocorrect suggestion`() = runBlocking {
        fakeDictProvider.fuzzyCorrections = listOf("the")

        learningEngine.initialize()
        learningEngine.addCustomWord("teh")
        val results = predictionEngine.predict("teh")

        assertTrue(results.none { it.isAutocorrect })
    }

    @Test
    fun `dictionary suggestions follow autocorrect`() = runBlocking {
        fakeDictProvider.suggestions = listOf("the", "them", "then")
        fakeDictProvider.fuzzyCorrections = listOf("the")

        learningEngine.initialize()
        val results = predictionEngine.predict("teh")

        assertTrue(results.size > 1)
        assertFalse(results[1].isAutocorrect)
    }

    @Test
    fun `no duplicate between autocorrect and dictionary suggestions`() = runBlocking {
        fakeDictProvider.suggestions = listOf("the", "them")

        learningEngine.initialize()
        val results = predictionEngine.predict("teh")
        val words = results.map { it.word }
        assertEquals(words.distinct(), words)
    }

    @Test
    fun `max 3 results returned`() = runBlocking {
        correctionLevel = SmartTextCorrectionLevel.OFF
        fakeDictProvider.suggestions = listOf("hello", "help", "held", "helm")

        learningEngine.initialize()
        val results = predictionEngine.predict("hel")
        assertTrue(results.size <= 3)
    }

    @Test
    fun `command mode returns command suggestions`() = runBlocking {
        predictionEngine.inputMode = InputMode.COMMAND
        learningEngine.initialize()
        learningEngine.onWordCommitted("kubectl", isCommand = true, contextApp = null)

        val results = predictionEngine.predict("kube")
        assertTrue(results.any { it.word == "kubectl" })
    }

    @Test
    fun `OFF aggressiveness still returns dictionary suggestions`() = runBlocking {
        correctionLevel = SmartTextCorrectionLevel.OFF
        fakeDictProvider.suggestions = listOf("hello", "help")

        learningEngine.initialize()
        val results = predictionEngine.predict("hel")

        assertTrue(results.isNotEmpty())
        assertFalse(results.any { it.isAutocorrect })
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
