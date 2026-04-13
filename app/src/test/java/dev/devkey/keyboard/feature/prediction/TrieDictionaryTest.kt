package dev.devkey.keyboard.feature.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TrieDictionaryTest {

    private lateinit var dict: TrieDictionary

    @Before
    fun setUp() {
        dict = TrieDictionary()
    }

    // -------------------------------------------------------------------------
    // editDistance
    // -------------------------------------------------------------------------

    @Test
    fun `editDistance identical strings returns 0`() {
        assertEquals(0, dict.editDistance("hello", "hello"))
    }

    @Test
    fun `editDistance single insertion returns 1`() {
        assertEquals(1, dict.editDistance("hell", "hello"))
    }

    @Test
    fun `editDistance single deletion returns 1`() {
        assertEquals(1, dict.editDistance("hello", "hell"))
    }

    @Test
    fun `editDistance single substitution returns 1`() {
        assertEquals(1, dict.editDistance("hello", "hallo"))
    }

    @Test
    fun `editDistance both empty returns 0`() {
        assertEquals(0, dict.editDistance("", ""))
    }

    @Test
    fun `editDistance one empty returns length of other`() {
        assertEquals(5, dict.editDistance("", "hello"))
        assertEquals(3, dict.editDistance("abc", ""))
    }

    // -------------------------------------------------------------------------
    // getSuggestions
    // -------------------------------------------------------------------------

    @Test
    fun `getSuggestions cached prefix returns results`() {
        val loaded = loadDictionaryWithWords(
            "the" to 1000,
            "them" to 900,
            "then" to 800,
            "there" to 700
        )
        val results = loaded.getSuggestions("th", maxResults = 3)
        assertTrue("Expected suggestions for short prefix", results.isNotEmpty())
        assertTrue("Expected at most 3 results", results.size <= 3)
    }

    @Test
    fun `getSuggestions DFS fallback for long prefix`() {
        val loaded = loadDictionaryWithWords(
            "international" to 500,
            "internationalize" to 300,
            "internationalism" to 200
        )
        // Prefix longer than 4 chars forces DFS fallback (no cached topCompletions)
        val results = loaded.getSuggestions("internation", maxResults = 3)
        assertTrue("Expected DFS fallback to find results", results.isNotEmpty())
    }

    @Test
    fun `getSuggestions filters exact match from results`() {
        val loaded = loadDictionaryWithWords(
            "hello" to 1000,
            "help" to 900,
            "held" to 800
        )
        val results = loaded.getSuggestions("hello", maxResults = 5)
        assertTrue("Exact match should be filtered", results.none { it.first == "hello" })
    }

    // -------------------------------------------------------------------------
    // getFuzzyMatches
    // -------------------------------------------------------------------------

    @Test
    fun `getFuzzyMatches respects maxDistance`() {
        val loaded = loadDictionaryWithWords(
            "the" to 1000,
            "them" to 900,
            "xyz" to 500
        )
        val results = loaded.getFuzzyMatches("teh", maxDistance = 1, maxResults = 10)
        for ((word, _) in results) {
            assertTrue(
                "Result should be within edit distance 1",
                loaded.editDistance("teh", word) <= 1
            )
        }
    }

    @Test
    fun `getFuzzyMatches early exit at maxResults times 2`() {
        val words = (1..100).map { "word${it}" to (1000 - it) }
        val loaded = loadDictionaryWithWords(*words.toTypedArray())
        val results = loaded.getFuzzyMatches("word1", maxDistance = 2, maxResults = 3)
        assertTrue("Expected at most 3 results", results.size <= 3)
    }

    @Test
    fun `getFuzzyMatches returns empty for short input`() {
        val loaded = loadDictionaryWithWords("ab" to 1000, "cd" to 900)
        val results = loaded.getFuzzyMatches("x", maxDistance = 2, maxResults = 3)
        assertTrue("Input < 2 chars should return empty", results.isEmpty())
    }

    // -------------------------------------------------------------------------
    // isValidWord
    // -------------------------------------------------------------------------

    @Test
    fun `isValidWord case insensitive`() {
        val loaded = loadDictionaryWithWords("hello" to 1000)
        assertTrue(loaded.isValidWord("hello"))
        assertTrue(loaded.isValidWord("Hello"))
        assertTrue(loaded.isValidWord("HELLO"))
    }

    @Test
    fun `isValidWord returns false for unknown word`() {
        val loaded = loadDictionaryWithWords("hello" to 1000)
        assertFalse(loaded.isValidWord("xyz"))
    }

    // -------------------------------------------------------------------------
    // load (TSV format via gzipped stream)
    // -------------------------------------------------------------------------

    @Test
    fun `load parses gzipped TSV format`() {
        val loaded = loadDictionaryWithWords("hello" to 1000, "world" to 500)

        assertEquals(2, loaded.size)
        assertTrue(loaded.isValidWord("hello"))
        assertTrue(loaded.isValidWord("world"))
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a TrieDictionary by creating a gzipped TSV stream and loading it
     * through the real [TrieDictionary.load] path, using Robolectric's
     * application context with a stubbed raw resource.
     */
    private fun loadDictionaryWithWords(vararg words: Pair<String, Int>): TrieDictionary {
        val tsv = words.joinToString("\n") { "${it.first}\t${it.second}" } + "\n"
        val gzipped = gzipBytes(tsv)
        val context = StubRawResourceContext(gzipped)
        val result = TrieDictionary()
        result.load(context, 0)
        return result
    }

    private fun gzipBytes(content: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(content.toByteArray(Charsets.UTF_8)) }
        return bos.toByteArray()
    }

    /**
     * Wraps Robolectric's application context and overrides
     * [getResources] to return a gzipped byte array from
     * [openRawResource], which is the only method [TrieDictionary.load] calls.
     */
    private class StubRawResourceContext(
        private val rawData: ByteArray
    ) : android.content.ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getResources(): android.content.res.Resources {
            val real = super.getResources()
            return object : android.content.res.Resources(
                real.assets, real.displayMetrics, real.configuration
            ) {
                override fun openRawResource(id: Int): java.io.InputStream {
                    return ByteArrayInputStream(rawData)
                }
            }
        }
    }
}
