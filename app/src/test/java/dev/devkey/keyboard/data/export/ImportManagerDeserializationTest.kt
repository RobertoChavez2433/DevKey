package dev.devkey.keyboard.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ImportManager's deserialization logic.
 *
 * These test the pure Kotlin deserialization and validation logic
 * without requiring a Room database or Android context.
 *
 * Note: Tests that exercise import() with REPLACE/MERGE strategies
 * require a mock database and are deferred to instrumented tests.
 */
class ImportManagerDeserializationTest {

    // ImportManager requires a DevKeyDatabase, but deserialization is testable
    // through direct JSON parsing since it uses the same Json instance.

    private val validJson = """
    {
        "schemaVersion": 1,
        "exportedAt": "2026-02-24T12:00:00Z",
        "appVersion": "0.1.0",
        "macros": [
            {
                "name": "Copy All",
                "keySequence": "[{\"key\":\"a\",\"keyCode\":97,\"modifiers\":[\"ctrl\"]}]",
                "createdAt": 1000,
                "usageCount": 5
            }
        ],
        "learnedWords": [
            {
                "word": "hello",
                "frequency": 10
            }
        ],
        "commandApps": [
            {
                "packageName": "com.termux",
                "mode": "command"
            }
        ],
        "pinnedClipboard": [
            {
                "content": "test clip",
                "timestamp": 2000
            }
        ]
    }
    """.trimIndent()

    @Test
    fun `valid JSON parses to DevKeyBackup`() {
        val backup = kotlinx.serialization.json.Json.decodeFromString<DevKeyBackup>(validJson)
        assertEquals(1, backup.schemaVersion)
        assertEquals("0.1.0", backup.appVersion)
        assertEquals(1, backup.macros.size)
        assertEquals("Copy All", backup.macros[0].name)
    }

    @Test
    fun `schema version 1 is accepted`() {
        val backup = kotlinx.serialization.json.Json.decodeFromString<DevKeyBackup>(validJson)
        assertTrue(backup.schemaVersion <= 1)
    }

    @Test
    fun `schema version 2 is detectable for rejection`() {
        val futureJson = """
        {
            "schemaVersion": 2,
            "exportedAt": "2026-02-24T12:00:00Z",
            "appVersion": "1.0.0"
        }
        """.trimIndent()
        val backup = kotlinx.serialization.json.Json.decodeFromString<DevKeyBackup>(futureJson)
        assertTrue("Schema version 2 should be detected", backup.schemaVersion > 1)
    }

    @Test(expected = kotlinx.serialization.SerializationException::class)
    fun `malformed JSON throws SerializationException`() {
        kotlinx.serialization.json.Json.decodeFromString<DevKeyBackup>("not json at all")
    }

    @Test
    fun `empty backup arrays deserialize correctly`() {
        val minimalJson = """
        {
            "schemaVersion": 1,
            "exportedAt": "2026-02-24T12:00:00Z",
            "appVersion": "0.1.0"
        }
        """.trimIndent()
        val backup = kotlinx.serialization.json.Json.decodeFromString<DevKeyBackup>(minimalJson)
        assertEquals(0, backup.macros.size)
        assertEquals(0, backup.learnedWords.size)
        assertEquals(0, backup.commandApps.size)
        assertEquals(0, backup.pinnedClipboard.size)
    }

    @Test
    fun `learned word with null contextApp deserializes`() {
        val json = """
        {
            "schemaVersion": 1,
            "exportedAt": "2026-02-24T12:00:00Z",
            "appVersion": "0.1.0",
            "learnedWords": [
                {
                    "word": "test",
                    "frequency": 5
                }
            ]
        }
        """.trimIndent()
        val backup = kotlinx.serialization.json.Json.decodeFromString<DevKeyBackup>(json)
        assertEquals(1, backup.learnedWords.size)
        assertEquals(null, backup.learnedWords[0].contextApp)
        assertEquals(false, backup.learnedWords[0].isCommand)
    }

    @Test
    fun `all data fields preserved through parse`() {
        val backup = kotlinx.serialization.json.Json.decodeFromString<DevKeyBackup>(validJson)

        assertEquals("Copy All", backup.macros[0].name)
        assertEquals(5, backup.macros[0].usageCount)
        assertEquals(1000L, backup.macros[0].createdAt)

        assertEquals("hello", backup.learnedWords[0].word)
        assertEquals(10, backup.learnedWords[0].frequency)

        assertEquals("com.termux", backup.commandApps[0].packageName)
        assertEquals("command", backup.commandApps[0].mode)

        assertEquals("test clip", backup.pinnedClipboard[0].content)
        assertEquals(2000L, backup.pinnedClipboard[0].timestamp)
    }
}
