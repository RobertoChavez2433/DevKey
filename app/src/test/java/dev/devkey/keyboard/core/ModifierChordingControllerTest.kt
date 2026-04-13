package dev.devkey.keyboard.core

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import dev.devkey.keyboard.core.modifier.ChordeTracker
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.keyboard.model.Keyboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ModifierChordingControllerTest {

    private lateinit var settings: SettingsRepository
    private lateinit var shiftKeyState: ChordeTracker
    private lateinit var ctrlKeyState: ChordeTracker
    private lateinit var altKeyState: ChordeTracker
    private lateinit var metaKeyState: ChordeTracker

    private var modCtrl = false
    private var modAlt = false
    private var modMeta = false
    private var shiftState = Keyboard.SHIFT_OFF

    private var setModCtrlCalled = false
    private var setModAltCalled = false
    private var setModMetaCalled = false
    private var resetShiftCalled = false

    private var lastSetModCtrl: Boolean? = null
    private var lastSetModAlt: Boolean? = null
    private var lastSetModMeta: Boolean? = null

    data class CapturedKeyEvent(
        val ic: InputConnection?,
        val key: Int,
        val meta: Int,
        val isDown: Boolean
    )

    private val sentKeyEvents = mutableListOf<CapturedKeyEvent>()
    private lateinit var mockIc: InputConnection
    private lateinit var controller: ModifierChordingController

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        settings = SettingsRepository(prefs)

        shiftKeyState = ChordeTracker()
        ctrlKeyState = ChordeTracker()
        altKeyState = ChordeTracker()
        metaKeyState = ChordeTracker()

        modCtrl = false
        modAlt = false
        modMeta = false
        shiftState = Keyboard.SHIFT_OFF

        setModCtrlCalled = false
        setModAltCalled = false
        setModMetaCalled = false
        resetShiftCalled = false
        lastSetModCtrl = null
        lastSetModAlt = null
        lastSetModMeta = null
        sentKeyEvents.clear()

        mockIc = dev.devkey.keyboard.testutil.MockInputConnection()

        controller = ModifierChordingController(
            modCtrlProvider = { modCtrl },
            modAltProvider = { modAlt },
            modMetaProvider = { modMeta },
            shiftKeyStateProvider = { shiftKeyState },
            ctrlKeyStateProvider = { ctrlKeyState },
            altKeyStateProvider = { altKeyState },
            metaKeyStateProvider = { metaKeyState },
            setModCtrl = { v -> lastSetModCtrl = v; setModCtrlCalled = true; modCtrl = v },
            setModAlt = { v -> lastSetModAlt = v; setModAltCalled = true; modAlt = v },
            setModMeta = { v -> lastSetModMeta = v; setModMetaCalled = true; modMeta = v },
            resetShift = { resetShiftCalled = true },
            getShiftState = { shiftState },
            settings = settings,
            sendKeyDownFn = { ic, key, meta -> sentKeyEvents.add(CapturedKeyEvent(ic, key, meta, true)) },
            sendKeyUpFn = { ic, key, meta -> sentKeyEvents.add(CapturedKeyEvent(ic, key, meta, false)) },
            inputConnectionProvider = { mockIc }
        )
    }

    @Test
    fun `sendCtrlKey with delay 0 returns without sending`() {
        settings.chordingCtrlKey = 0
        controller.sendCtrlKey(mockIc, true, true)
        assertTrue("No events should be sent when chording delay is 0", sentKeyEvents.isEmpty())
    }

    @Test
    fun `sendCtrlKey with delay=keycode sends key`() {
        settings.chordingCtrlKey = KeyEvent.KEYCODE_CTRL_LEFT
        controller.sendCtrlKey(mockIc, true, true)
        assertTrue("Key event should be sent when chording key is set", sentKeyEvents.isNotEmpty())
        assertEquals(KeyEvent.KEYCODE_CTRL_LEFT, sentKeyEvents[0].key)
        assertTrue("Should be a key-down event", sentKeyEvents[0].isDown)
    }

    @Test
    fun `sendAltKey with delay 0 returns without sending`() {
        settings.chordingAltKey = 0
        controller.sendAltKey(mockIc, true, true)
        assertTrue("No events should be sent when chording delay is 0", sentKeyEvents.isEmpty())
    }

    @Test
    fun `sendMetaKey with delay 0 returns without sending`() {
        settings.chordingMetaKey = 0
        controller.sendMetaKey(mockIc, true, true)
        assertTrue("No events should be sent when chording delay is 0", sentKeyEvents.isEmpty())
    }

    @Test
    fun `sendModifierKeysDown Ctrl+Shift sends both DOWN`() {
        modCtrl = true
        settings.chordingCtrlKey = 0 // delay chording -> sendModifierKeysDown sends Ctrl when delay is true
        controller.sendModifierKeysDown(true, mockIc)

        val shiftDown = sentKeyEvents.any { it.key == KeyEvent.KEYCODE_SHIFT_LEFT && it.isDown }
        val ctrlDown = sentKeyEvents.any { it.key == KeyEvent.KEYCODE_CTRL_LEFT && it.isDown }
        assertTrue("Shift DOWN should be sent", shiftDown)
        assertTrue("Ctrl DOWN should be sent", ctrlDown)
    }

    @Test
    fun `sendModifierKeysDown no modifiers sends nothing`() {
        modCtrl = false
        modAlt = false
        modMeta = false
        controller.sendModifierKeysDown(false, mockIc)
        assertTrue("No events should be sent when no modifiers are active", sentKeyEvents.isEmpty())
    }

    @Test
    fun `handleModifierKeysUp resets Ctrl state`() {
        modCtrl = true
        // Not chording — ctrl key state is in RELEASING state by default
        controller.handleModifierKeysUp(false, true, mockIc)
        assertEquals("setModCtrl should be called with false", false, lastSetModCtrl)
    }

    @Test
    fun `handleModifierKeysUp shift-locked does not reset`() {
        shiftState = Keyboard.SHIFT_LOCKED
        controller.handleModifierKeysUp(true, true, mockIc)
        assertFalse("resetShift should NOT be called when shift is locked", resetShiftCalled)
    }
}
