package dev.devkey.keyboard.feature.command

import dev.devkey.keyboard.data.db.dao.CommandAppDao
import dev.devkey.keyboard.data.db.entity.CommandAppEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for user-configured command mode overrides per app.
 *
 * Persists per-app mode settings via Room, allowing users to force
 * specific apps into command or normal mode.
 *
 * @param dao The Room DAO for command app entities.
 */
class CommandModeRepository(private val dao: CommandAppDao) {

    /**
     * Get the user-configured mode for a specific app.
     *
     * @param packageName The app's package name.
     * @return The configured [InputMode], or null if no override exists.
     */
    suspend fun getMode(packageName: String?): InputMode? {
        if (packageName == null) return null
        val entity = dao.getByPackageName(packageName) ?: return null
        return if (entity.mode == "command") InputMode.COMMAND else InputMode.NORMAL
    }

    /**
     * Set or update the mode override for a specific app.
     *
     * @param packageName The app's package name.
     * @param mode The mode to assign.
     */
    suspend fun setMode(packageName: String, mode: InputMode) {
        dao.insert(CommandAppEntity(packageName, mode.name.lowercase()))
    }

    /**
     * Remove the mode override for a specific app, reverting to auto-detection.
     *
     * @param packageName The app's package name.
     */
    suspend fun removeOverride(packageName: String) {
        val entity = dao.getByPackageName(packageName) ?: return
        dao.delete(entity)
    }

    /**
     * Get a Flow of all configured app overrides.
     */
    fun getAllOverrides(): Flow<List<CommandAppEntity>> {
        return dao.getAllCommandApps()
    }
}
