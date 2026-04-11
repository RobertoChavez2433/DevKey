package dev.devkey.keyboard.keyboard.switcher
import dev.devkey.keyboard.LanguageSwitcher
import dev.devkey.keyboard.LatinIME
import dev.devkey.keyboard.R
import dev.devkey.keyboard.keyboard.latin.LatinKeyboard
import dev.devkey.keyboard.keyboard.model.KeyboardId

import java.lang.ref.SoftReference

internal class KeyboardCache(
    private val imeProvider: () -> LatinIME,
    private val languageSwitcherProvider: () -> LanguageSwitcher?,
    private val isAutoCompletionActiveProvider: () -> Boolean,
    private val hasVoiceButtonFn: (Boolean) -> Boolean,
    private val hasVoiceProvider: () -> Boolean
) {
    private val keyboards = HashMap<KeyboardId, SoftReference<LatinKeyboard>>()
    private var lastDisplayWidth = 0

    fun get(id: KeyboardId): LatinKeyboard {
        val ref = keyboards[id]
        var keyboard = ref?.get()
        if (keyboard == null) {
            val ims = imeProvider()
            val orig = ims.resources
            val conf = orig.configuration
            val saveLocale = conf.locale
            conf.locale = LatinIME.sKeyboardSettings.inputLocale
            orig.updateConfiguration(conf, null)
            keyboard = LatinKeyboard(ims, id.mXml, id.mKeyboardMode, id.mKeyboardHeightPercent)
            keyboard.setVoiceMode(hasVoiceButtonFn(id.mXml == R.xml.kbd_symbols), hasVoiceProvider())
            keyboard.setLanguageSwitcher(languageSwitcherProvider(), isAutoCompletionActiveProvider())
            if (id.mEnableShiftLock) keyboard.enableShiftLock()
            keyboards[id] = SoftReference(keyboard)
            conf.locale = saveLocale
            orig.updateConfiguration(conf, null)
        }
        return keyboard
    }

    fun clear() = keyboards.clear()

    fun refreshIfNeeded(forceCreate: Boolean, displayWidth: Int): Boolean {
        if (forceCreate) keyboards.clear()
        if (displayWidth == lastDisplayWidth) return false
        lastDisplayWidth = displayWidth
        if (!forceCreate) keyboards.clear()
        return true
    }
}
