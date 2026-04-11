package dev.devkey.keyboard.compose

import android.view.inputmethod.EditorInfo
import dev.devkey.keyboard.Keyboard
import java.text.Normalizer

class DeadAccentSequence(
    onComposedText: (CharSequence) -> Unit,
    updateShiftState: (EditorInfo?) -> Unit,
    editorInfoProvider: () -> EditorInfo?
) : ComposeSequence(onComposedText, updateShiftState, editorInfoProvider) {

    override fun execute(code: Int): Boolean {
        val composed = executeToString(code)
        if (composed != null) {
            if (composed == "") {
                // Unrecognised - try to use the built-in Java text normalisation
                val c = composeBuffer.codePointAt(composeBuffer.length - 1)
                if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) {
                    val buildComposed = StringBuilder(10)
                    buildComposed.append(composeBuffer)
                    // FIXME? Put the combining character(s) temporarily at the end, else this won't work
                    val normalised = doNormalise(buildComposed.reverse().toString())
                    if (normalised == "") {
                        return true // incomplete :-)
                    }
                    clear()
                    onComposedText(normalised)
                    return false
                } else {
                    return true // there may be multiple combining accents
                }
            }

            clear()
            onComposedText(composed)
            return false
        }
        return true
    }

    companion object {
        private fun putAccent(nonSpacing: String, spacing: String, ascii: String?) {
            val effectiveAscii = ascii ?: spacing
            ComposeSequence.put("$nonSpacing ", effectiveAscii)
            ComposeSequence.put("$nonSpacing$nonSpacing", spacing)
            ComposeSequence.put("${Keyboard.DEAD_KEY_PLACEHOLDER}$nonSpacing", spacing)
        }

        fun getSpacing(nonSpacing: Char): String {
            val spacing = ComposeSequence.get("${Keyboard.DEAD_KEY_PLACEHOLDER}$nonSpacing")
            if (spacing != null) return spacing
            val normalized = normalize(" $nonSpacing")
            if (normalized != null) return normalized
            return nonSpacing.toString()
        }

        fun normalize(input: String): String? {
            val lookup = ComposeSequence.getMap()[input]
            return lookup ?: doNormalise(input)
        }

        private fun doNormalise(input: String): String {
            return Normalizer.normalize(input, Normalizer.Form.NFC)
        }

        init {
            // space + combining diacritical
            // cf. http://unicode.org/charts/PDF/U0300.pdf
            putAccent("\u0300", "\u02cb", "`")  // grave
            putAccent("\u0301", "\u02ca", "\u00b4")  // acute
            putAccent("\u0302", "\u02c6", "^")  // circumflex
            putAccent("\u0303", "\u02dc", "~")  // small tilde
            putAccent("\u0304", "\u02c9", "\u00af")  // macron
            putAccent("\u0305", "\u00af", "\u00af")  // overline
            putAccent("\u0306", "\u02d8", null)  // breve
            putAccent("\u0307", "\u02d9", null)  // dot above
            putAccent("\u0308", "\u00a8", "\u00a8")  // diaeresis
            putAccent("\u0309", "\u02c0", null)  // hook above
            putAccent("\u030a", "\u02da", "\u00b0")  // ring above
            putAccent("\u030b", "\u02dd", "\"")  // double acute
            putAccent("\u030c", "\u02c7", null)  // caron
            putAccent("\u030d", "\u02c8", null)  // vertical line above
            putAccent("\u030e", "\"", "\"")  // double vertical line above
            putAccent("\u0313", "\u02bc", null)  // comma above
            putAccent("\u0314", "\u02bd", null)  // reversed comma above

            ComposeSequence.put("\u0308\u0301\u03b9", "\u0390")  // Greek Dialytika+Tonos, iota
            ComposeSequence.put("\u0301\u0308\u03b9", "\u0390")  // Greek Dialytika+Tonos, iota
            ComposeSequence.put("\u0301\u03ca", "\u0390")        // Greek Dialytika+Tonos, iota
            ComposeSequence.put("\u0308\u0301\u03c5", "\u03b0")  // Greek Dialytika+Tonos, upsilon
            ComposeSequence.put("\u0301\u0308\u03c5", "\u03b0")  // Greek Dialytika+Tonos, upsilon
            ComposeSequence.put("\u0301\u03cb", "\u03b0")        // Greek Dialytika+Tonos, upsilon
        }
    }
}
