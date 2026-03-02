package dev.devkey.keyboard.core

import android.util.Log

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
