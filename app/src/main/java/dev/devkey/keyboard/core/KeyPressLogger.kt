package dev.devkey.keyboard.core

import android.util.Log
import dev.devkey.keyboard.debug.DevKeyLogger

object KeyPressLogger {
    private const val TAG = "DevKeyPress"

    fun logKeyDown(label: String, code: Int, type: String) {
        Log.d(TAG, "DOWN  label=$label code=$code type=$type")
    }

    fun logKeyTap(label: String, code: Int) {
        Log.d(TAG, "TAP   label=$label code=$code")
    }

    fun logLongPress(label: String, code: Int, longPressCode: Int?) {
        Log.d(TAG, "LONG  label=$label code=$code lpCode=$longPressCode")
        // WHY: Phase 2.3 long-press coverage flow gates on this signal instead of a post-swipe sleep.
        // FROM SPEC: §6 Phase 2 item 2.3 — "Long-press popup coverage (every key in every mode)"
        // IMPORTANT: No content leakage — label is the primary key identity, lp_code is the long-press output code.
        //            These are non-secret UI structure values, safe to log.
        DevKeyLogger.text(
            "long_press_fired",
            mapOf(
                "label" to label,
                "code" to code,
                "lp_code" to longPressCode
            )
        )
    }

    fun logRepeat(label: String, code: Int, count: Int) {
        Log.d(TAG, "RPT   label=$label code=$code count=$count")
    }

    fun logKeyUp(label: String, code: Int) {
        Log.d(TAG, "UP    label=$label code=$code")
    }

    fun logBridgeOnKey(rawCode: Int, effectiveCode: Int, shiftActive: Boolean, ctrlActive: Boolean, altActive: Boolean) {
        Log.d(TAG, "BRIDGE raw=$rawCode eff=$effectiveCode shift=$shiftActive ctrl=$ctrlActive alt=$altActive")
    }

    fun logModifierTransition(type: String, from: String, to: String) {
        Log.d(TAG, "MOD   $type: $from -> $to")
    }
}
