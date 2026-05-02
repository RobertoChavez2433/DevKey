package dev.devkey.keyboard.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize

@Composable
internal fun KeyTransientOverlay(
    popupCodes: List<Int>?,
    popupActiveIndex: Int,
    keySize: IntSize,
    density: Density,
    isPressed: Boolean,
    key: KeyData,
    displayLabel: String,
    longPressFired: Boolean
) {
    if (popupCodes != null) {
        LongPressPopup(
            codes = popupCodes,
            activeIndex = popupActiveIndex,
            keySize = keySize,
            density = density
        )
        return
    }

    if (isPressed && key.type == KeyType.LETTER && displayLabel.length <= 2) {
        KeyPressPreview(
            label = resolvePreviewLabel(key, displayLabel, longPressFired),
            keySize = keySize,
            density = density
        )
    }
}
