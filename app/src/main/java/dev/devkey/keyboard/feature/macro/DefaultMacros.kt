package dev.devkey.keyboard.feature.macro

import dev.devkey.keyboard.data.db.entity.MacroEntity

/**
 * Provides the default set of macros pre-populated on first database creation.
 */
object DefaultMacros {

    /**
     * Returns the 7 default macros for common keyboard shortcuts.
     */
    fun getDefaults(): List<MacroEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            MacroEntity(
                name = "Copy",
                keySequence = """[{"key":"c","keyCode":99,"modifiers":["ctrl"]}]""",
                createdAt = now,
                usageCount = 0
            ),
            MacroEntity(
                name = "Paste",
                keySequence = """[{"key":"v","keyCode":118,"modifiers":["ctrl"]}]""",
                createdAt = now,
                usageCount = 0
            ),
            MacroEntity(
                name = "Cut",
                keySequence = """[{"key":"x","keyCode":120,"modifiers":["ctrl"]}]""",
                createdAt = now,
                usageCount = 0
            ),
            MacroEntity(
                name = "Undo",
                keySequence = """[{"key":"z","keyCode":122,"modifiers":["ctrl"]}]""",
                createdAt = now,
                usageCount = 0
            ),
            MacroEntity(
                name = "Select All",
                keySequence = """[{"key":"a","keyCode":97,"modifiers":["ctrl"]}]""",
                createdAt = now,
                usageCount = 0
            ),
            MacroEntity(
                name = "Save",
                keySequence = """[{"key":"s","keyCode":115,"modifiers":["ctrl"]}]""",
                createdAt = now,
                usageCount = 0
            ),
            MacroEntity(
                name = "Find",
                keySequence = """[{"key":"f","keyCode":102,"modifiers":["ctrl"]}]""",
                createdAt = now,
                usageCount = 0
            )
        )
    }
}
