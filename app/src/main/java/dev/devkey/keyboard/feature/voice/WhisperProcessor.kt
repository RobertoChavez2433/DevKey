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
     * companion: little-endian `magic:int32 numMelBins:int32 numFreqs:int32`,
     * followed by `numMelBins * numFreqs` float32 mel filters, then
     * `vocabSize:int32` and a length-prefixed UTF-8 vocabulary table.
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

            if (bytes.size < 12) return false
            val magic = buf.int
            if (magic != 0x5553454e) return false
            val numMelBins = buf.int
            val numFreqs = buf.int
            val melFilterCount = numMelBins * numFreqs
            if (melFilterCount <= 0 || buf.remaining() < melFilterCount * 4 + 4) return false
            val melFloats = FloatArray(melFilterCount)
            for (i in melFloats.indices) melFloats[i] = buf.float
            melFilters = melFloats

            // Vocabulary section: vocabSize entries, each length:int32 + UTF-8 bytes.
            val vocabSize = buf.int
            val vocab = mutableListOf<String>()
            for (i in 0 until vocabSize) {
                if (buf.remaining() < 4) break
                val len = buf.int
                if (len <= 0 || len > buf.remaining()) break
                val tokenBytes = ByteArray(len)
                buf.get(tokenBytes)
                vocab.add(String(tokenBytes, Charsets.UTF_8))
            }
            vocabulary = vocab

            Log.i(
                TAG,
                "WhisperProcessor: loaded magic=0x${magic.toString(16)} " +
                        "melBins=$numMelBins numFreqs=$numFreqs " +
                        "vocabSize=$vocabSize melFilters=${melFloats.size} " +
                        "vocab=${vocab.size}"
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
    fun processAudio(pcmData: ShortArray, _sampleRate: Int = SAMPLE_RATE): FloatArray? {
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
        val fftIn = FloatArray(nFft)
        val fftOut = FloatArray(nFft * 2)
        val output = FloatArray(N_MELS * N_FRAMES)
        for (frame in 0 until N_FRAMES) {
            val offset = frame * hopLength
            for (i in 0 until nFft) {
                val idx = offset + i
                fftIn[i] = if (idx < audio.size) audio[idx] * window[i] else 0f
            }
            fft(fftIn, fftOut)
            for (i in 0 until nFft) {
                fftOut[i] = fftOut[2 * i] * fftOut[2 * i] + fftOut[2 * i + 1] * fftOut[2 * i + 1]
            }
            for (i in 1 until nFft / 2) {
                fftOut[i] += fftOut[nFft - i]
            }

            // Apply mel filter bank: filters is flat [numMelBins * nFreqs] = 80 * 201
            for (mel in 0 until N_MELS) {
                var sum = 0.0
                val filterOffset = mel * nFreqs
                for (k in 0 until nFreqs) {
                    if (filterOffset + k < filters.size) {
                        sum += filters[filterOffset + k] * fftOut[k]
                    }
                }
                // Log10 compression, clamp floor
                val logVal = Math.log10(sum.coerceAtLeast(1e-10))
                output[mel * N_FRAMES + frame] = logVal.toFloat()
            }
        }

        // Normalize per canonical Whisper: clamp to max - 8.0, then (x + 4) / 4
        var maxVal = -Float.MAX_VALUE
        for (v in output) if (v > maxVal) maxVal = v
        val floor = maxVal - 8.0f
        for (i in output.indices) {
            output[i] = (output[i].coerceAtLeast(floor) + 4.0f) / 4.0f
        }

        return output
    }

    /**
     * Cooley-Tukey FFT matching the upstream Android Whisper reference.
     * The Whisper filter bank expects a 400-point FFT folded into 201 bins.
     */
    private fun fft(input: FloatArray, output: FloatArray) {
        val size = input.size
        if (size == 1) {
            output[0] = input[0]
            output[1] = 0.0f
            return
        }
        if (size % 2 == 1) {
            dft(input, output)
            return
        }

        val even = FloatArray(size / 2)
        val odd = FloatArray(size / 2)
        var evenIndex = 0
        var oddIndex = 0
        for (i in 0 until size) {
            if (i % 2 == 0) {
                even[evenIndex++] = input[i]
            } else {
                odd[oddIndex++] = input[i]
            }
        }

        val evenFft = FloatArray(size)
        val oddFft = FloatArray(size)
        fft(even, evenFft)
        fft(odd, oddFft)

        for (k in 0 until size / 2) {
            val theta = 2.0 * Math.PI * k / size
            val re = Math.cos(theta).toFloat()
            val im = (-Math.sin(theta)).toFloat()
            val oddRe = oddFft[2 * k]
            val oddIm = oddFft[2 * k + 1]
            output[2 * k] = evenFft[2 * k] + re * oddRe - im * oddIm
            output[2 * k + 1] = evenFft[2 * k + 1] + re * oddIm + im * oddRe
            output[2 * (k + size / 2)] = evenFft[2 * k] - re * oddRe + im * oddIm
            output[2 * (k + size / 2) + 1] = evenFft[2 * k + 1] - re * oddIm - im * oddRe
        }
    }

    private fun dft(input: FloatArray, output: FloatArray) {
        for (k in input.indices) {
            var re = 0.0f
            var im = 0.0f
            for (n in input.indices) {
                val angle = (2.0 * Math.PI * k * n / input.size).toFloat()
                re += input[n] * Math.cos(angle.toDouble()).toFloat()
                im -= input[n] * Math.sin(angle.toDouble()).toFloat()
            }
            output[k * 2] = re
            output[k * 2 + 1] = im
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
