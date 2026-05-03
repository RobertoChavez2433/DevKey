package dev.devkey.keyboard.testutil

import dev.devkey.keyboard.feature.smarttext.AnySoftKeyboardDictionary
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

fun loadAnySoftKeyboardDictionaryWithWords(
    vararg words: Pair<String, Int>,
): AnySoftKeyboardDictionary {
    val dictionary = AnySoftKeyboardDictionary()
    dictionary.load(ByteArrayInputStream(anySoftKeyboardWordlistBytes(*words)))
    return dictionary
}

fun anySoftKeyboardWordlistBytes(vararg words: Pair<String, Int>): ByteArray {
    val content = buildString {
        appendLine("dictionary=main:en,locale=en,description=English,date=1414726273,version=54")
        for ((word, frequency) in words) {
            appendLine(" word=$word,f=$frequency,flags=,originalFreq=$frequency")
        }
    }
    val output = ByteArrayOutputStream()
    GZIPOutputStream(output).use { it.write(content.toByteArray(Charsets.UTF_8)) }
    return output.toByteArray()
}
