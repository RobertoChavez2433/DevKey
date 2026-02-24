package dev.devkey.keyboard.data.export

import kotlinx.serialization.Serializable

@Serializable
data class DevKeyBackup(
    val schemaVersion: Int = 1,
    val exportedAt: String, // ISO 8601
    val appVersion: String,
    val macros: List<MacroExport> = emptyList(),
    val learnedWords: List<LearnedWordExport> = emptyList(),
    val commandApps: List<CommandAppExport> = emptyList(),
    val pinnedClipboard: List<ClipboardExport> = emptyList()
)

@Serializable
data class MacroExport(
    val name: String,
    val keySequence: String, // JSON string from MacroEntity
    val createdAt: Long,
    val usageCount: Int
)

@Serializable
data class LearnedWordExport(
    val word: String,
    val frequency: Int,
    val contextApp: String? = null,
    val isCommand: Boolean = false
)

@Serializable
data class CommandAppExport(
    val packageName: String,
    val mode: String
)

@Serializable
data class ClipboardExport(
    val content: String,
    val timestamp: Long
)
