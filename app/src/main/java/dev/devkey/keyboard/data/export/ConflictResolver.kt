package dev.devkey.keyboard.data.export

import dev.devkey.keyboard.data.db.dao.ClipboardHistoryDao
import dev.devkey.keyboard.data.db.dao.CommandAppDao
import dev.devkey.keyboard.data.db.dao.LearnedWordDao
import dev.devkey.keyboard.data.db.dao.MacroDao
import dev.devkey.keyboard.data.db.entity.ClipboardHistoryEntity
import dev.devkey.keyboard.data.db.entity.CommandAppEntity
import dev.devkey.keyboard.data.db.entity.LearnedWordEntity
import dev.devkey.keyboard.data.db.entity.MacroEntity

/**
 * Applies REPLACE or MERGE conflict resolution strategies when importing a [DevKeyBackup].
 *
 * Returns an [ImportManager.ImportResult] tallying how many rows were written per category.
 */
internal class ConflictResolver(
    private val macroDao: MacroDao,
    private val learnedWordDao: LearnedWordDao,
    private val commandAppDao: CommandAppDao,
    private val clipboardDao: ClipboardHistoryDao,
) {

    suspend fun applyReplace(backup: DevKeyBackup): ImportManager.ImportResult {
        macroDao.deleteAll()
        learnedWordDao.deleteAll()
        commandAppDao.deleteAll()
        clipboardDao.deleteAll()

        var macroCount = 0
        var wordCount = 0
        var commandAppCount = 0
        var clipboardCount = 0

        for (macro in backup.macros) {
            macroDao.insert(
                MacroEntity(
                    name = macro.name,
                    keySequence = macro.keySequence,
                    createdAt = macro.createdAt,
                    usageCount = macro.usageCount
                )
            )
            macroCount++
        }

        for (word in backup.learnedWords) {
            learnedWordDao.insert(
                LearnedWordEntity(
                    word = word.word,
                    frequency = word.frequency,
                    contextApp = word.contextApp,
                    isCommand = word.isCommand
                )
            )
            wordCount++
        }

        for (app in backup.commandApps) {
            commandAppDao.insert(
                CommandAppEntity(
                    packageName = app.packageName,
                    mode = app.mode
                )
            )
            commandAppCount++
        }

        for (clip in backup.pinnedClipboard) {
            clipboardDao.insert(
                ClipboardHistoryEntity(
                    content = clip.content,
                    timestamp = clip.timestamp,
                    isPinned = true
                )
            )
            clipboardCount++
        }

        return ImportManager.ImportResult(
            macros = macroCount,
            words = wordCount,
            commandApps = commandAppCount,
            clipboard = clipboardCount
        )
    }

    suspend fun applyMerge(backup: DevKeyBackup): ImportManager.ImportResult {
        var macroCount = 0
        var wordCount = 0
        var commandAppCount = 0
        var clipboardCount = 0

        // Macros: insert only when no existing macro has the same name
        val existingMacroNames = macroDao.getAllMacrosList().map { it.name }.toSet()
        for (macro in backup.macros) {
            if (macro.name !in existingMacroNames) {
                macroDao.insert(
                    MacroEntity(
                        name = macro.name,
                        keySequence = macro.keySequence,
                        createdAt = macro.createdAt,
                        usageCount = macro.usageCount
                    )
                )
                macroCount++
            }
        }

        // Learned words: keep higher frequency when word+contextApp already exists
        for (word in backup.learnedWords) {
            val existing = learnedWordDao.findWord(word.word, word.contextApp)
            if (existing != null) {
                if (word.frequency > existing.frequency) {
                    learnedWordDao.update(existing.copy(frequency = word.frequency))
                    wordCount++
                }
            } else {
                learnedWordDao.insert(
                    LearnedWordEntity(
                        word = word.word,
                        frequency = word.frequency,
                        contextApp = word.contextApp,
                        isCommand = word.isCommand
                    )
                )
                wordCount++
            }
        }

        // Command apps: upsert — imported value wins
        for (app in backup.commandApps) {
            commandAppDao.insert(
                CommandAppEntity(
                    packageName = app.packageName,
                    mode = app.mode
                )
            )
            commandAppCount++
        }

        // Pinned clipboard: insert only when content does not already exist
        val existingContents = clipboardDao.getPinnedEntriesList().map { it.content }.toSet()
        for (clip in backup.pinnedClipboard) {
            if (clip.content !in existingContents) {
                clipboardDao.insert(
                    ClipboardHistoryEntity(
                        content = clip.content,
                        timestamp = clip.timestamp,
                        isPinned = true
                    )
                )
                clipboardCount++
            }
        }

        return ImportManager.ImportResult(
            macros = macroCount,
            words = wordCount,
            commandApps = commandAppCount,
            clipboard = clipboardCount
        )
    }
}
