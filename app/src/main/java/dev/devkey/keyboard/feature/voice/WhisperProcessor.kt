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

            // Header: magic:int32, numMelBins:int32, melFilterCount:int32, vocabSize:int32 = 16 bytes
            if (bytes.size < 16) return false
            val magic = buf.int
            val numMelBins = buf.int
            val melFilterCount = buf.int
            val vocabSize = buf.int
            val melFloats = FloatArray(melFilterCount.coerceAtMost((bytes.size - 16) / 4))
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
                        "melBins=$numMelBins melFilterCount=$melFilterCount " +
                        "vocabSize=$vocabSize mel=${melFloats.size} vocab=${vocab.size}"
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
     * Pipeline: STFT (Hann window, N_FFT=400, HOP=160) → power spectrum →
     * mel filter bank application → log10 compression → normalize to [-1, 1].
     *
     * Reference: nyadla-sys/whisper_android Java implementation.
     *
     * @param audio Normalized float audio samples (480,000 = 30s at 16kHz).
     * @return Mel spectrogram as flattened float array [mel_bin, frame] = 80×3000.
     */
    private fun computeMelSpectrogram(audio: FloatArray): FloatArray {
        val filters = melFilters ?: return FloatArray(N_MELS * N_FRAMES)
        val nFft = 400
        val hopLength = 160
        val nFreqs = nFft / 2 + 1 // 201

        // Hann window
        val window = FloatArray(nFft) { i ->
            (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / nFft))).toFloat()
        }

        // STFT → power spectrum for each frame
        val magnitudes = Array(N_FRAMES) { frame ->
            val offset = frame * hopLength
            // Windowed frame (zero-padded if near end)
            val real = FloatArray(nFft)
            val imag = FloatArray(nFft)
            for (i in 0 until nFft) {
                val idx = offset + i
                real[i] = if (idx < audio.size) audio[idx] * window[i] else 0f
            }
            // In-place radix-2 FFT
            fft(real, imag)
            // Power spectrum (squared magnitude), only first nFreqs bins
            FloatArray(nFreqs) { k -> real[k] * real[k] + imag[k] * imag[k] }
        }

        // Apply mel filter bank: filters is flat [numMelBins * nFreqs] = 80 * 201
        val output = FloatArray(N_MELS * N_FRAMES)
        for (mel in 0 until N_MELS) {
            for (frame in 0 until N_FRAMES) {
                var sum = 0.0
                val filterOffset = mel * nFreqs
                for (k in 0 until nFreqs) {
                    if (filterOffset + k < filters.size) {
                        sum += filters[filterOffset + k] * magnitudes[frame][k]
                    }
                }
                // Log10 compression, clamp floor
                val logVal = if (sum < 1e-10) -10.0 else Math.log10(sum)
                output[mel * N_FRAMES + frame] = logVal.toFloat()
            }
        }

        // Normalize: clamp to max - 8.0 (80dB below peak), then scale to [-1, 1]
        var maxVal = -Float.MAX_VALUE
        for (v in output) if (v > maxVal) maxVal = v
        val floor = maxVal - 8.0f
        for (i in output.indices) {
            output[i] = ((output[i].coerceAtLeast(floor) - floor) / 4.0f) - 1.0f
        }

        return output
    }

    /**
     * In-place radix-2 Cooley-Tukey FFT. Input arrays must be power-of-2 length.
     */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var tmp = real[i]; real[i] = real[j]; real[j] = tmp
                tmp = imag[i]; imag[i] = imag[j]; imag[j] = tmp
            }
        }
        // FFT butterfly
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = -2.0 * Math.PI / len
            for (i in 0 until n step len) {
                for (k in 0 until halfLen) {
                    val theta = angle * k
                    val cos = Math.cos(theta).toFloat()
                    val sin = Math.sin(theta).toFloat()
                    val tReal = real[i + k + halfLen] * cos - imag[i + k + halfLen] * sin
                    val tImag = real[i + k + halfLen] * sin + imag[i + k + halfLen] * cos
                    real[i + k + halfLen] = real[i + k] - tReal
                    imag[i + k + halfLen] = imag[i + k] - tImag
                    real[i + k] += tReal
                    imag[i + k] += tImag
                }
            }
            len = len shl 1
        }
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
