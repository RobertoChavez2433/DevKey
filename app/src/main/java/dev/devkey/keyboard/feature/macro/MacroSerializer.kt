package dev.devkey.keyboard.feature.macro

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes and deserializes macro steps to/from JSON strings for Room storage.
 *
 * JSON format: [{"key": "a", "keyCode": 97, "modifiers": ["ctrl"]}]
 */
object MacroSerializer {

    /**
     * Converts a list of macro steps to a JSON string.
     */
    fun serialize(steps: List<MacroStep>): String {
        val array = JSONArray()
        for (step in steps) {
            val obj = JSONObject()
            obj.put("key", step.key)
            obj.put("keyCode", step.keyCode)
            val modArray = JSONArray()
            for (mod in step.modifiers) {
                modArray.put(mod)
            }
            obj.put("modifiers", modArray)
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Parses a JSON string back to a list of macro steps.
     * Returns an empty list on invalid JSON (graceful fallback).
     */
    fun deserialize(json: String): List<MacroStep> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val steps = mutableListOf<MacroStep>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val key = obj.optString("key", "")
                val keyCode = obj.optInt("keyCode", 0)
                val modArray = obj.optJSONArray("modifiers")
                val modifiers = mutableListOf<String>()
                if (modArray != null) {
                    for (j in 0 until modArray.length()) {
                        modifiers.add(modArray.getString(j))
                    }
                }
                steps.add(MacroStep(key, keyCode, modifiers))
            }
            steps
        } catch (e: Exception) {
            emptyList()
        }
    }
}
