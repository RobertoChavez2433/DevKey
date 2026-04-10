package dev.devkey.keyboard.feature.prediction

import dev.devkey.keyboard.feature.command.InputMode
import dev.devkey.keyboard.testutil.FakeLearnedWordDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PredictionEngineTest {

    private lateinit var fakeDictProvider: FakeDictionaryProvider
    private lateinit var autocorrectEngine: AutocorrectEngine
    private lateinit var learningEngine: LearningEngine
    private lateinit var predictionEngine: PredictionEngine

    @Before
    fun setUp() {
        fakeDictProvider = FakeDictionaryProvider()
        autocorrectEngine = AutocorrectEngine(fakeDictProvider)
        learningEngine = LearningEngine(FakeLearnedWordDao())
        predictionEngine = PredictionEngine(fakeDictProvider, autocorrectEngine, learningEngine)
    }

    @Test
    fun `empty word returns empty predictions`() = runBlocking {
        val results = predictionEngine.predict("")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `autocorrect suggestion appears first`() = runBlocking {
        autocorrectEngine.aggressiveness = AutocorrectEngine.Aggressiveness.MILD
        fakeDictProvider.suggestions = listOf("the", "them", "then")
        fakeDictProvider.fuzzyCorrections = listOf("the")

        learningEngine.initialize()
        val results = predictionEngine.predict("teh")

        assertTrue(results.isNotEmpty())
        assertTrue(results[0].isAutocorrect)
        assertEquals("the", results[0].word)
    }

    @Test
    fun `dictionary suggestions follow autocorrect`() = runBlocking {
        autocorrectEngine.aggressiveness = AutocorrectEngine.Aggressiveness.MILD
        fakeDictProvider.suggestions = listOf("the", "them", "then")
        fakeDictProvider.fuzzyCorrections = listOf("the")

        learningEngine.initialize()
        val results = predictionEngine.predict("teh")

        assertTrue(results.size > 1)
        assertFalse(results[1].isAutocorrect)
    }

    @Test
    fun `no duplicate between autocorrect and dictionary suggestions`() = runBlocking {
        autocorrectEngine.aggressiveness = AutocorrectEngine.Aggressiveness.MILD
        fakeDictProvider.suggestions = listOf("the", "them")

        learningEngine.initialize()
        val results = predictionEngine.predict("teh")
        val words = results.map { it.word }
        assertEquals(words.distinct(), words)
    }

    @Test
    fun `max 3 results returned`() = runBlocking {
        autocorrectEngine.aggressiveness = AutocorrectEngine.Aggressiveness.OFF
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
        autocorrectEngine.aggressiveness = AutocorrectEngine.Aggressiveness.OFF
        fakeDictProvider.suggestions = listOf("hello", "help")

        learningEngine.initialize()
        val results = predictionEngine.predict("hel")

        assertTrue(results.isNotEmpty())
        assertFalse(results.any { it.isAutocorrect })
    }
}
