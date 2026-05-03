package dev.devkey.keyboard.feature.voice

object VoiceLatencyPolicy {
    const val RELEASE_STOP_TO_COMMITTED_TARGET_MS = 1_000L
    const val POSTURE_RELEASE_QUALITY = "release_quality"
    const val POSTURE_OFFLINE_DELAYED = "offline_delayed"
    const val CURRENT_RELEASE_POSTURE = POSTURE_OFFLINE_DELAYED
    const val RUNTIME_EVALUATION_NEXT_STEP = "replace_full_window_tiny_with_streaming_subsecond_runtime"
    const val MIN_WARMUP_MEMORY_CLASS_MB = 192
    const val TFLITE_THREAD_COUNT = 4

    fun meetsReleaseTarget(durationMs: Long): Boolean =
        durationMs <= RELEASE_STOP_TO_COMMITTED_TARGET_MS

    fun stopToCommittedLogData(durationMs: Long): Map<String, Any?> {
        val releaseQuality = meetsReleaseTarget(durationMs)
        return mapOf(
            "target_ms" to RELEASE_STOP_TO_COMMITTED_TARGET_MS,
            "release_quality" to releaseQuality,
            "release_posture" to if (releaseQuality) {
                POSTURE_RELEASE_QUALITY
            } else {
                POSTURE_OFFLINE_DELAYED
            },
            "runtime_next_step" to if (releaseQuality) {
                null
            } else {
                RUNTIME_EVALUATION_NEXT_STEP
            },
        )
    }
}
