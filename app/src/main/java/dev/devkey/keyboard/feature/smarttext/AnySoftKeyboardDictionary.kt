package dev.devkey.keyboard.feature.smarttext

import android.content.Context
import androidx.annotation.RawRes
import androidx.annotation.VisibleForTesting
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.min

/**
 * Donor dictionary adapter for AnySoftKeyboard language-pack wordlists.
 *
 * The English pack ships as `XX_wordlist.combined.gz` lines like:
 * `word=the,f=222,flags=,originalFreq=222`.
 */
class AnySoftKeyboardDictionary {
    data class Metadata(
        val sourceName: String,
        val sourceArtifact: String,
        val locale: String?,
        val description: String?,
        val version: Int?,
        val date: Long?,
    )

    data class LoadStats(
        val metadata: Metadata,
        val wordCount: Int,
        val sourceBytesRead: Long,
    )

    private class TrieNode {
        val children = HashMap<Char, TrieNode>(4)
        var frequency: Int = 0
        var isTerminal: Boolean = false
        var topCompletions: Array<Completion>? = null
    }

    private data class Completion(val word: String, val frequency: Int)

    private var root = TrieNode()
    private var wordCount = 0
    private var topWords: Array<Completion> = emptyArray()
    private var loadedMetadata = Metadata(
        sourceName = SOURCE_NAME,
        sourceArtifact = DEFAULT_ARTIFACT,
        locale = null,
        description = null,
        version = null,
        date = null,
    )

    val size: Int get() = wordCount
    val metadata: Metadata get() = loadedMetadata

    fun load(
        context: Context,
        @RawRes rawResId: Int,
        sourceArtifact: String = DEFAULT_ARTIFACT,
    ): LoadStats = context.resources.openRawResource(rawResId).use {
        load(it, sourceArtifact)
    }

    @VisibleForTesting
    internal fun load(
        inputStream: InputStream,
        sourceArtifact: String = DEFAULT_ARTIFACT,
    ): LoadStats {
        val countingStream = CountingInputStream(inputStream)
        val words = LinkedHashMap<String, Int>()
        var locale: String? = null
        var description: String? = null
        var version: Int? = null
        var date: Long? = null

        BufferedReader(
            InputStreamReader(GZIPInputStream(countingStream), Charsets.UTF_8)
        ).use { reader ->
            reader.forEachLine { rawLine ->
                val line = rawLine.trim()
                when {
                    line.startsWith("dictionary=") -> {
                        val attrs = parseAttributes(line)
                        locale = attrs["locale"] ?: locale
                        description = attrs["description"] ?: description
                        version = attrs["version"]?.toIntOrNull() ?: version
                        date = attrs["date"]?.toLongOrNull() ?: date
                    }
                    line.startsWith("word=") -> {
                        val attrs = parseAttributes(line)
                        val word = attrs["word"]?.let(::normalizeWord) ?: return@forEachLine
                        val frequency = attrs["f"]?.toIntOrNull()
                            ?: attrs["originalFreq"]?.toIntOrNull()
                            ?: return@forEachLine
                        if (frequency > 0) {
                            words[word] = maxOf(words[word] ?: 0, frequency)
                        }
                    }
                }
            }
        }

        rebuild(words)
        loadedMetadata = Metadata(
            sourceName = SOURCE_NAME,
            sourceArtifact = sourceArtifact,
            locale = locale,
            description = description,
            version = version,
            date = date,
        )
        return LoadStats(
            metadata = loadedMetadata,
            wordCount = wordCount,
            sourceBytesRead = countingStream.bytesRead,
        )
    }

    fun isValidWord(word: String): Boolean {
        val normalized = normalizeLookup(word) ?: return false
        val node = findNodeExact(normalized) ?: return false
        return node.isTerminal
    }

    fun getSuggestions(prefix: String, maxResults: Int = 5): List<Pair<String, Int>> {
        val normalized = normalizeLookup(prefix) ?: return emptyList()
        val prefixNode = findNodeExact(normalized) ?: return emptyList()
        val cached = prefixNode.topCompletions
        if (cached != null) {
            return cached
                .filter { it.word != normalized }
                .take(maxResults)
                .map { it.word to it.frequency }
        }

        val results = mutableListOf<Pair<String, Int>>()
        val current = StringBuilder(normalized)
        collectWords(prefixNode, current, results, maxResults * 3, maxDepth = 12)
        return results
            .sortedByDescending { it.second }
            .filter { it.first != normalized }
            .take(maxResults)
    }

