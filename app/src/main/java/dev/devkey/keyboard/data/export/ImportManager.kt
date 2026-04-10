package dev.devkey.keyboard.data.export

import android.content.Context
import android.net.Uri
import dev.devkey.keyboard.data.db.DevKeyDatabase
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

        val resolver = ConflictResolver(
            macroDao = database.macroDao(),
            learnedWordDao = database.learnedWordDao(),
            commandAppDao = database.commandAppDao(),
            clipboardDao = database.clipboardHistoryDao(),
        )

        return when (strategy) {
            ConflictStrategy.REPLACE -> resolver.applyReplace(backup)
            ConflictStrategy.MERGE -> resolver.applyMerge(backup)
            ConflictStrategy.CANCEL -> ImportResult(macros = 0, words = 0, commandApps = 0, clipboard = 0)
        }
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
