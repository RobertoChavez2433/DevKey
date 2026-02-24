package dev.devkey.keyboard.feature.clipboard

import dev.devkey.keyboard.data.db.dao.ClipboardHistoryDao
import dev.devkey.keyboard.data.db.entity.ClipboardHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for clipboard history CRUD operations.
 * Wraps the Room DAO with domain-level methods and enforces the max entry limit.
 *
 * @param dao The clipboard history data access object.
 */
class ClipboardRepository(private val dao: ClipboardHistoryDao) {

    companion object {
        const val MAX_ENTRIES = 50
    }

    /**
     * Returns a Flow of all clipboard entries, ordered by timestamp descending.
     */
    fun getAll(): Flow<List<ClipboardHistoryEntity>> = dao.getAllEntries()

    /**
     * Returns a Flow of entries filtered by a search query (case-insensitive).
     */
    fun search(query: String): Flow<List<ClipboardHistoryEntity>> {
        return dao.getAllEntries().map { entries ->
            entries.filter { it.content.contains(query, ignoreCase = true) }
        }
    }

    /**
     * Adds a new clipboard entry. Enforces max limit by deleting oldest unpinned entries.
     */
    suspend fun addEntry(content: String) {
        if (content.isBlank()) return
        val entity = ClipboardHistoryEntity(
            content = content,
            timestamp = System.currentTimeMillis()
        )
        dao.insert(entity)

        // Enforce max limit
        val count = dao.getCount()
        if (count > MAX_ENTRIES) {
            dao.deleteOldest(count - MAX_ENTRIES)
        }
    }

    /**
     * Pins a clipboard entry.
     */
    suspend fun pin(id: Long) {
        val entry = dao.getEntryById(id) ?: return
        dao.update(entry.copy(isPinned = true))
    }

    /**
     * Unpins a clipboard entry.
     */
    suspend fun unpin(id: Long) {
        val entry = dao.getEntryById(id) ?: return
        dao.update(entry.copy(isPinned = false))
    }

    /**
     * Deletes a clipboard entry by ID.
     */
    suspend fun delete(id: Long) {
        val entry = dao.getEntryById(id) ?: return
        dao.delete(entry)
    }

    /**
     * Clears all clipboard history (including pinned items).
     */
    suspend fun clearAll() {
        dao.deleteAll()
    }
}
