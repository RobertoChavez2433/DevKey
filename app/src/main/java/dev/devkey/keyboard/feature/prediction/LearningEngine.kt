package dev.devkey.keyboard.feature.prediction

import dev.devkey.keyboard.data.db.dao.LearnedWordDao
import dev.devkey.keyboard.data.db.entity.LearnedWordEntity
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks words the user has committed, enabling personalized predictions
 * and preventing autocorrect from overriding intentional word choices.
 *
 * Uses an in-memory cache for fast lookups, backed by Room persistence.
 *
 * @param dao The Room DAO for learned words.
 */
class LearningEngine(private val dao: LearnedWordDao) {

    /** In-memory cache of learned words for fast lookup (thread-safe). */
    private val learnedWordsCache: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Whether initialize() has been called. */
    private var initialized = false

    /**
     * Load all learned words from the database into the in-memory cache.
     * Should be called once during keyboard startup.
     */
    suspend fun initialize() {
        if (initialized) return
        try {
            val words = dao.getAllWords().first()
            learnedWordsCache.addAll(words.map { it.word })
            initialized = true
        } catch (e: Exception) {
            // If loading fails, continue with empty cache
        }
    }

    /**
     * Record that the user committed a word.
     *
     * If the word already exists, its frequency is incremented.
     * If it's new, it's inserted with frequency 1.
     *
     * @param word The committed word.
     * @param isCommand Whether this word was typed in command mode.
     * @param contextApp The package name of the app being used, or null.
     */
    suspend fun onWordCommitted(word: String, isCommand: Boolean, contextApp: String?) {
        if (word.length < 2) return

        try {
            val existing = dao.findWord(word, contextApp)
            if (existing != null) {
                dao.update(existing.copy(frequency = existing.frequency + 1))
            } else {
                dao.insert(
                    LearnedWordEntity(
                        word = word,
                        frequency = 1,
                        contextApp = contextApp,
                        isCommand = isCommand
                    )
                )
            }
            learnedWordsCache.add(word)
        } catch (e: Exception) {
            // Don't crash the keyboard if learning fails
        }
    }

    /**
     * Check whether the user has previously committed this word.
     */
    fun isLearnedWord(word: String): Boolean = word in learnedWordsCache

    /**
     * Get a snapshot of all learned words.
     */
    fun getLearnedWords(): Set<String> = learnedWordsCache.toSet()

    /**
     * Get command-mode suggestions matching a prefix, sorted by frequency.
     *
     * @param prefix The prefix to match.
     * @param limit Maximum number of results.
     * @return List of matching command words.
     */
    suspend fun getCommandSuggestions(prefix: String, limit: Int = 3): List<String> {
        return try {
            val commandWords = dao.getCommandWords().first()
            commandWords
                .filter { it.word.startsWith(prefix, ignoreCase = true) }
                .sortedByDescending { it.frequency }
                .take(limit)
                .map { it.word }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
