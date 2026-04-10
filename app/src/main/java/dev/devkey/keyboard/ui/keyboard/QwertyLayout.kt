package dev.devkey.keyboard.ui.keyboard

/**
 * Defines the QWERTY keyboard layouts for DevKey.
 *
 * Supports three layout modes:
 * - FULL: 6-row layout with number row, QWERTY, home, Z, space, and utility rows
 * - COMPACT: 4-row layout with digit long-press + accented vowel popups (option A)
 * - COMPACT_DEV: 4-row layout with hacker shift-symbol long-press
 *
 * Uses keycodes from [Keyboard] for standard keys, and [KeyCodes] for special keys.
 *
 * Long-press data added per plan Phase 5.2 + spec §4.2.
 * Templates are the default; SwiftKey reference screenshots for pixel-match
 * tuning land in Phase 6 (see .claude/test-flows/swiftkey-reference/).
 * Phase 5 decision: option A — COMPACT letter keys DO get long-press.
 *
 * Layout builders live in:
 *  - QwertyLayoutFull.kt    (buildFullLayout, buildFullHomeRow)
 *  - QwertyLayoutCompact.kt (buildCompactLayout, buildCompactDevLayout)
 *  - QwertyLayoutSharedRows.kt (buildSpaceRow, DPAD keycode constants)
 */
object QwertyLayout {

    private val fullLayout: KeyboardLayoutData by lazy { buildFullLayout() }
    private val compactLayout: KeyboardLayoutData by lazy { buildCompactLayout() }
    private val compactLayoutNoNumbers: KeyboardLayoutData by lazy {
        // Drop the first row (number row) from the full compact layout.
        KeyboardLayoutData(rows = compactLayout.rows.drop(1))
    }
    private val compactDevLayout: KeyboardLayoutData by lazy { buildCompactDevLayout() }
    private val compactDevLayoutWithNumbers: KeyboardLayoutData by lazy {
        // Prepend the compact layout's number row onto compact_dev.
        KeyboardLayoutData(rows = listOf(compactLayout.rows.first()) + compactDevLayout.rows)
    }

    /**
     * Returns the appropriate layout for the given [LayoutMode].
     *
     * @param includeNumberRow When true, COMPACT and COMPACT_DEV layouts include the
     *        `1 2 3 4 5 6 7 8 9 0` number row at the top. FULL always has its own
     *        number row regardless. Controlled by `SettingsRepository.KEY_SHOW_NUMBER_ROW`.
     */
    fun getLayout(mode: LayoutMode, includeNumberRow: Boolean = true): KeyboardLayoutData = when (mode) {
        LayoutMode.FULL -> fullLayout
        LayoutMode.COMPACT -> if (includeNumberRow) compactLayout else compactLayoutNoNumbers
        LayoutMode.COMPACT_DEV -> if (includeNumberRow) compactDevLayoutWithNumbers else compactDevLayout
    }
}
