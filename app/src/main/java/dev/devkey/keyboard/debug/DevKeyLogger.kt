// WHY: Structured logging with categories and hypothesis tagging for systematic debugging.
// Session-scoped hypothesis markers (H001, H002...) are temporary investigation tools
// that MUST be removed after each debug session (Phase 9 cleanup gate).
package dev.devkey.keyboard.debug

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * IMPORTANT: NEVER log text content typed by the user. This IME sees ALL keystrokes
 * including passwords, PII, and private messages. The text()/ime()/log() APIs
 * exist for structural logging (state, mode, lifecycle events) — never for content.
 * See .claude/skills/systematic-debugging/references/log-investigation-and-instrumentation.md
 */
object DevKeyLogger {

    enum class Category(val tag: String) {
        IME_LIFECYCLE("DevKey/IME"),
        COMPOSE_UI("DevKey/UI"),
        MODIFIER_STATE("DevKey/MOD"),
        TEXT_INPUT("DevKey/TXT"),
        NATIVE_JNI("DevKey/NDK"),
        VOICE("DevKey/VOX"),
        BUILD_TEST("DevKey/BLD"),
        ERROR("DevKey/ERR"),
    }

    @Volatile private var serverUrl: String? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Enable HTTP server forwarding for Deep debug mode. */
    fun enableServer(url: String) {
        serverUrl = url
    }

    /** Disable HTTP server forwarding. */
    fun disableServer() {
        serverUrl = null
    }

    // Category convenience methods
    fun ime(message: String, data: Map<String, Any?> = emptyMap()) =
        log(Category.IME_LIFECYCLE, message, data)

    fun ui(message: String, data: Map<String, Any?> = emptyMap()) =
        log(Category.COMPOSE_UI, message, data)

    fun modifier(message: String, data: Map<String, Any?> = emptyMap()) =
        log(Category.MODIFIER_STATE, message, data)

    /** WARNING: log structural state only — NEVER log typed text, passwords, or PII. */
    fun text(message: String, data: Map<String, Any?> = emptyMap()) =
        log(Category.TEXT_INPUT, message, data)

    fun native(message: String, data: Map<String, Any?> = emptyMap()) =
        log(Category.NATIVE_JNI, message, data)

    fun voice(message: String, data: Map<String, Any?> = emptyMap()) =
        log(Category.VOICE, message, data)

    fun error(message: String, data: Map<String, Any?> = emptyMap()) =
        log(Category.ERROR, message, data)

    /**
     * Session-scoped hypothesis marker. MUST be removed after debug session.
     * Grep "hypothesis(" to find all markers for cleanup.
     */
    fun hypothesis(id: String, category: Category, description: String, data: Map<String, Any?> = emptyMap()) {
        val tag = "DevKey/$id"
        val fullMessage = "[${category.tag}] $description"
        Log.d(tag, formatMessage(fullMessage, data))
        sendToServer(category.tag, fullMessage, data, hypothesisId = id)
    }

    private fun log(category: Category, message: String, data: Map<String, Any?>) {
        Log.d(category.tag, formatMessage(message, data))
        sendToServer(category.tag, message, data)
    }

    private fun formatMessage(message: String, data: Map<String, Any?>): String {
        if (data.isEmpty()) return message
        val dataStr = data.entries.joinToString(", ") { "${it.key}=${it.value}" }
        return "$message | $dataStr"
    }

    private fun sendToServer(
        category: String,
        message: String,
        data: Map<String, Any?>,
        hypothesisId: String? = null
    ) {
        val url = serverUrl ?: return
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("category", category)
                    put("message", message)
                    put("data", JSONObject(data.mapValues { it.value?.toString() }))
                    hypothesisId?.let { put("hypothesis", it) }
                }
                val conn = URL("$url/log").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 1000
                conn.readTimeout = 1000
                conn.outputStream.use { it.write(json.toString().toByteArray()) }
                conn.responseCode // trigger send
                conn.disconnect()
            } catch (_: Exception) {
                // Silent fail — debug logging must never crash the IME
            }
        }
    }
}
