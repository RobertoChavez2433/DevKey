package dev.devkey.keyboard.feature.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

/**
 * Unit tests for [VoiceInputEngine].
 *
 * TFLite Interpreter is unavailable under Robolectric, so tests focus on
 * the state machine transitions and error-handling paths that do not
 * require actual model inference.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VoiceInputEngineTest {

    private lateinit var context: Context
    private lateinit var shadowApp: ShadowApplication

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowApp = Shadows.shadowOf(RuntimeEnvironment.getApplication())
    }

    // ------------------------------------------------------------------
    // State: initial state is IDLE
    // ------------------------------------------------------------------

    @Test
    fun `initial state is IDLE`() {
        val engine = VoiceInputEngine(context)
        assertEquals(VoiceInputEngine.VoiceState.IDLE, engine.state.value)
    }

    // ------------------------------------------------------------------
    // startListening without permission -> IDLE + false
    // ------------------------------------------------------------------

    @Test
    fun `startListening without RECORD_AUDIO permission stays IDLE`() = runBlocking {
        // Robolectric defaults to permission denied
        shadowApp.denyPermissions(Manifest.permission.RECORD_AUDIO)

        val engine = VoiceInputEngine(context)
        val started = engine.startListening()

        assertEquals(false, started)
        assertEquals(VoiceInputEngine.VoiceState.IDLE, engine.state.value)
    }

    // ------------------------------------------------------------------
    // startListening when already LISTENING -> no state change
    // ------------------------------------------------------------------

    @Test
    fun `startListening when already LISTENING is a no-op`() = runBlocking {
        // We cannot fully start listening (AudioRecord won't init under Robolectric),
        // but we can verify the guard by checking the state machine contract.
        // The engine checks _state == LISTENING as the first guard.
        // We use reflection to set the state to LISTENING and verify no transition.
        val engine = VoiceInputEngine(context)

        // Use the internal MutableStateFlow to simulate LISTENING state
        val stateField = VoiceInputEngine::class.java.getDeclaredField("_state")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<VoiceInputEngine.VoiceState>
        stateFlow.value = VoiceInputEngine.VoiceState.LISTENING

        // Now call startListening — should be a no-op (stays LISTENING)
        engine.startListening()

        assertEquals(VoiceInputEngine.VoiceState.LISTENING, engine.state.value)
    }

    // ------------------------------------------------------------------
    // cancelListening -> IDLE
    // ------------------------------------------------------------------

    @Test
    fun `cancelListening sets state to IDLE`() {
        val engine = VoiceInputEngine(context)
        engine.cancelListening()
        assertEquals(VoiceInputEngine.VoiceState.IDLE, engine.state.value)
    }

    // ------------------------------------------------------------------
    // cancelListening from simulated LISTENING -> IDLE
    // ------------------------------------------------------------------

    @Test
    fun `cancelListening from LISTENING returns to IDLE`() {
        val engine = VoiceInputEngine(context)

        // Simulate LISTENING state
        val stateField = VoiceInputEngine::class.java.getDeclaredField("_state")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<VoiceInputEngine.VoiceState>
        stateFlow.value = VoiceInputEngine.VoiceState.LISTENING

        engine.cancelListening()

        assertEquals(VoiceInputEngine.VoiceState.IDLE, engine.state.value)
    }

    // ------------------------------------------------------------------
    // stopListening with no audio -> IDLE with empty result
    // ------------------------------------------------------------------

    @Test
    fun `stopListening with no captured audio returns empty and sets IDLE`() = runBlocking {
        val engine = VoiceInputEngine(context)

        // stopListening calls captureManager.stopCapture() which returns empty
        // when no recording was started. The engine should go to IDLE.
        val result = engine.stopListening()

        assertEquals(VoiceInputEngine.VoiceState.IDLE, engine.state.value)
        assertEquals("", result)
    }

    // ------------------------------------------------------------------
    // release -> state remains IDLE, no crash
    // ------------------------------------------------------------------

    @Test
    fun `release from IDLE does not crash`() {
        val engine = VoiceInputEngine(context)
        engine.release()
        assertEquals(VoiceInputEngine.VoiceState.IDLE, engine.state.value)
    }

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
