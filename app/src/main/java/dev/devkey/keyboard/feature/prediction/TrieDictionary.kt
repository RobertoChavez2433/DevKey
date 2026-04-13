package dev.devkey.keyboard.feature.prediction

import android.content.Context
import androidx.annotation.VisibleForTesting
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlin.math.min

/**
 * Pure Kotlin dictionary backed by a prefix trie for fast suggestion lookup
 * and a frequency-sorted word list for fuzzy autocorrect matching.
 *
 * Loads a gzipped word-frequency file from resources at startup. Each line
 * is "word\tfrequency\n".
 *
 * Thread-safety: built once during [load], read-only after. Concurrent reads safe.
 */
class TrieDictionary {

    private class TrieNode {
        val children = HashMap<Char, TrieNode>(4)
        var frequency: Int = 0
        var isTerminal: Boolean = false
        var topCompletions: Array<Completion>? = null
    }

    private class Completion(val word: String, val frequency: Int)

    private val root = TrieNode()
    private var wordCount = 0

    /**
     * Top ~10,000 most frequent words for fuzzy autocorrect matching.
     * Checking edit distance against 10k words is fast (~1-2ms).
     */
    private var topWords: Array<Pair<String, Int>> = emptyArray()

    val size: Int get() = wordCount

    fun load(context: Context, rawResId: Int) {
        val allWords = mutableListOf<Pair<String, Int>>()
        val stream = context.resources.openRawResource(rawResId)
        val reader = BufferedReader(InputStreamReader(GZIPInputStream(stream), Charsets.UTF_8))
        reader.use { br ->
            br.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) {
                    val word = line.substring(0, tab)
                    val freq = line.substring(tab + 1).toIntOrNull() ?: 0
                    if (word.isNotEmpty() && freq > 0) {
                        val lower = word.lowercase()
                        insert(lower, freq)
                        allWords.add(lower to freq)
                    }
                }
            }
        }

        // Sort by frequency descending
        allWords.sortByDescending { it.second }

        // Keep top 10k words for fuzzy matching
        topWords = allWords.take(10_000).toTypedArray()

        // Pre-compute top completions at prefix nodes (depth 1-4)
        precomputeTopCompletions(allWords)
    }

    private fun insert(word: String, frequency: Int) {
        var node = root
        for (ch in word) {
            node = node.children.getOrPut(ch) { TrieNode() }
        }
        if (!node.isTerminal) wordCount++
        node.isTerminal = true
        node.frequency = frequency
    }

    private fun precomputeTopCompletions(allWords: List<Pair<String, Int>>) {
        val prefixMap = HashMap<String, MutableList<Completion>>(10000)
        for ((word, freq) in allWords) {
            val maxPrefixLen = minOf(word.length, 4)
            for (len in 1..maxPrefixLen) {
                val prefix = word.substring(0, len)
                val list = prefixMap.getOrPut(prefix) { mutableListOf() }
                if (list.size < 8) {
                    list.add(Completion(word, freq))
                }
            }
        }
        for ((prefix, completions) in prefixMap) {
            val node = findNodeExact(prefix)
            node?.topCompletions = completions.toTypedArray()
        }
    }

    private fun findNodeExact(word: String): TrieNode? {
        var node = root
        for (ch in word) {
            node = node.children[ch] ?: return null
        }
        return node
    }

    fun isValidWord(word: String): Boolean {
        val node = findNodeExact(word.lowercase()) ?: return false
        return node.isTerminal
    }

    /**
     * Prefix-based completions for the suggestion bar.
     */
    fun getSuggestions(prefix: String, maxResults: Int = 5): List<Pair<String, Int>> {
        if (prefix.isEmpty()) return emptyList()
        val lowerPrefix = prefix.lowercase()
        val prefixNode = findNodeExact(lowerPrefix) ?: return emptyList()

        val cached = prefixNode.topCompletions
        if (cached != null) {
            return cached
                .filter { it.word != lowerPrefix }
                .take(maxResults)
                .map { it.word to it.frequency }
        }

        val results = mutableListOf<Pair<String, Int>>()
        val sb = StringBuilder(lowerPrefix)
        collectWords(prefixNode, sb, results, maxResults * 3, maxDepth = 10)
        results.sortByDescending { it.second }
        return results.filter { it.first != lowerPrefix }.take(maxResults)
    }

    /**
     * Fuzzy matching for autocorrect: find dictionary words within [maxDistance]
     * edit distance of [typed], sorted by frequency descending.
     *
     * Searches the top 10,000 most frequent words — fast enough for main thread.
     */
    fun getFuzzyMatches(typed: String, maxDistance: Int = 2, maxResults: Int = 3): List<Pair<String, Int>> {
        if (typed.length < 2) return emptyList()
        val lower = typed.lowercase()
        val matches = mutableListOf<Pair<String, Int>>()

        for ((word, freq) in topWords) {
            // Quick length filter: edit distance can't be less than length difference
            if (kotlin.math.abs(word.length - lower.length) > maxDistance) continue
            // Skip exact match
            if (word == lower) continue

            val dist = editDistance(lower, word)
            if (dist in 1..maxDistance) {
                matches.add(word to freq)
                if (matches.size >= maxResults * 2) break
            }
        }

        matches.sortByDescending { it.second }
        return matches.take(maxResults)
    }

    @VisibleForTesting
    internal fun editDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        // Use single-row optimization for memory
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[n]
    }

    private fun collectWords(
        node: TrieNode,
        prefix: StringBuilder,
        results: MutableList<Pair<String, Int>>,
        limit: Int,
        maxDepth: Int
    ) {
        if (results.size >= limit || maxDepth <= 0) return
        if (node.isTerminal) {
            results.add(prefix.toString() to node.frequency)
        }
        for ((ch, child) in node.children) {
            prefix.append(ch)
            collectWords(child, prefix, results, limit, maxDepth - 1)
            prefix.deleteCharAt(prefix.length - 1)
            if (results.size >= limit) return
        }
    }
}
