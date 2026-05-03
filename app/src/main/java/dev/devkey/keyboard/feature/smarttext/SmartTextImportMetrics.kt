package dev.devkey.keyboard.feature.smarttext

/**
 * Privacy-safe measurements for the imported smart-text dictionary path.
 *
 * The payload deliberately records only structural artifact and performance
 * data. It must never include typed text, dictionary words, transcripts, or
 * clipboard contents.
 */
data class SmartTextImportMetrics(
    val source: String,
    val artifact: String,
    val locale: String,
    val version: Int,
    val wordCount: Int,
    val artifactBytes: Long,
    val loadDurationMs: Long,
    val memoryDeltaKb: Long,
    val loadedAtUptimeMs: Long,
) {
    fun toLogData(): Map<String, Any> = mapOf(
        "source" to source,
        "artifact" to artifact,
        "locale" to locale,
        "version" to version,
        "word_count" to wordCount,
        "artifact_bytes" to artifactBytes,
        "load_duration_ms" to loadDurationMs,
        "memory_delta_kb" to memoryDeltaKb,
        "loaded_at_uptime_ms" to loadedAtUptimeMs,
    )
}
