package dev.devkey.keyboard.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import dev.devkey.keyboard.data.repository.SettingsRepository
import kotlinx.coroutines.launch

internal class DebugReceiverManager(private val context: Context) {
    private var enableDebugServerReceiver: BroadcastReceiver? = null
    private var setLayoutModeReceiver: BroadcastReceiver? = null
    private var setBoolPrefReceiver: BroadcastReceiver? = null
    private var setAutocorrectLevelReceiver: BroadcastReceiver? = null
    private var voiceProcessFileReceiver: BroadcastReceiver? = null
    private var clearLearnedWordsReceiver: BroadcastReceiver? = null
    private var resetCircuitBreakerReceiver: BroadcastReceiver? = null

    fun registerAll() {
        enableDebugServerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val url = intent.getStringExtra("url")
                if (url.isNullOrBlank()) {
                    DevKeyLogger.disableServer()
                    DevKeyLogger.ime("debug_server_disabled")
                } else {
                    DevKeyLogger.enableServer(url)
                    val scrubbed = try {
                        java.net.URI(url).let { "${it.scheme}://${it.host}:${it.port}" }
                    } catch (_: Exception) { "<invalid url>" }
                    DevKeyLogger.ime("debug_server_enabled", mapOf("url" to scrubbed))
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            enableDebugServerReceiver,
            IntentFilter("dev.devkey.keyboard.ENABLE_DEBUG_SERVER"),
            ContextCompat.RECEIVER_EXPORTED
        )

        setLayoutModeReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val mode = intent.getStringExtra("mode") ?: return
                if (mode !in setOf("full", "compact", "compact_dev")) {
                    DevKeyLogger.error("set_layout_mode_rejected", mapOf("mode" to mode))
                    return
                }
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit().putString(SettingsRepository.KEY_LAYOUT_MODE, mode).apply()
                DevKeyLogger.ime("layout_mode_set", mapOf("mode" to mode))
            }
        }
        ContextCompat.registerReceiver(
            context,
            setLayoutModeReceiver,
            IntentFilter("dev.devkey.keyboard.SET_LAYOUT_MODE"),
            ContextCompat.RECEIVER_EXPORTED
        )

        val allowedBoolKeys = setOf(
            SettingsRepository.KEY_SHOW_TOOLBAR,
            SettingsRepository.KEY_SHOW_NUMBER_ROW,
            SettingsRepository.KEY_AUTO_CAP,
            SettingsRepository.KEY_SHOW_SUGGESTIONS,
        )
        setBoolPrefReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val key = intent.getStringExtra("key") ?: return
                if (key !in allowedBoolKeys) {
                    DevKeyLogger.error("set_bool_pref_rejected", mapOf("key" to key))
                    return
                }
                val value = intent.getBooleanExtra("value", false)
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                prefs.edit().putBoolean(key, value).apply()
                DevKeyLogger.ime("bool_pref_set", mapOf("key" to key, "value" to value))
            }
        }
        ContextCompat.registerReceiver(
            context,
            setBoolPrefReceiver,
            IntentFilter("dev.devkey.keyboard.SET_BOOL_PREF"),
            ContextCompat.RECEIVER_EXPORTED
        )

        setAutocorrectLevelReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getStringExtra("level") ?: return
                if (level !in listOf("off", "mild", "aggressive")) {
                    DevKeyLogger.error("set_autocorrect_level_rejected", mapOf("level" to level))
                    return
                }
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit().putString(SettingsRepository.KEY_AUTOCORRECT_LEVEL, level).apply()
                DevKeyLogger.ime("autocorrect_level_set", mapOf("level" to level))
            }
        }
        ContextCompat.registerReceiver(
            context,
            setAutocorrectLevelReceiver,
            IntentFilter("dev.devkey.keyboard.SET_AUTOCORRECT_LEVEL"),
            ContextCompat.RECEIVER_EXPORTED
        )

        voiceProcessFileReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val filePath = intent.getStringExtra("file_path") ?: return
                val engine = dev.devkey.keyboard.ui.keyboard.SessionDependencies.voiceInputEngine
                if (engine == null) {
                    DevKeyLogger.error("voice_process_file_rejected", mapOf("reason" to "engine_null"))
                    return
                }
                DevKeyLogger.voice("process_file_start", mapOf("path" to filePath))
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    val result = engine.processFileForTest(filePath)
                    DevKeyLogger.voice("process_file_result", mapOf("length" to result.length, "preview" to result.take(100)))
                    // Commit the transcription to the current input field
                    dev.devkey.keyboard.LatinIME.sInstance?.currentInputConnection?.commitText(result, 1)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            voiceProcessFileReceiver,
            IntentFilter("dev.devkey.keyboard.VOICE_PROCESS_FILE"),
            ContextCompat.RECEIVER_EXPORTED
        )

        resetCircuitBreakerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                DevKeyLogger.resetCircuitBreaker()
            }
        }
        ContextCompat.registerReceiver(
            context,
            resetCircuitBreakerReceiver,
            IntentFilter("dev.devkey.keyboard.RESET_CIRCUIT_BREAKER"),
            ContextCompat.RECEIVER_EXPORTED
        )

        clearLearnedWordsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val engine = dev.devkey.keyboard.ui.keyboard.SessionDependencies.learningEngine
                if (engine == null) {
                    DevKeyLogger.error("clear_learned_words_rejected", mapOf("reason" to "engine_null"))
                    return
                }
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    engine.clearAll()
                    DevKeyLogger.ime("learned_words_cleared")
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            clearLearnedWordsReceiver,
            IntentFilter("dev.devkey.keyboard.CLEAR_LEARNED_WORDS"),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun unregisterAll() {
        listOf(
            enableDebugServerReceiver, setLayoutModeReceiver, setBoolPrefReceiver,
            setAutocorrectLevelReceiver, voiceProcessFileReceiver,
            clearLearnedWordsReceiver, resetCircuitBreakerReceiver
        ).forEach { r ->
            try { r?.let { context.unregisterReceiver(it) } } catch (_: IllegalArgumentException) {}
        }
        enableDebugServerReceiver = null
        setLayoutModeReceiver = null
        setBoolPrefReceiver = null
        setAutocorrectLevelReceiver = null
        voiceProcessFileReceiver = null
        clearLearnedWordsReceiver = null
        resetCircuitBreakerReceiver = null
    }
}
