package dev.devkey.keyboard.core

import android.content.Context
import android.view.KeyEvent
import dev.devkey.keyboard.core.modifier.ChordeTracker
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.testutil.MockInputConnection
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
class ModifiableKeyDispatcherTest {

    private lateinit var settings: SettingsRepository
    private lateinit var mockIc: MockInputConnection
    private lateinit var altKeyState: ChordeTracker

    private var modCtrl = false
    private var modAlt = false
    private var modMeta = false
    private var shiftMod = false

    private var setModAltCalled = false
    private var ctrlAToastCalled = false
    private var lastSendKeyChar: Char? = null

    private val sendModifiedKeyDownUpCalls = mutableListOf<Pair<Int, Boolean>>()
    private var sendModifierKeysDownCalls = 0
    private var sendModifierKeysUpCalls = 0
    private var handleModifierKeysUpCalls = 0

    private lateinit var dispatcher: ModifiableKeyDispatcher

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        settings = SettingsRepository(prefs)

        mockIc = MockInputConnection()
        altKeyState = ChordeTracker()

        modCtrl = false
        modAlt = false
        modMeta = false
        shiftMod = false

        setModAltCalled = false
        ctrlAToastCalled = false
        lastSendKeyChar = null
        sendModifiedKeyDownUpCalls.clear()
        sendModifierKeysDownCalls = 0
        sendModifierKeysUpCalls = 0
        handleModifierKeysUpCalls = 0

        dispatcher = ModifiableKeyDispatcher(
            inputConnectionProvider = { mockIc },
            modCtrlProvider = { modCtrl },
            modAltProvider = { modAlt },
            modMetaProvider = { modMeta },
            shiftModProvider = { shiftMod },
            altKeyStateProvider = { altKeyState },
            setModAlt = { v -> setModAltCalled = true; modAlt = v },
            settings = settings,
            ctrlAToastAction = { ctrlAToastCalled = true },
            sendModifiedKeyDownUp = { code, shifted -> sendModifiedKeyDownUpCalls.add(code to shifted) },
            sendModifierKeysDown = { _ -> sendModifierKeysDownCalls++ },
            sendModifierKeysUp = { _ -> sendModifierKeysUpCalls++ },
            handleModifierKeysUp = { _, _ -> handleModifierKeysUpCalls++ },
            sendKeyCharFn = { ch -> lastSendKeyChar = ch }
        )
    }

    @Test
    fun `plain letter with shift commits text`() {
        shiftMod = true
        dispatcher.dispatch('a')
        // With only shift and a letter, commitText is called on IC
        assertEquals("commitText should be called once", 1, mockIc.commitTextCount)
    }

    @Test
    fun `Ctrl+letter sends modified key`() {
        modCtrl = true
        dispatcher.dispatch('c')
        assertEquals("sendModifiedKeyDownUp should be called", 1, sendModifiedKeyDownUpCalls.size)
        assertEquals(KeyEvent.KEYCODE_C, sendModifiedKeyDownUpCalls[0].first)
    }

    @Test
    fun `Ctrl+A override=0 no Alt shows toast`() {
        modCtrl = true
        settings.ctrlAOverride = 0
        dispatcher.dispatch('a')
        assertTrue("ctrlAToast should be shown", ctrlAToastCalled)
    }

    @Test
    fun `Ctrl+A override=0 Alt active sends Ctrl+A`() {
        modCtrl = true
        modAlt = true
        settings.ctrlAOverride = 0
        dispatcher.dispatch('a')
        assertFalse("ctrlAToast should NOT be shown when Alt is active", ctrlAToastCalled)
        assertEquals("sendModifiedKeyDownUp should be called", 1, sendModifiedKeyDownUpCalls.size)
    }

    @Test
    fun `Ctrl+A override=1 silently ignores`() {
        modCtrl = true
        settings.ctrlAOverride = 1
        dispatcher.dispatch('a')
        assertFalse("ctrlAToast should NOT be shown", ctrlAToastCalled)
        assertTrue("sendModifiedKeyDownUp should NOT be called", sendModifiedKeyDownUpCalls.isEmpty())
    }

    @Test
    fun `Ctrl+A override=2 sends standard Ctrl+A`() {
        modCtrl = true
        settings.ctrlAOverride = 2
        dispatcher.dispatch('a')
        assertEquals("sendModifiedKeyDownUp should be called", 1, sendModifiedKeyDownUpCalls.size)
        assertEquals(KeyEvent.KEYCODE_A, sendModifiedKeyDownUpCalls[0].first)
    }

    @Test
    fun `digit with modifier clears meta then sends char`() {
        // No modifiers active, just a digit — exercises the clearMetaKeyStates path
        dispatcher.dispatch('5')
        assertEquals("sendKeyChar should be called for digit", '5', lastSendKeyChar)
    }

    @Test
    fun `non-ASCII falls through to sendKeyChar`() {
        val ch = 200.toChar()
        dispatcher.dispatch(ch)
        assertEquals("sendKeyChar should be called for non-ASCII", ch, lastSendKeyChar)
    }
}
