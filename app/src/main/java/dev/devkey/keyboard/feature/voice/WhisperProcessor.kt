package dev.devkey.keyboard.feature.voice

import android.content.Context
import android.util.Log
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import org.jtransforms.fft.FloatFFT_1D

/**
 * Preprocesses raw PCM audio into the format expected by the Whisper TF Lite model.
 *
 * Whisper expects 30 seconds of audio at 16kHz (480,000 samples), converted to
 * an 80-bin mel spectrogram. This processor handles padding/trimming and
 * mel spectrogram computation.
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

        private const val N_FFT = 400
        private const val HOP_LENGTH = 160
        private const val N_FREQS = N_FFT / 2 + 1
        private const val PCM_SCALE = 1.0f / Short.MAX_VALUE.toFloat()
        private const val LOG_FLOOR = 1e-10f
    }

    /** Mel filter bank loaded from assets, null if not available. */
    private var melFilters: FloatArray? = null
    private var melFilterFreqs: Int = 0

    /** Vocabulary tokens loaded from assets, null if not available. */
    private var vocabulary: List<String>? = null

    private val fft = FloatFFT_1D(N_FFT.toLong())
    private val hannWindow = FloatArray(N_FFT) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / N_FFT))).toFloat()
    }

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
            melFilterFreqs = numFreqs

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
            melFilterFreqs = 0
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
    @Synchronized
    fun processAudio(pcmData: ShortArray, _sampleRate: Int = SAMPLE_RATE): FloatArray? {
        if (pcmData.isEmpty()) return null

        val sampleCount = pcmData.size.coerceAtMost(EXPECTED_SAMPLES)

        return if (melFilters != null) {
            computeMelSpectrogram(pcmData, sampleCount)
        } else {
            normalizedAudio(pcmData, sampleCount)
        }
    }

    private fun normalizedAudio(pcmData: ShortArray, sampleCount: Int): FloatArray {
        val normalized = FloatArray(EXPECTED_SAMPLES)
        var i = 0
        while (i < sampleCount) {
            normalized[i] = pcmData[i] * PCM_SCALE
            i++
        }
        return normalized
    }

    /**
     * Compute mel spectrogram from normalized audio.
     *
     * Pipeline: STFT (Hann window, N_FFT=400, HOP=160) → power spectrum →
     * mel filter bank application → log10 compression → normalize to [-1, 1].
     *
     * Reference: nyadla-sys/whisper_android Java implementation.
     *
     * The old implementation recursively allocated FFT buffers for every frame.
     * This path keeps one frame buffer and uses an optimized mixed-radix real FFT.
     * For short dictation, it computes only frames that contain recorded samples
     * and fills the padded tail after normalization.
     *
     * @param pcmData Raw PCM samples.
     * @param sampleCount Samples to process after 30s trimming.
     * @return Mel spectrogram as flattened float array [mel_bin, frame] = 80×3000.
     */
    private fun computeMelSpectrogram(pcmData: ShortArray, sampleCount: Int): FloatArray {
        val filters = melFilters ?: return FloatArray(N_MELS * N_FRAMES)
        val filterFreqs = melFilterFreqs.coerceAtMost(N_FREQS)
        val activeFrames = ((sampleCount + HOP_LENGTH - 1) / HOP_LENGTH)
            .coerceIn(1, N_FRAMES)
        val fftBuffer = FloatArray(N_FFT)
        val powerSpectrum = FloatArray(N_FREQS)
        val output = FloatArray(N_MELS * N_FRAMES)

        var frame = 0
        while (frame < activeFrames) {
            val offset = frame * HOP_LENGTH
            var i = 0
            while (i < N_FFT) {
                val idx = offset + i
                fftBuffer[i] = if (idx < sampleCount) {
                    pcmData[idx] * PCM_SCALE * hannWindow[i]
                } else {
                    0f
                }
                i++
            }
            fft.realForward(fftBuffer)
            writePowerSpectrum(fftBuffer, powerSpectrum)

            var mel = 0
            while (mel < N_MELS) {
                var sum = 0.0f
                val filterOffset = mel * melFilterFreqs
                var k = 0
                while (k < filterFreqs && filterOffset + k < filters.size) {
                    sum += filters[filterOffset + k] * powerSpectrum[k]
                    k++
                }
                output[mel * N_FRAMES + frame] = log10(sum.coerceAtLeast(LOG_FLOOR)).toFloat()
                mel++
            }
            frame++
        }

        normalizeMelOutput(output, activeFrames)
        return output
    }

    private fun writePowerSpectrum(fftBuffer: FloatArray, powerSpectrum: FloatArray) {
        powerSpectrum[0] = fftBuffer[0] * fftBuffer[0]
        var k = 1
        while (k < N_FFT / 2) {
            val re = fftBuffer[2 * k]
            val im = fftBuffer[2 * k + 1]
            powerSpectrum[k] = re * re + im * im
            k++
        }
        powerSpectrum[N_FFT / 2] = fftBuffer[1] * fftBuffer[1]
    }

    private fun normalizeMelOutput(output: FloatArray, activeFrames: Int) {
        var maxVal = -Float.MAX_VALUE
        var mel = 0
        while (mel < N_MELS) {
            val base = mel * N_FRAMES
            var frame = 0
            while (frame < activeFrames) {
                val value = output[base + frame]
                if (value > maxVal) maxVal = value
                frame++
            }
            mel++
        }
        val floor = maxVal - 8.0f
        val paddedValue = (floor + 4.0f) / 4.0f
        mel = 0
        while (mel < N_MELS) {
            val base = mel * N_FRAMES
            var frame = 0
            while (frame < activeFrames) {
                output[base + frame] = (output[base + frame].coerceAtLeast(floor) + 4.0f) / 4.0f
                frame++
            }
            while (frame < N_FRAMES) {
                output[base + frame] = paddedValue
                frame++
            }
            mel++
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