    fun getFuzzyCorrections(
        typed: String,
        maxDistance: Int = 2,
        maxResults: Int = 3,
    ): List<Pair<String, Int>> {
        val normalized = normalizeLookup(typed) ?: return emptyList()
        if (normalized.length < 2) return emptyList()

        val matches = mutableListOf<Pair<String, Int>>()
        var index = 0
        val matchLimit = maxResults * FUZZY_EARLY_EXIT_MULTIPLIER
        while (index < topWords.size && matches.size < matchLimit) {
            val candidate = topWords[index]
            if (isPossibleFuzzyCandidate(normalized, candidate.word, maxDistance)) {
                val distance = editDistance(normalized, candidate.word)
                if (distance in 1..maxDistance) {
                    matches.add(candidate.word to candidate.frequency)
                }
            }
            index++
        }
        return matches.sortedByDescending { it.second }.take(maxResults)
    }

    @VisibleForTesting
    internal fun editDistance(a: String, b: String): Int {
        val rowCount = b.length + 1
        var previous = IntArray(rowCount) { it }
        var current = IntArray(rowCount)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(
                    min(previous[j] + 1, current[j - 1] + 1),
                    previous[j - 1] + cost,
                )
            }
            val nextPrevious = current
            current = previous
            previous = nextPrevious
        }
        return previous[b.length]
    }

    private fun rebuild(words: Map<String, Int>) {
        root = TrieNode()
        wordCount = 0

        val sortedWords = words.entries
            .sortedByDescending { it.value }
            .map { Completion(it.key, it.value) }

        for (entry in sortedWords) {
            insert(entry.word, entry.frequency)
        }
        topWords = sortedWords.take(MAX_FUZZY_WORDS).toTypedArray()
        precomputeTopCompletions(sortedWords)
    }

    private fun isPossibleFuzzyCandidate(
        typed: String,
        candidate: String,
        maxDistance: Int,
    ): Boolean =
        candidate != typed &&
            abs(candidate.length - typed.length) <= maxDistance

    private fun insert(word: String, frequency: Int) {
        var node = root
        for (ch in word) {
            node = node.children.getOrPut(ch) { TrieNode() }
        }
        if (!node.isTerminal) wordCount++
        node.isTerminal = true
        node.frequency = frequency
    }

    private fun precomputeTopCompletions(sortedWords: List<Completion>) {
        val prefixMap = HashMap<String, MutableList<Completion>>(16_000)
        for (entry in sortedWords) {
            val maxPrefixLength = min(entry.word.length, MAX_PRECOMPUTED_PREFIX_LENGTH)
            for (length in 1..maxPrefixLength) {
                val prefix = entry.word.substring(0, length)
                val completions = prefixMap.getOrPut(prefix) { mutableListOf() }
                if (completions.size < TOP_COMPLETIONS_PER_PREFIX) {
                    completions.add(entry)
                }
            }
        }
        for ((prefix, completions) in prefixMap) {
            findNodeExact(prefix)?.topCompletions = completions.toTypedArray()
        }
    }

    private fun findNodeExact(word: String): TrieNode? {
        var node = root
        for (ch in word) {
            node = node.children[ch] ?: return null
        }
        return node
    }

    private fun collectWords(
        node: TrieNode,
        prefix: StringBuilder,
        results: MutableList<Pair<String, Int>>,
        limit: Int,
        maxDepth: Int,
    ) {
        if (results.size >= limit || maxDepth <= 0) return
        if (node.isTerminal) results.add(prefix.toString() to node.frequency)
        for ((ch, child) in node.children) {
            prefix.append(ch)
            collectWords(child, prefix, results, limit, maxDepth - 1)
            prefix.deleteCharAt(prefix.length - 1)
            if (results.size >= limit) return
        }
    }

    private class CountingInputStream(inputStream: InputStream) : FilterInputStream(inputStream) {
        var bytesRead: Long = 0
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) bytesRead++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) bytesRead += count
            return count
        }
    }

    companion object {
        const val SOURCE_NAME = "anysoftkeyboard_language_pack"
        const val DEFAULT_ARTIFACT = "ask_english_wordlist_combined"

        private const val MAX_WORD_LENGTH = 32
        private const val MAX_FUZZY_WORDS = 20_000
        private const val MAX_PRECOMPUTED_PREFIX_LENGTH = 5
        private const val TOP_COMPLETIONS_PER_PREFIX = 8
        private const val FUZZY_EARLY_EXIT_MULTIPLIER = 3

        private fun parseAttributes(line: String): Map<String, String> =
            line.split(',')
                .mapNotNull { part ->
                    val separator = part.indexOf('=')
                    if (separator <= 0) {
                        null
                    } else {
                        part.substring(0, separator).trim() to
                            part.substring(separator + 1).trim()
                    }
                }
                .toMap()

        private fun normalizeLookup(word: String): String? =
            normalizeWord(word.trim())

        private fun normalizeWord(word: String): String? {
            val normalized = word.lowercase(Locale.ROOT)
            if (normalized.length !in 1..MAX_WORD_LENGTH) return null
            if (!normalized.any { it.isLetter() }) return null
            if (!normalized.all { it.isLetter() || it == '\'' }) return null
            return normalized
        }
    }
}
