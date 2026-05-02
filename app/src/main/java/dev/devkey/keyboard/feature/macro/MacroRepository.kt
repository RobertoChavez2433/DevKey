package dev.devkey.keyboard.feature.macro

import dev.devkey.keyboard.data.db.dao.MacroDao
import dev.devkey.keyboard.data.db.entity.MacroEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for macro CRUD operations.
 * Wraps the Room DAO with domain-level methods.
 *
 * @param dao The macro data access object.
 */
class MacroRepository(private val dao: MacroDao) {

    /**
     * Returns a Flow of all macros, ordered by usage count descending.
     */
    fun getAllMacros(): Flow<List<MacroEntity>> = dao.getAllMacros()

    /**
     * Saves a new macro with the given name and steps.
     */
    suspend fun saveMacro(name: String, steps: List<MacroStep>) {
        val entity = MacroEntity(
            name = name,
            keySequence = MacroSerializer.serialize(steps),
            createdAt = System.currentTimeMillis(),
            usageCount = 0
        )
        dao.insert(entity)
    }

    suspend fun saveMacroReplacing(name: String, steps: List<MacroStep>): Long {
        dao.deleteByName(name)
        return dao.insert(
            MacroEntity(
                name = name,
                keySequence = MacroSerializer.serialize(steps),
                createdAt = System.currentTimeMillis(),
                usageCount = 0
            )
        )
    }

    suspend fun getMacroByName(name: String): MacroEntity? = dao.getMacroByName(name)

    /**
     * Deletes a macro by its ID.
     */
    suspend fun deleteMacro(id: Long) {
        val macro = dao.getMacroById(id) ?: return
        dao.delete(macro)
    }

    suspend fun deleteMacrosByName(name: String): Int = dao.deleteByName(name)

    /**
     * Updates the name of a macro.
     */
    suspend fun updateMacroName(id: Long, name: String) {
        val macro = dao.getMacroById(id) ?: return
        dao.update(macro.copy(name = name))
    }

    suspend fun updateMacroName(oldName: String, newName: String): Int =
        dao.updateNameByName(oldName, newName)

    /**
     * Increments the usage count for a macro.
     */
    suspend fun incrementUsage(id: Long) {
        dao.incrementUsageCount(id)
    }

    suspend fun getCount(): Int = dao.getCount()
}
