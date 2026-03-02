package dev.devkey.keyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for KeyData.
 *
 * Tests default values (type, weight, isRepeatable, longPress)
 * and structural equality.
 */
class KeyDataTest {

    // --- Default values ---

    @Test
    fun `KeyData default type is LETTER`() {
        val key = KeyData("a", 'a'.code)
        assertEquals(KeyType.LETTER, key.type)
    }

    @Test
    fun `KeyData default weight is 1_0f`() {
        val key = KeyData("a", 'a'.code)
        assertEquals(1.0f, key.weight)
    }

    @Test
    fun `KeyData default isRepeatable is false`() {
        val key = KeyData("a", 'a'.code)
        assertFalse(key.isRepeatable)
    }

    @Test
    fun `KeyData default longPressLabel is null`() {
        val key = KeyData("a", 'a'.code)
        assertNull(key.longPressLabel)
    }

    @Test
    fun `KeyData default longPressCode is null`() {
        val key = KeyData("a", 'a'.code)
        assertNull(key.longPressCode)
    }

    // --- Explicit values ---

    @Test
    fun `KeyData stores primaryLabel and primaryCode correctly`() {
        val key = KeyData("Enter", 10, type = KeyType.ACTION)
        assertEquals("Enter", key.primaryLabel)
        assertEquals(10, key.primaryCode)
        assertEquals(KeyType.ACTION, key.type)
    }

    // --- Structural equality ---

    @Test
    fun `two KeyData instances with same values are equal`() {
        val key1 = KeyData("q", 'q'.code, longPressLabel = "!", longPressCode = '!'.code)
        val key2 = KeyData("q", 'q'.code, longPressLabel = "!", longPressCode = '!'.code)
        assertEquals(key1, key2)
    }

    @Test
    fun `KeyData copy with changed type creates new distinct instance`() {
        val key = KeyData("q", 'q'.code)
        val modified = key.copy(type = KeyType.SPECIAL)
        assertEquals(KeyType.LETTER, key.type)
        assertEquals(KeyType.SPECIAL, modified.type)
    }

    @Test
    fun `KeyData with isRepeatable true stores correctly`() {
        val key = KeyData("Del", -5, type = KeyType.ACTION, isRepeatable = true)
        assertEquals(true, key.isRepeatable)
    }

    @Test
    fun `KeyData weight can be set to non-default value`() {
        val key = KeyData(" ", ' '.code, type = KeyType.SPACEBAR, weight = 5.0f)
        assertEquals(5.0f, key.weight)
    }

    // --- Edge cases ---

    @Test
    fun `negative keycode stored correctly`() {
        val key = KeyData("Esc", -111)
        assertEquals(-111, key.primaryCode)
    }

    @Test
    fun `KeyData hashCode consistent with equals`() {
        val key1 = KeyData("a", 97, longPressLabel = "~", longPressCode = '~'.code)
        val key2 = KeyData("a", 97, longPressLabel = "~", longPressCode = '~'.code)
        assertEquals(key1, key2)
        assertEquals(key1.hashCode(), key2.hashCode())
    }
}
