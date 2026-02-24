package dev.devkey.keyboard.data.export

import android.content.Context
import android.net.Uri
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.data.db.entity.ClipboardHistoryEntity
import dev.devkey.keyboard.data.db.entity.CommandAppEntity
import dev.devkey.keyboard.data.db.entity.LearnedWordEntity
import dev.devkey.keyboard.data.db.entity.MacroEntity
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ImportManager(private val database: DevKeyDatabase) {

    enum class ConflictStrategy { REPLACE, MERGE, CANCEL }

    data class ImportResult(
        val macros: Int,
        val words: Int,
        val commandApps: Int,
        val clipboard: Int
    )

    fun deserialize(json: String): Result<DevKeyBackup> {
        return try {
            val backup = Json.decodeFromString<DevKeyBackup>(json)
            if (backup.schemaVersion > 1) {
                Result.failure(
                    IllegalArgumentException(
                        "Unsupported backup schema version: ${backup.schemaVersion}. " +
                                "Please update DevKey to import this backup."
                    )
                )
            } else {
                Result.success(backup)
            }
        } catch (e: SerializationException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    suspend fun importBackup(backup: DevKeyBackup, strategy: ConflictStrategy): ImportResult {
        if (strategy == ConflictStrategy.CANCEL) {
            return ImportResult(macros = 0, words = 0, commandApps = 0, clipboard = 0)
        }

        val macroDao = database.macroDao()
        val learnedWordDao = database.learnedWordDao()
        val commandAppDao = database.commandAppDao()
        val clipboardDao = database.clipboardHistoryDao()

        var macroCount = 0
        var wordCount = 0
        var commandAppCount = 0
        var clipboardCount = 0

        if (strategy == ConflictStrategy.REPLACE) {
            macroDao.deleteAll()
            learnedWordDao.deleteAll()
            commandAppDao.deleteAll()
            clipboardDao.deleteAll()

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
        } else if (strategy == ConflictStrategy.MERGE) {
            // Macros: insert if no existing macro with same name
            val existingMacros = macroDao.getAllMacrosList()
            val existingMacroNames = existingMacros.map { it.name }.toSet()
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

            // Learned words: if word+contextApp exists, keep higher frequency; else insert
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

            // Command apps: upsert (imported value wins)
            for (app in backup.commandApps) {
                commandAppDao.insert(
                    CommandAppEntity(
                        packageName = app.packageName,
                        mode = app.mode
                    )
                )
                commandAppCount++
            }

            // Pinned clipboard: insert if content doesn't already exist
            val existingPinned = clipboardDao.getPinnedEntriesList()
            val existingContents = existingPinned.map { it.content }.toSet()
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
        }

        return ImportResult(
            macros = macroCount,
            words = wordCount,
            commandApps = commandAppCount,
            clipboard = clipboardCount
        )
    }

    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        strategy: ConflictStrategy
    ): Result<ImportResult> {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return Result.failure(IllegalStateException("Could not read file"))

            val backupResult = deserialize(jsonString)
            if (backupResult.isFailure) {
                return Result.failure(backupResult.exceptionOrNull()!!)
            }

            val result = importBackup(backupResult.getOrThrow(), strategy)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
