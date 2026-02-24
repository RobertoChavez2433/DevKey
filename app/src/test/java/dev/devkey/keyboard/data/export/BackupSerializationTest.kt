package dev.devkey.keyboard.data.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for BackupData serialization/deserialization.
 *
 * Tests the JSON round-trip of DevKeyBackup and related data classes.
 * These are pure Kotlin tests with no Android dependencies.
 */
class BackupSerializationTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun `serialize produces valid JSON with schema version 1`() {
        val backup = DevKeyBackup(
            schemaVersion = 1,
            exportedAt = "2026-02-24T12:00:00Z",
            appVersion = "0.1.0"
        )
        val jsonString = json.encodeToString(backup)
        // Verify round-trip: deserialize and check values
        val parsed = json.decodeFromString<DevKeyBackup>(jsonString)
        assertEquals(1, parsed.schemaVersion)
        assertEquals("0.1.0", parsed.appVersion)
        assertEquals("2026-02-24T12:00:00Z", parsed.exportedAt)
    }

    @Test
    fun `serialize includes all data types`() {
        val backup = DevKeyBackup(
            schemaVersion = 1,
            exportedAt = "2026-02-24T12:00:00Z",
            appVersion = "0.1.0",
            macros = listOf(
                MacroExport("test", "[{\"key\":\"a\"}]", 1000L, 5)
            ),
            learnedWords = listOf(
                LearnedWordExport("hello", 10, "com.example.app", false)
            ),
            commandApps = listOf(
                CommandAppExport("com.termux", "command")
            ),
            pinnedClipboard = listOf(
                ClipboardExport("test content", 2000L)
            )
        )
        val jsonString = json.encodeToString(backup)
        assertTrue(jsonString.contains("\"macros\""))
        assertTrue(jsonString.contains("\"learnedWords\""))
        assertTrue(jsonString.contains("\"commandApps\""))
        assertTrue(jsonString.contains("\"pinnedClipboard\""))
    }

    @Test
    fun `deserialize with valid JSON succeeds`() {
        val backup = DevKeyBackup(
            schemaVersion = 1,
            exportedAt = "2026-02-24T12:00:00Z",
            appVersion = "0.1.0",
            macros = listOf(
                MacroExport("Copy All", "[{\"key\":\"a\",\"keyCode\":97,\"modifiers\":[\"ctrl\"]}]", 1000L, 3)
            )
        )
        val jsonString = json.encodeToString(backup)
        val deserialized = json.decodeFromString<DevKeyBackup>(jsonString)

        assertEquals(1, deserialized.schemaVersion)
        assertEquals("0.1.0", deserialized.appVersion)
        assertEquals(1, deserialized.macros.size)
        assertEquals("Copy All", deserialized.macros[0].name)
        assertEquals(3, deserialized.macros[0].usageCount)
    }

    @Test
    fun `deserialize empty backup has empty arrays`() {
        val backup = DevKeyBackup(
            schemaVersion = 1,
            exportedAt = "2026-02-24T12:00:00Z",
            appVersion = "0.1.0"
        )
        val jsonString = json.encodeToString(backup)
        val deserialized = json.decodeFromString<DevKeyBackup>(jsonString)

        assertEquals(0, deserialized.macros.size)
        assertEquals(0, deserialized.learnedWords.size)
        assertEquals(0, deserialized.commandApps.size)
        assertEquals(0, deserialized.pinnedClipboard.size)
    }

    @Test
    fun `roundtrip preserves all data`() {
        val original = DevKeyBackup(
            schemaVersion = 1,
            exportedAt = "2026-02-24T12:00:00Z",
            appVersion = "0.1.0",
            macros = listOf(
                MacroExport("Paste", "[{\"key\":\"v\",\"keyCode\":118,\"modifiers\":[\"ctrl\"]}]", 500L, 10),
                MacroExport("Undo", "[{\"key\":\"z\",\"keyCode\":122,\"modifiers\":[\"ctrl\"]}]", 600L, 7)
            ),
            learnedWords = listOf(
                LearnedWordExport("kotlin", 15, null, false),
                LearnedWordExport("ls", 20, "com.termux", true)
            ),
            commandApps = listOf(
                CommandAppExport("com.termux", "command"),
                CommandAppExport("com.example.notes", "normal")
            ),
            pinnedClipboard = listOf(
                ClipboardExport("Important note", 3000L)
            )
        )

        val jsonString = json.encodeToString(original)
        val deserialized = json.decodeFromString<DevKeyBackup>(jsonString)

        assertEquals(original.schemaVersion, deserialized.schemaVersion)
        assertEquals(original.exportedAt, deserialized.exportedAt)
        assertEquals(original.appVersion, deserialized.appVersion)
        assertEquals(original.macros, deserialized.macros)
        assertEquals(original.learnedWords, deserialized.learnedWords)
        assertEquals(original.commandApps, deserialized.commandApps)
        assertEquals(original.pinnedClipboard, deserialized.pinnedClipboard)
    }

    @Test
    fun `LearnedWordExport nullable contextApp works`() {
        val word1 = LearnedWordExport("hello", 10, null, false)
        val word2 = LearnedWordExport("ls", 5, "com.termux", true)

        val jsonString = json.encodeToString(listOf(word1, word2))
        val deserialized = json.decodeFromString<List<LearnedWordExport>>(jsonString)

        assertEquals(null, deserialized[0].contextApp)
        assertEquals("com.termux", deserialized[1].contextApp)
        assertEquals(false, deserialized[0].isCommand)
        assertEquals(true, deserialized[1].isCommand)
    }

    @Test(expected = kotlinx.serialization.SerializationException::class)
    fun `deserialize with malformed JSON throws SerializationException`() {
        json.decodeFromString<DevKeyBackup>("{ not valid json }")
    }

    @Test
    fun `deserialize with future schema version still parses`() {
        // The deserialization itself should work; version check is in ImportManager
        val futureBackup = """
        {
            "schemaVersion": 2,
            "exportedAt": "2026-02-24T12:00:00Z",
            "appVersion": "1.0.0"
        }
        """.trimIndent()
        val backup = json.decodeFromString<DevKeyBackup>(futureBackup)
        assertEquals(2, backup.schemaVersion)
    }
}
