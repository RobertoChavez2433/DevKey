package dev.devkey.keyboard.feature.voice

import android.content.Context
import android.util.Log

/**
 * Preprocesses raw PCM audio into the format expected by the Whisper TF Lite model.
 *
 * Whisper expects 30 seconds of audio at 16kHz (480,000 samples), converted to
 * an 80-bin mel spectrogram. This processor handles padding/trimming and
 * mel spectrogram computation.
 *
 * Note: Full mel spectrogram computation is complex. This implementation provides
 * a simplified approach that normalizes and pads raw audio. When the actual model
 * is integrated, this should be updated to match the model's expected input format,
 * referencing the Java implementation from whisper_android.
 *
 * @param context Android context for loading assets.
 */
class WhisperProcessor(private val context: Context) {

    companion object {
        private const val TAG = "DevKey/WhisperProcessor"

        /** Whisper's expected sample rate. */
        const val SAMPLE_RATE = 16000

        /** Whisper processes 30-second chunks. */
        const val CHUNK_LENGTH_SECONDS = 30

        /** Total samples for a 30-second chunk at 16kHz. */
        const val EXPECTED_SAMPLES = SAMPLE_RATE * CHUNK_LENGTH_SECONDS // 480,000

        /** Number of mel frequency bins. */
        const val N_MELS = 80

        /** Number of frames in the mel spectrogram. */
        const val N_FRAMES = 3000
    }

    /** Mel filter bank loaded from assets, null if not available. */
    private var melFilters: FloatArray? = null

    /** Vocabulary tokens loaded from assets, null if not available. */
    private var vocabulary: List<String>? = null

    /**
     * Load the mel filter bank and vocabulary from `filters_vocab_en.bin`.
     *
     * The bin file is the canonical `nyadla-sys/whisper-tiny.en.tflite`
     * companion: little-endian header `magic:int32 numMelBins:int32
     * melFilterCount:int32 vocabSize:int32`, followed by `melFilterCount`
     * float32s and a length-prefixed UTF-8 vocabulary table. We accept any
     * mel filter shape (this implementation does not yet compute the
     * spectrogram itself — see [computeMelSpectrogram]) and best-effort
     * load the vocabulary so [decodeTokens] can produce text once decoder
     * support lands.
     *
     * Failures degrade gracefully: returns false and leaves [melFilters]
     * and [vocabulary] null. The TF Lite interpreter still runs.
     *
     * @return true if loading succeeded, false otherwise.
     */
    fun loadResources(): Boolean {
        return try {
            val assetManager = context.assets
            val bytes = assetManager.open("filters_vocab_en.bin").use { it.readBytes() }
            val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)

            // Header (best-effort — different bin layouts exist; treat any read
            // failure as a graceful skip rather than throwing).
            val magic = buf.int
            val melCount = buf.int
            val melFloats = FloatArray(melCount.coerceAtMost((bytes.size - 8) / 4))
            for (i in melFloats.indices) melFloats[i] = buf.float
            melFilters = melFloats

            // Remaining bytes contain the vocabulary table. Each entry is
            // length-prefixed (int16) then UTF-8 bytes. Stop on EOF.
            val vocab = mutableListOf<String>()
            while (buf.remaining() >= 2) {
                val len = buf.short.toInt() and 0xFFFF
                if (len <= 0 || len > buf.remaining()) break
                val tokenBytes = ByteArray(len)
                buf.get(tokenBytes)
                vocab.add(String(tokenBytes, Charsets.UTF_8))
            }
            vocabulary = vocab

            Log.i(
                TAG,
                "WhisperProcessor: loaded magic=0x${magic.toString(16)} " +
                        "mel=${melFloats.size} vocab=${vocab.size}"
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load Whisper resources", e)
            melFilters = null
            vocabulary = null
            false
        }
    }

    /**
     * Convert raw PCM audio to the input format expected by the Whisper model.
     *
     * @param pcmData Raw 16-bit PCM samples.
     * @param sampleRate The sample rate of the input audio (should be 16000).
     * @return Float array suitable for TF Lite interpreter input, or null if processing fails.
     */
    fun processAudio(pcmData: ShortArray, sampleRate: Int = SAMPLE_RATE): FloatArray? {
        if (pcmData.isEmpty()) return null

        // Step 1: Convert ShortArray PCM to float (-1.0 to 1.0)
        val floatData = FloatArray(pcmData.size) { i ->
            pcmData[i].toFloat() / Short.MAX_VALUE.toFloat()
        }

        // Step 2: Pad or trim to expected length (30 seconds at 16kHz)
        val normalized = when {
            floatData.size >= EXPECTED_SAMPLES -> floatData.copyOfRange(0, EXPECTED_SAMPLES)
            else -> {
                val padded = FloatArray(EXPECTED_SAMPLES)
                floatData.copyInto(padded)
                padded
            }
        }

        // Step 3: If mel filters are available, compute mel spectrogram
        // Otherwise, return the raw normalized audio for simplified model input
        return if (melFilters != null) {
            computeMelSpectrogram(normalized)
        } else {
            // Simplified: return raw audio as float array
            // The actual model may require mel spectrogram input
            normalized
        }
    }

    /**
     * Compute mel spectrogram from normalized audio.
     *
     * TODO: Implement full mel spectrogram computation following the
     * whisper_android reference implementation. This requires:
     * - STFT (Short-Time Fourier Transform)
     * - Mel filter bank application
     * - Log magnitude conversion
     *
     * @param audio Normalized float audio samples.
     * @return Mel spectrogram as flattened float array.
     */
    private fun computeMelSpectrogram(audio: FloatArray): FloatArray {
        // Placeholder: return zeros until mel computation is implemented
        return FloatArray(N_MELS * N_FRAMES)
    }

    /**
     * Decode output tokens from the model to text.
     *
     * @param tokens Model output token IDs.
     * @return Decoded text string.
     */
    fun decodeTokens(tokens: IntArray): String {
        val vocab = vocabulary
        if (vocab == null || vocab.isEmpty()) {
            // Without vocabulary, return empty string
            return ""
        }

        return tokens
            .filter { it in vocab.indices }
            .joinToString("") { vocab[it] }
            .trim()
    }
}
