package dev.devkey.keyboard.feature.smarttext

import dev.devkey.keyboard.feature.prediction.DictionaryProvider
import dev.devkey.keyboard.feature.prediction.LearningEngine
import dev.devkey.keyboard.testutil.FakeLearnedWordDao
import dev.devkey.keyboard.testutil.loadAnySoftKeyboardDictionaryWithWords
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AnySoftKeyboardSmartTextEngineTest {

    private lateinit var learningEngine: LearningEngine
    private var correctionLevel = SmartTextCorrectionLevel.MILD

    @Before
    fun setUp() = runBlocking {
        learningEngine = LearningEngine(FakeLearnedWordDao())
        learningEngine.initialize()
        correctionLevel = SmartTextCorrectionLevel.MILD
    }

    @Test
    fun `common typo corrections cross smart text boundary`() {
        val engine = createEngine()
        val cases = listOf(
            "helo" to "hello",
            "hell" to "hello",
            "helloo" to "hello",
            "teh" to "the",
        )

        for ((typed, expected) in cases) {
            val correction = engine.correction(
                SmartTextCorrectionRequest(
                    typedWord = typed,
                    customWords = emptySet(),
                )
            )

            assertNotNull("Expected correction for $typed", correction)
            assertEquals(expected, correction!!.word)
            assertFalse(correction.autoApply)
        }
    }

    @Test
    fun `aggressive level auto applies corrections`() {
        correctionLevel = SmartTextCorrectionLevel.AGGRESSIVE
        val engine = createEngine()

        val correction = engine.correction(
            SmartTextCorrectionRequest(
                typedWord = "teh",
                customWords = emptySet(),
            )
        )

        assertNotNull(correction)
        assertEquals("the", correction!!.word)
        assertEquals(true, correction.autoApply)
    }

    @Test
    fun `off level suppresses corrections but keeps completions`() = runBlocking {
        correctionLevel = SmartTextCorrectionLevel.OFF
        val engine = createEngine()

        val correction = engine.correction(
            SmartTextCorrectionRequest(
                typedWord = "teh",
                customWords = emptySet(),
            )
        )
        val suggestions = engine.suggestions(
            SmartTextSuggestionRequest(
                currentWord = "hel",
                maxResults = 3,
            )
        )

        assertNull(correction)
        assertEquals(
            listOf(
                SmartTextSuggestion("hello", SmartTextSuggestionKind.COMPLETION),
                SmartTextSuggestion("help", SmartTextSuggestionKind.COMPLETION),
            ),
            suggestions,
        )
    }

    @Test
    fun `custom words suppress corrections across smart text boundary`() {
        val engine = createEngine()

        val correction = engine.correction(
            SmartTextCorrectionRequest(
                typedWord = "teh",
                customWords = setOf("teh"),
            )
        )

        assertNull(correction)
    }

    @Test
    fun `credential and structured inputs suppress corrections`() {
        val engine = createEngine()
        val excludedKinds = listOf(
            SmartTextInputKind.PASSWORD,
            SmartTextInputKind.URL,
            SmartTextInputKind.EMAIL,
            SmartTextInputKind.CODE_LIKE,
        )

        for (kind in excludedKinds) {
            val correction = engine.correction(
                SmartTextCorrectionRequest(
                    typedWord = "teh",
                    customWords = emptySet(),
                    inputKind = kind,
                )
            )
            assertNull("Expected no correction for $kind", correction)
        }

        assertNull(
            engine.correction(
                SmartTextCorrectionRequest(
                    typedWord = "abc1",
                    customWords = emptySet(),
                )
            )
        )
    }

    @Test
    fun `command mode returns learned command suggestions without autocorrect`() = runBlocking {
        val engine = createEngine()
        learningEngine.onWordCommitted("deploy", isCommand = true, contextApp = null)

        val correction = engine.correction(
            SmartTextCorrectionRequest(
                typedWord = "deply",
                customWords = emptySet(),
                inputKind = SmartTextInputKind.COMMAND,
            )
        )
        val suggestions = engine.suggestions(
            SmartTextSuggestionRequest(
                currentWord = "de",
                inputKind = SmartTextInputKind.COMMAND,
                maxResults = 3,
            )
        )

        assertNull(correction)
        assertEquals(
            listOf(SmartTextSuggestion("deploy", SmartTextSuggestionKind.COMMAND)),
            suggestions,
        )
    }

    @Test
    fun `candidate suggestions use non suspend donor path`() {
        correctionLevel = SmartTextCorrectionLevel.OFF
        val engine = createEngine()

        val suggestions = engine.candidateSuggestions(
            SmartTextSuggestionRequest(
                currentWord = "hel",
                maxResults = 3,
            )
        )

        assertEquals(
            listOf(
                SmartTextSuggestion("hello", SmartTextSuggestionKind.COMPLETION),
                SmartTextSuggestion("help", SmartTextSuggestionKind.COMPLETION),
            ),
            suggestions,
        )
    }

    @Test
    fun `next word reports explicit temporary boundary`() {
        val engine = createEngine()

        val result = engine.nextWordSuggestions(
            SmartTextNextWordRequest(
                previousWord = "hello",
                maxResults = 3,
            )
        )

        assertEquals(emptyList<String>(), result.suggestions)
        assertEquals(SmartTextNextWordSource.UNAVAILABLE, result.source)
    }

    private fun createEngine(): AnySoftKeyboardSmartTextEngine {
        val dictionaryProvider = DictionaryProvider(null)
        dictionaryProvider.donorDictionary = loadAnySoftKeyboardDictionaryWithWords(
            "hello" to 3000,
            "help" to 1200,
            "the" to 4000,
            "test" to 900,
        )
        return AnySoftKeyboardSmartTextEngine(
            dictionaryProvider,
            learningEngine,
            correctionLevel = { correctionLevel },
        )
    }
}
