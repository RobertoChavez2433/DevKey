package dev.devkey.keyboard.data.export

import android.content.Context
import android.net.Uri
import dev.devkey.keyboard.data.db.DevKeyDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ExportManager(private val database: DevKeyDatabase) {

    private val json = Json { prettyPrint = true }

    suspend fun export(appVersion: String): DevKeyBackup {
        val macros = database.macroDao().getAllMacrosList().map { macro ->
            MacroExport(
                name = macro.name,
                keySequence = macro.keySequence,
                createdAt = macro.createdAt,
                usageCount = macro.usageCount
            )
        }

        val learnedWords = database.learnedWordDao().getAllWordsList().map { word ->
            LearnedWordExport(
                word = word.word,
                frequency = word.frequency,
                contextApp = word.contextApp,
                isCommand = word.isCommand
            )
        }

        val commandApps = database.commandAppDao().getAllCommandAppsList().map { app ->
            CommandAppExport(
                packageName = app.packageName,
                mode = app.mode
            )
        }

        val pinnedClipboard = database.clipboardHistoryDao().getPinnedEntriesList().map { entry ->
            ClipboardExport(
                content = entry.content,
                timestamp = entry.timestamp
            )
        }

        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))

        return DevKeyBackup(
            schemaVersion = 1,
            exportedAt = now,
            appVersion = appVersion,
            macros = macros,
            learnedWords = learnedWords,
            commandApps = commandApps,
            pinnedClipboard = pinnedClipboard
        )
    }

    fun serialize(backup: DevKeyBackup): String {
        return json.encodeToString(backup)
    }

    suspend fun exportToUri(context: Context, uri: Uri, appVersion: String) {
        val backup = export(appVersion)
        val jsonString = serialize(backup)
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
        }
    }
}
