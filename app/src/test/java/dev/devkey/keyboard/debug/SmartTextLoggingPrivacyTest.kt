package dev.devkey.keyboard.debug

import dev.devkey.keyboard.feature.prediction.DictionaryProvider
import dev.devkey.keyboard.feature.prediction.LearningEngine
import dev.devkey.keyboard.feature.smarttext.AnySoftKeyboardSmartTextEngine
import dev.devkey.keyboard.feature.smarttext.SmartTextCorrectionRequest
import dev.devkey.keyboard.feature.smarttext.SmartTextPunctuationRequest
import dev.devkey.keyboard.feature.smarttext.SmartTextPunctuationTransform
import dev.devkey.keyboard.feature.smarttext.SmartTextSuggestionRequest
import dev.devkey.keyboard.testutil.FakeLearnedWordDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SmartTextLoggingPrivacyTest {

    @Before
    fun setUp() {
        DevKeyLogger.disableServer()
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        DevKeyLogger.disableServer()
        ShadowLog.clear()
    }

    @Test
    fun `smart text and dictionary logs omit sensitive content`() = runBlocking {
        val learningEngine = LearningEngine(FakeLearnedWordDao())
        learningEngine.initialize()
        val engine = AnySoftKeyboardSmartTextEngine(
            DictionaryProvider(),
            learningEngine,
            correctionLevel = { dev.devkey.keyboard.feature.smarttext.SmartTextCorrectionLevel.MILD },
        )
        val secrets = listOf(
            "TypedPasswordSentinel",
            "CredentialSentinel",
            "ClipboardSentinel",
            "TranscriptSentinel",
            "DictionarySentinel",
        )

        engine.correction(
            SmartTextCorrectionRequest(
                typedWord = secrets[0],
                customWords = setOf(secrets[1]),
            )
        )
        engine.candidateSuggestions(
            SmartTextSuggestionRequest(currentWord = secrets[2])
        )
        engine.punctuation(
            SmartTextPunctuationRequest(
                transform = SmartTextPunctuationTransform.DOUBLE_SPACE_PERIOD,
                contextBeforeCursor = "${secrets[3]}  ",
            )
        )
        learningEngine.addCustomWord(secrets[4])

        val logs = ShadowLog.getLogs().joinToString("\n") { "${it.tag}: ${it.msg}" }

        for (secret in secrets) {
            assertFalse("Log output leaked $secret", logs.contains(secret))
        }
        assertTrue(logs.contains("word_length"))
        assertTrue(logs.contains("context_length"))
    }
}
