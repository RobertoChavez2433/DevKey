package dev.devkey.keyboard.feature.macro

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for MacroSerializer.
 *
 * Tests JSON round-trips, empty/blank/invalid input, field preservation,
 * and modifier handling. Uses org.json (JVM-compatible, no Android).
 */
class MacroSerializerTest {

    // --- serialize ---

    @Test
    fun `serialize empty list produces empty JSON array`() {
        val json = MacroSerializer.serialize(emptyList())
        assertEquals("[]", json)
    }

    @Test
    fun `serialize single step produces valid JSON with correct structure`() {
        val steps = listOf(MacroStep("c", 99, listOf("ctrl")))
        val json = MacroSerializer.serialize(steps)
        val array = JSONArray(json)
        assertEquals(1, array.length())
        val obj = array.getJSONObject(0)
        assertEquals("c", obj.getString("key"))
        assertEquals(99, obj.getInt("keyCode"))
        assertEquals("ctrl", obj.getJSONArray("modifiers").getString(0))
    }

    @Test
    fun `serialize preserves key field`() {
        val steps = listOf(MacroStep("v", 118, listOf("ctrl")))
        val json = MacroSerializer.serialize(steps)
        val result = MacroSerializer.deserialize(json)
        assertEquals("v", result[0].key)
    }

    @Test
    fun `serialize preserves keyCode field`() {
        val steps = listOf(MacroStep("a", 97, listOf()))
        val json = MacroSerializer.serialize(steps)
        val result = MacroSerializer.deserialize(json)
        assertEquals(97, result[0].keyCode)
    }

    @Test
    fun `serialize multiple steps produces array with correct count`() {
        val steps = listOf(
            MacroStep("c", 99, listOf("ctrl")),
            MacroStep("v", 118, listOf("ctrl"))
        )
        val result = MacroSerializer.deserialize(MacroSerializer.serialize(steps))
        assertEquals(2, result.size)
    }

    // --- deserialize ---

    @Test
    fun `deserialize empty string returns empty list`() {
        val result = MacroSerializer.deserialize("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deserialize blank string returns empty list`() {
        val result = MacroSerializer.deserialize("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deserialize invalid JSON returns empty list`() {
        val result = MacroSerializer.deserialize("not json at all {{{")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deserialize empty JSON array returns empty list`() {
        val result = MacroSerializer.deserialize("[]")
        assertTrue(result.isEmpty())
    }

    // --- round-trip ---

    @Test
    fun `round-trip preserves key keyCode and modifiers`() {
        val original = listOf(MacroStep("c", 99, listOf("ctrl")))
        val json = MacroSerializer.serialize(original)
        val result = MacroSerializer.deserialize(json)
        assertEquals(1, result.size)
        assertEquals("c", result[0].key)
        assertEquals(99, result[0].keyCode)
        assertEquals(listOf("ctrl"), result[0].modifiers)
    }

    @Test
    fun `round-trip preserves multiple modifiers`() {
        val original = listOf(MacroStep("a", 97, listOf("ctrl", "shift")))
        val json = MacroSerializer.serialize(original)
        val result = MacroSerializer.deserialize(json)
        assertEquals(listOf("ctrl", "shift"), result[0].modifiers)
    }

    @Test
    fun `round-trip preserves step with no modifiers`() {
        val original = listOf(MacroStep("x", 120, emptyList()))
        val json = MacroSerializer.serialize(original)
        val result = MacroSerializer.deserialize(json)
        assertEquals(1, result.size)
        assertTrue(result[0].modifiers.isEmpty())
        assertEquals("x", result[0].key)
    }

    // --- Edge cases ---

    @Test
    fun `deserialize JSON object not array returns empty`() {
        val result = MacroSerializer.deserialize("{}")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deserialize missing key defaults to empty string`() {
        val result = MacroSerializer.deserialize("""[{"keyCode":99}]""")
        assertEquals(1, result.size)
        assertEquals("", result[0].key)
    }

    @Test
    fun `deserialize missing keyCode defaults to 0`() {
        val result = MacroSerializer.deserialize("""[{"key":"c"}]""")
        assertEquals(1, result.size)
        assertEquals(0, result[0].keyCode)
    }

    @Test
    fun `deserialize missing modifiers defaults to empty`() {
        val result = MacroSerializer.deserialize("""[{"key":"c","keyCode":99}]""")
        assertEquals(1, result.size)
        assertTrue(result[0].modifiers.isEmpty())
    }

    @Test
    fun `multi-step round-trip preserves all fields`() {
        val original = listOf(
            MacroStep("a", 97, listOf("ctrl")),
            MacroStep("b", 98, listOf("shift", "alt")),
            MacroStep("c", 99, emptyList())
        )
        val json = MacroSerializer.serialize(original)
        val result = MacroSerializer.deserialize(json)
        assertEquals(3, result.size)
        assertEquals("a", result[0].key)
        assertEquals(97, result[0].keyCode)
        assertEquals(listOf("ctrl"), result[0].modifiers)
        assertEquals("b", result[1].key)
        assertEquals(98, result[1].keyCode)
        assertEquals(listOf("shift", "alt"), result[1].modifiers)
        assertEquals("c", result[2].key)
        assertEquals(99, result[2].keyCode)
        assertTrue(result[2].modifiers.isEmpty())
    }
}
