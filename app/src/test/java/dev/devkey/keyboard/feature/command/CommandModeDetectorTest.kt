package dev.devkey.keyboard.feature.command

import dev.devkey.keyboard.testutil.FakeCommandAppDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CommandModeDetector.
 *
 * Tests initial mode, null package, known terminals, unknown packages,
 * user overrides, and manual toggle.
 * Uses FakeCommandAppDao to avoid Room/Android dependencies.
 */
class CommandModeDetectorTest {

    private lateinit var fakeDao: FakeCommandAppDao
    private lateinit var repository: CommandModeRepository
    private lateinit var detector: CommandModeDetector

    @Before
    fun setUp() {
        fakeDao = FakeCommandAppDao()
        repository = CommandModeRepository(fakeDao)
        detector = CommandModeDetector(repository)
    }

    // --- Initial state ---

    @Test
    fun `initial input mode is NORMAL`() {
        assertEquals(InputMode.NORMAL, detector.inputMode.value)
    }

    @Test
    fun `isCommandMode returns false initially`() {
        assertFalse(detector.isCommandMode())
    }

    // --- null package ---

    @Test
    fun `detect with null package results in NORMAL mode`() = runTest {
        detector.detect(null)
        assertEquals(InputMode.NORMAL, detector.inputMode.value)
    }

    // --- Known terminals ---

    @Test
    fun `detect com_termux sets mode to COMMAND`() = runTest {
        detector.detect("com.termux")
        assertEquals(InputMode.COMMAND, detector.inputMode.value)
    }

    @Test
    fun `detect org_connectbot sets mode to COMMAND`() = runTest {
        detector.detect("org.connectbot")
        assertEquals(InputMode.COMMAND, detector.inputMode.value)
    }

    @Test
    fun `detect jackpal_androidterm sets mode to COMMAND`() = runTest {
        detector.detect("jackpal.androidterm")
        assertEquals(InputMode.COMMAND, detector.inputMode.value)
    }

    @Test
    fun `detect com_sonelli_juicessh sets mode to COMMAND`() = runTest {
        detector.detect("com.sonelli.juicessh")
        assertEquals(InputMode.COMMAND, detector.inputMode.value)
    }

    // --- Unknown packages ---

    @Test
    fun `detect unknown package results in NORMAL mode`() = runTest {
        detector.detect("com.example.myapp")
        assertEquals(InputMode.NORMAL, detector.inputMode.value)
    }

    // --- User overrides ---

    @Test
    fun `user override for unknown package forces COMMAND mode`() = runTest {
        repository.setMode("com.example.notes", InputMode.COMMAND)
        detector.detect("com.example.notes")
        assertEquals(InputMode.COMMAND, detector.inputMode.value)
    }

    @Test
    fun `user override NORMAL for known terminal forces NORMAL mode`() = runTest {
        repository.setMode("com.termux", InputMode.NORMAL)
        detector.detect("com.termux")
        assertEquals(InputMode.NORMAL, detector.inputMode.value)
    }

    // --- Manual toggle ---

    @Test
    fun `toggleManualOverride switches mode to COMMAND`() {
        detector.toggleManualOverride()
        assertEquals(InputMode.COMMAND, detector.inputMode.value)
        assertTrue(detector.isCommandMode())
    }

    @Test
    fun `toggleManualOverride twice cycles back to NORMAL`() {
        detector.toggleManualOverride() // -> COMMAND
        detector.toggleManualOverride() // -> NORMAL
        assertEquals(InputMode.NORMAL, detector.inputMode.value)
    }

    @Test
    fun `manual override is cleared on app switch`() = runTest {
        detector.detect("com.example.app")
        detector.toggleManualOverride() // set manual COMMAND
        detector.detect("com.other.app") // switch app — clears manual override
        assertEquals(InputMode.NORMAL, detector.inputMode.value)
    }

    // --- TERMINAL_PACKAGES completeness ---

    @Test
    fun `TERMINAL_PACKAGES contains exactly 9 entries`() {
        assertEquals(9, CommandModeDetector.TERMINAL_PACKAGES.size)
    }

    @Test
    fun `detect com_server_auditor sets COMMAND`() = runTest {
        detector.detect("com.server.auditor")
        assertEquals(InputMode.COMMAND, detector.inputMode.value)
    }

    @Test
    fun `detect com_offsec_nethunter sets COMMAND`() = runTest {
        detector.detect("com.offsec.nethunter")
        assertEquals(InputMode.COMMAND, detector.inputMode.value)
    }

    @Test
    fun `detect yarolegovich_materialterminal sets COMMAND`() = runTest {
        detector.detect("yarolegovich.materialterminal")
        assertEquals(InputMode.COMMAND, detector.inputMode.value)
    }

    @Test
    fun `detect com_termoneplus sets COMMAND`() = runTest {
        detector.detect("com.termoneplus")
        assertEquals(InputMode.COMMAND, detector.inputMode.value)
    }

    @Test
    fun `detect com_googlecode_android_scripting sets COMMAND`() = runTest {
        detector.detect("com.googlecode.android_scripting")
        assertEquals(InputMode.COMMAND, detector.inputMode.value)
    }

    // --- detectSync ---

    @Test
    fun `detectSync produces same result as detect`() {
        detector.detectSync("com.termux")
        assertEquals(InputMode.COMMAND, detector.inputMode.value)
    }
}
