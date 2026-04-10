package dev.devkey.keyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.devkey.keyboard.ui.theme.DevKeyThemeColors
import dev.devkey.keyboard.ui.theme.DevKeyThemeDimensions
import dev.devkey.keyboard.ui.theme.DevKeyThemeTypography

/**
 * Renders the multi-char long-press candidate popup anchored above the key.
 *
 * Displayed as a horizontal Row of candidate cells. The cell at [activeIndex]
 * is highlighted using [DevKeyThemeColors.popupSelectedBg]. All other cells use
 * [DevKeyThemeColors.popupBg].
 *
 * The popup is offset upward by its own estimated height plus a small gap
 * so it appears above the key surface.
 */
@Composable
internal fun LongPressPopup(
    codes: List<Int>,
    activeIndex: Int,
    keySize: IntSize,
    density: Density
) {
    val cellWidthDp = DevKeyThemeDimensions.popupCellPadH * 2 + DevKeyThemeDimensions.popupCellGlyphWidth
    val popupHeightDp = DevKeyThemeDimensions.popupCellPadV * 2 + DevKeyThemeDimensions.popupCellGlyphHeight
    val gapDp = DevKeyThemeDimensions.popupAnchorGap

    val totalPopupWidthPx = with(density) { (cellWidthDp * codes.size).roundToPx() }
    val keyWidthPx = keySize.width
    val xOffsetPx = (keyWidthPx - totalPopupWidthPx) / 2
    val yOffsetPx = with(density) { -(popupHeightDp + gapDp).roundToPx() }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(xOffsetPx, yOffsetPx),
        properties = PopupProperties(focusable = false)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(DevKeyThemeDimensions.popupRadius))
                .background(DevKeyThemeColors.popupBg)
        ) {
            codes.forEachIndexed { index, code ->
                val isSelected = index == activeIndex
                val bgColor = if (isSelected) DevKeyThemeColors.popupSelectedBg else Color.Transparent
                val textColor = if (isSelected) DevKeyThemeColors.popupSelectedText else DevKeyThemeColors.popupCandidateText
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(DevKeyThemeDimensions.popupRadius))
                        .background(bgColor)
                        .padding(
                            horizontal = DevKeyThemeDimensions.popupCellPadH,
                            vertical = DevKeyThemeDimensions.popupCellPadV
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val label = if (code > 0) code.toChar().toString() else "?"
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = DevKeyThemeTypography.fontPopupCandidate
                    )
                }
            }
        }
    }
}
