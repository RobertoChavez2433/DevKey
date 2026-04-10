package dev.devkey.keyboard.feature.prediction

import dev.devkey.keyboard.testutil.FakeLearnedWordDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LearningEngineTest {

    private lateinit var dao: FakeLearnedWordDao
    private lateinit var engine: LearningEngine

    @Before
    fun setUp() {
        dao = FakeLearnedWordDao()
        engine = LearningEngine(dao)
    }

    // --- initialize ---

    @Test
    fun `initialize loads words from DAO into cache`() = runBlocking {
        dao.insert(
            dev.devkey.keyboard.data.db.entity.LearnedWordEntity(
                word = "hello", frequency = 5, contextApp = null
            )
        )
        engine.initialize()
        assertTrue(engine.isLearnedWord("hello"))
    }

    @Test
    fun `initialize is idempotent`() = runBlocking {
        engine.initialize()
        dao.insert(
            dev.devkey.keyboard.data.db.entity.LearnedWordEntity(
                word = "late", frequency = 1, contextApp = null
            )
        )
        engine.initialize() // second call should be no-op
        assertFalse(engine.isLearnedWord("late"))
    }

    // --- onWordCommitted ---

    @Test
    fun `onWordCommitted adds word to cache`() = runBlocking {
        engine.initialize()
        engine.onWordCommitted("kotlin", isCommand = false, contextApp = null)
        assertTrue(engine.isLearnedWord("kotlin"))
    }

    @Test
    fun `onWordCommitted increments frequency on repeat`() = runBlocking {
        engine.initialize()
        engine.onWordCommitted("test", isCommand = false, contextApp = null)
        engine.onWordCommitted("test", isCommand = false, contextApp = null)

        val words = dao.getAllWordsList()
        val testWord = words.find { it.word == "test" }
        assertEquals(2, testWord?.frequency)
    }

    @Test
    fun `onWordCommitted ignores single-character words`() = runBlocking {
        engine.initialize()
        engine.onWordCommitted("a", isCommand = false, contextApp = null)
        assertFalse(engine.isLearnedWord("a"))
    }

    // --- getLearnedWords ---

    @Test
    fun `getLearnedWords returns snapshot of cache`() = runBlocking {
        engine.initialize()
        engine.onWordCommitted("alpha", isCommand = false, contextApp = null)
        engine.onWordCommitted("beta", isCommand = false, contextApp = null)

        val words = engine.getLearnedWords()
        assertTrue("alpha" in words)
        assertTrue("beta" in words)
        assertEquals(2, words.size)
    }

    // --- isLearnedWord ---

    @Test
    fun `isLearnedWord returns false for unknown word`() {
        assertFalse(engine.isLearnedWord("unknown"))
    }
}
