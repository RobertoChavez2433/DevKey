package dev.devkey.keyboard.integration

import android.Manifest
import android.content.Context
import dev.devkey.keyboard.feature.voice.VoiceInputEngine
import dev.devkey.keyboard.feature.voice.VoiceInputEngine.VoiceState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

/**
 * Integration test for the voice pipeline.
 *
 * Wires a real [VoiceInputEngine] (which internally creates a real
 * [WhisperProcessor]). AudioRecord and TFLite Interpreter are not
 * available under Robolectric, so these tests verify state machine
 * behavior and error-handling paths rather than actual inference.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VoicePipelineTest {

    private lateinit var context: Context
    private lateinit var shadowApp: ShadowApplication
    private lateinit var engine: VoiceInputEngine

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowApp = Shadows.shadowOf(RuntimeEnvironment.getApplication())
        engine = VoiceInputEngine(context)
    }

    // ------------------------------------------------------------------
    // Test 1: start -> stop flow without permission -> IDLE -> stop -> IDLE
    // ------------------------------------------------------------------

    @Test
    fun `start without permission stays IDLE then stop returns to IDLE`() = runBlocking {
        shadowApp.denyPermissions(Manifest.permission.RECORD_AUDIO)

        // Permission prompting is owned by the UI entry points. The engine
        // should not enter ERROR just because runtime permission is absent.
        val started = engine.startListening()
        assertEquals(false, started)
        assertEquals(VoiceState.IDLE, engine.state.value)

        // stopListening should still work and return to IDLE
        val result = engine.stopListening()
        assertEquals(VoiceState.IDLE, engine.state.value)
        // No audio was captured, so result is empty
        assertEquals("", result)
    }

    // ------------------------------------------------------------------
    // Test 2: cancel mid-processing -> IDLE
    // ------------------------------------------------------------------

    @Test
    fun `cancel from simulated PROCESSING returns to IDLE`() {
        // Simulate PROCESSING state (as if audio was being processed)
        val stateField = VoiceInputEngine::class.java.getDeclaredField("_state")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<VoiceState>
        stateFlow.value = VoiceState.PROCESSING

        engine.cancelListening()

        assertEquals(VoiceState.IDLE, engine.state.value)
    }

    // ------------------------------------------------------------------
    // Test 3: state machine round-trip IDLE -> IDLE -> IDLE
    // ------------------------------------------------------------------

    @Test
    fun `state machine round-trip verification`() = runBlocking {
        shadowApp.denyPermissions(Manifest.permission.RECORD_AUDIO)

        // 1. Start from IDLE
        assertEquals(VoiceState.IDLE, engine.state.value)

        // 2. startListening without permission -> IDLE
        val started = engine.startListening()
        assertEquals(false, started)
        assertEquals(VoiceState.IDLE, engine.state.value)

        // 3. cancelListening -> IDLE
        engine.cancelListening()
        assertEquals(VoiceState.IDLE, engine.state.value)

        // 4. stopListening from IDLE -> stays IDLE (no audio to process)
        val result = engine.stopListening()
        assertEquals(VoiceState.IDLE, engine.state.value)
        assertEquals("", result)

        // 5. release -> stays IDLE, no crash
        engine.release()
        assertEquals(VoiceState.IDLE, engine.state.value)
    }
}
