package dev.devkey.keyboard.dictionary.base

import dev.devkey.keyboard.suggestion.word.WordComposer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BinaryDictionaryTest {

    @Test
    fun `empty native dictionary ignores bigram lookup and closes idempotently`() {
        val dictionary = BinaryDictionary(RuntimeEnvironment.getApplication(), null as IntArray?, 0)
        val composer = WordComposer()

        dictionary.getBigrams(
            composer,
            "the",
            Dictionary.WordCallback { _, _, _, _, _, _ -> true },
            null
        )
        dictionary.close()
        dictionary.close()
    }
}
