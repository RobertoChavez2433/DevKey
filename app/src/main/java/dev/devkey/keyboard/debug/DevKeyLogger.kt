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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
    // WHY: Limit parallelism so a slow/unreachable driver can never saturate the
    //      full Dispatchers.IO pool (64 threads by default on Android). If we let
    //      every log call grab an IO thread, a flurry of events with a stalled
    //      driver can queue ~100+ POSTs each holding a thread for the full socket
    //      timeout, starving the rest of the IME's background work and cascading
    //      into a main-thread ANR when some binder call waits for an IO-bound worker.
    //      limitedParallelism(2) caps us at 2 concurrent POSTs; the rest are queued
    //      in the coroutine dispatcher queue and run when the prior POSTs complete
    //      or time out.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(2))

    // WHY: Phase 3 gate ANR root cause — when the driver goes unreachable mid-run
    //      (reinstall, killed process, network hiccup) every log call fires an HTTP
    //      POST that times out after 1s. With ~50 events/sec during test runs the
    //      Dispatchers.IO pool saturates, backing up to the main thread via any
    //      blocking IPC and causing a 239-second binder ANR that crashes the IME.
    //      Fix: circuit-breaker — after 3 consecutive failures, suppress POSTs
    //      entirely for 5 seconds. Reset on the next successful POST.
    private val consecutiveFailures = AtomicInteger(0)
    private val circuitBreakerUntilMs = AtomicLong(0L)
    // Fail fast — a single failure trips the breaker for the cooldown window.
    // Any driver that is genuinely reachable will succeed on the first POST.
    private const val CIRCUIT_BREAKER_TRIP_COUNT = 1
    private const val CIRCUIT_BREAKER_COOLDOWN_MS = 10000L
    // Tight timeouts — the driver is on localhost via emulator NAT, so real
    // POSTs complete in <100ms. 300ms is plenty; anything slower is a stall.
    private const val HTTP_CONNECT_TIMEOUT_MS = 300
    private const val HTTP_READ_TIMEOUT_MS = 300

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
        // Circuit breaker — if the driver has failed N times in a row, suppress
        // POSTs for the cooldown window. Reads are cheap (single volatile + long
        // compare) so this runs unconditionally on the main thread.
        val now = System.currentTimeMillis()
        val breakerUntil = circuitBreakerUntilMs.get()
        if (now < breakerUntil) return

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
                conn.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
                conn.readTimeout = HTTP_READ_TIMEOUT_MS
                conn.outputStream.use { it.write(json.toString().toByteArray()) }
                conn.responseCode // trigger send
                conn.disconnect()
                // Success — reset the failure counter so the next failure starts fresh.
                consecutiveFailures.set(0)
            } catch (e: Exception) {
                val failures = consecutiveFailures.incrementAndGet()
                if (failures == CIRCUIT_BREAKER_TRIP_COUNT) {
                    // First trip — log once, then go silent for the cooldown window.
                    circuitBreakerUntilMs.set(
                        System.currentTimeMillis() + CIRCUIT_BREAKER_COOLDOWN_MS
                    )
                    Log.w(
                        "DevKey/SRV",
                        "Circuit breaker tripped after $failures consecutive failures: " +
                            "${e.javaClass.simpleName}: ${e.message}. " +
                            "Suppressing server forwarding for ${CIRCUIT_BREAKER_COOLDOWN_MS}ms."
                    )
                }
                // Below the trip count, stay completely silent — Phase 3 gate ANR
                // root cause was a Log.w per failed POST flooding the system log.
            }
        }
    }
}
