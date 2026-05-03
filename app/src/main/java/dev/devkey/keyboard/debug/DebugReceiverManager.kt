package dev.devkey.keyboard.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Base64
import androidx.core.content.ContextCompat
import dev.devkey.keyboard.BuildConfig
import dev.devkey.keyboard.data.db.DevKeyDatabase
import dev.devkey.keyboard.data.repository.SettingsRepository
import dev.devkey.keyboard.feature.smarttext.SmartTextCorrectionLevel
import dev.devkey.keyboard.feature.voice.VoiceLatencyPolicy
import dev.devkey.keyboard.ui.keyboard.SessionDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class DebugReceiverManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository
) {
    private var enableDebugServerReceiver: BroadcastReceiver? = null
    private var setLayoutModeReceiver: BroadcastReceiver? = null
    private var setBoolPrefReceiver: BroadcastReceiver? = null
    private var setStringPrefReceiver: BroadcastReceiver? = null
    private var setAutocorrectLevelReceiver: BroadcastReceiver? = null
    private var voiceProcessFileReceiver: BroadcastReceiver? = null
    private var smartTextMetricsReceiver: BroadcastReceiver? = null
    private var clearLearnedWordsReceiver: BroadcastReceiver? = null
    private var assertLearnedWordReceiver: BroadcastReceiver? = null
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
                settingsRepository.setString(SettingsRepository.KEY_LAYOUT_MODE, mode)
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
                settingsRepository.setBoolean(key, value)
                DevKeyLogger.ime("bool_pref_set", mapOf("key" to key, "value" to value))
            }
        }
        ContextCompat.registerReceiver(
            context,
            setBoolPrefReceiver,
            IntentFilter("dev.devkey.keyboard.SET_BOOL_PREF"),
            ContextCompat.RECEIVER_EXPORTED
        )

        val allowedStringKeys = setOf(
            SettingsRepository.KEY_CTRL_A_OVERRIDE,
            SettingsRepository.KEY_CHORDING_CTRL,
            SettingsRepository.KEY_CHORDING_ALT,
            SettingsRepository.KEY_CHORDING_META,
        )
        setStringPrefReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val key = intent.getStringExtra("key") ?: return
                if (key !in allowedStringKeys) {
                    DevKeyLogger.error("set_string_pref_rejected", mapOf("key" to key))
                    return
                }
                val value = intent.getStringExtra("value") ?: return
                settingsRepository.setString(key, value)
                DevKeyLogger.ime("string_pref_set", mapOf("key" to key, "value" to value))
            }
        }
        ContextCompat.registerReceiver(
            context,
            setStringPrefReceiver,
            IntentFilter("dev.devkey.keyboard.SET_STRING_PREF"),
            ContextCompat.RECEIVER_EXPORTED
        )

        setAutocorrectLevelReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getStringExtra("level") ?: return
                if (level !in listOf("off", "mild", "aggressive")) {
                    DevKeyLogger.error("set_autocorrect_level_rejected", mapOf("level" to level))
                    return
                }
                SessionDependencies.smartTextCorrectionLevel = parseSmartTextCorrectionLevel(level)
                settingsRepository.setString(SettingsRepository.KEY_AUTOCORRECT_LEVEL, level)
                DevKeyLogger.ime("autocorrect_level_set", mapOf("level" to level))
            }
        }
        ContextCompat.registerReceiver(
            context,
            setAutocorrectLevelReceiver,
            IntentFilter("dev.devkey.keyboard.SET_AUTOCORRECT_LEVEL"),
            ContextCompat.RECEIVER_EXPORTED
        )

        if (BuildConfig.DEBUG) {
            smartTextMetricsReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val metrics = SessionDependencies.smartTextImportMetrics
                    if (metrics == null) {
                        DevKeyLogger.error(
                            "smart_text_import_metrics_unavailable",
                            mapOf("reason" to "dictionary_not_ready")
                        )
                        return
                    }
                    DevKeyLogger.ime("smart_text_import_metrics", metrics.toLogData())
                }
            }
            ContextCompat.registerReceiver(
                context,
                smartTextMetricsReceiver,
                IntentFilter("dev.devkey.keyboard.debug.DUMP_SMART_TEXT_IMPORT_METRICS"),
                ContextCompat.RECEIVER_EXPORTED
            )

            voiceProcessFileReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val filePath = intent.getStringExtra("file_path") ?: return
                    val engine = SessionDependencies.voiceInputEngine
                    if (engine == null) {
                        DevKeyLogger.error("voice_process_file_rejected", mapOf("reason" to "engine_null"))
                        return
                    }
                    DevKeyLogger.voice("process_file_start")
                    scope.launch(Dispatchers.Main) {
                        val startedAt = android.os.SystemClock.elapsedRealtime()
                        val result = engine.processFileForTest(filePath)
                        val committed = engine.commitTranscriptionForTest(result)
                        val durationMs = android.os.SystemClock.elapsedRealtime() - startedAt
                        val releaseGate = VoiceLatencyPolicy.stopToCommittedLogData(durationMs)
                        DevKeyLogger.voice(
                            "latency",
                            mapOf(
                                "phase" to "process_file_to_committed",
                                "duration_ms" to durationMs
                            ) + releaseGate
                        )
                        DevKeyLogger.voice(
                            "process_file_result",
                            mapOf(
                                "length" to result.length,
                                "committed" to committed
                            ) + releaseGate + mapOf("duration_ms" to durationMs)
                        )
                    }
                }
            }
            ContextCompat.registerReceiver(
                context,
                voiceProcessFileReceiver,
                IntentFilter("dev.devkey.keyboard.VOICE_PROCESS_FILE"),
                ContextCompat.RECEIVER_EXPORTED
            )
        }

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
                val engine = SessionDependencies.learningEngine
                val requestId = intent.getStringExtra("request_id") ?: ""
                if (engine == null) {
                    DevKeyLogger.error(
                        "clear_learned_words_rejected",
                        mapOf("reason" to "engine_null", "request_id" to requestId)
                    )
                    return
                }
                scope.launch(Dispatchers.IO) {
                    engine.clearAll()
                    DevKeyLogger.ime("learned_words_cleared", mapOf("request_id" to requestId))
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            clearLearnedWordsReceiver,
            IntentFilter("dev.devkey.keyboard.CLEAR_LEARNED_WORDS"),
            ContextCompat.RECEIVER_EXPORTED
        )

        if (BuildConfig.DEBUG) {
            assertLearnedWordReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val requestId = intent.getStringExtra("request_id") ?: ""
                    val expected = decodeExpectedWord(intent)
                    val minFrequency = intent.getIntExtra("min_frequency", 1)
                    if (expected == null) {
                        logLearnedWordAssertion(
                            ok = false,
                            wordLength = -1,
                            frequency = 0,
                            minFrequency = minFrequency,
                            isCommand = false,
                            isUserAdded = false,
                            requestId = requestId
                        )
                        return
                    }
                    scope.launch(Dispatchers.IO) {
                        val entity = DevKeyDatabase
                            .getInstance(context)
                            .learnedWordDao()
                            .findWordAny(expected)
                        logLearnedWordAssertion(
                            ok = entity != null && entity.frequency >= minFrequency,
                            wordLength = expected.length,
                            frequency = entity?.frequency ?: 0,
                            minFrequency = minFrequency,
                            isCommand = entity?.isCommand ?: false,
                            isUserAdded = entity?.isUserAdded ?: false,
                            requestId = requestId
                        )
                    }
                }
            }
            ContextCompat.registerReceiver(
                context,
                assertLearnedWordReceiver,
                IntentFilter("dev.devkey.keyboard.debug.ASSERT_LEARNED_WORD"),
                ContextCompat.RECEIVER_EXPORTED
            )
        }
    }

    fun unregisterAll() {
        listOf(
            enableDebugServerReceiver, setLayoutModeReceiver, setBoolPrefReceiver,
            setStringPrefReceiver, setAutocorrectLevelReceiver, voiceProcessFileReceiver,
            smartTextMetricsReceiver, clearLearnedWordsReceiver, assertLearnedWordReceiver,
            resetCircuitBreakerReceiver
        ).forEach { r ->
            try { r?.let { context.unregisterReceiver(it) } } catch (_: IllegalArgumentException) {}
        }
        enableDebugServerReceiver = null
        setLayoutModeReceiver = null
        setBoolPrefReceiver = null
        setStringPrefReceiver = null
        setAutocorrectLevelReceiver = null
        voiceProcessFileReceiver = null
        smartTextMetricsReceiver = null
        clearLearnedWordsReceiver = null
        assertLearnedWordReceiver = null
        resetCircuitBreakerReceiver = null
    }

    private fun decodeExpectedWord(intent: Intent): String? {
        val encoded = intent.getStringExtra("word_base64") ?: return null
        return try {
            String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun logLearnedWordAssertion(
        ok: Boolean,
        wordLength: Int,
        frequency: Int,
        minFrequency: Int,
        isCommand: Boolean,
        isUserAdded: Boolean,
        requestId: String
    ) {
        DevKeyLogger.ime(
            "learned_word_asserted",
            mapOf(
                "ok" to ok,
                "word_length" to wordLength,
                "frequency" to frequency,
                "min_frequency" to minFrequency,
                "is_command" to isCommand,
                "is_user_added" to isUserAdded,
                "request_id" to requestId
            )
        )
    }

    private fun parseSmartTextCorrectionLevel(level: String): SmartTextCorrectionLevel =
        when (level) {
            "aggressive" -> SmartTextCorrectionLevel.AGGRESSIVE
            "off" -> SmartTextCorrectionLevel.OFF
            else -> SmartTextCorrectionLevel.MILD
        }
}
