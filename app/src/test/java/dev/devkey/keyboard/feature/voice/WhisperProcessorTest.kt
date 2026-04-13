package dev.devkey.keyboard.feature.voice

import android.content.Context
import android.content.res.AssetManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit tests for [WhisperProcessor].
 *
 * TFLite is not available under Robolectric, so these tests exercise the
 * pure-logic paths: resource parsing (loadResources), audio
 * padding/trimming (processAudio), and token decoding (decodeTokens).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WhisperProcessorTest {

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager

    @Before
    fun setUp() {
        context = mock()
        assetManager = mock()
        whenever(context.assets).thenReturn(assetManager)
    }

    // ------------------------------------------------------------------
    // loadResources — valid header
    // ------------------------------------------------------------------

    @Test
    fun `loadResources with valid header returns true`() {
        val bin = buildValidFilterVocabBin(
            magic = 0x5749_4853,
            numMelBins = 2,
            numFreqs = 3,
            vocabSize = 2,
            melFloats = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f),
            vocab = listOf("hello", "world")
        )
        whenever(assetManager.open("filters_vocab_en.bin"))
            .thenReturn(ByteArrayInputStream(bin))

        val processor = WhisperProcessor(context)
        assertTrue(processor.loadResources())
    }

    // ------------------------------------------------------------------
    // loadResources — header too short
    // ------------------------------------------------------------------

    @Test
    fun `loadResources with header shorter than 16 bytes returns false`() {
        val tooShort = ByteArray(12) // less than 16-byte header
        whenever(assetManager.open("filters_vocab_en.bin"))
            .thenReturn(ByteArrayInputStream(tooShort))

        val processor = WhisperProcessor(context)
        assertFalse(processor.loadResources())
    }

    // ------------------------------------------------------------------
    // loadResources — missing asset file
    // ------------------------------------------------------------------

    @Test
    fun `loadResources returns false when asset file is missing`() {
        whenever(assetManager.open("filters_vocab_en.bin"))
            .thenThrow(java.io.FileNotFoundException("not found"))

        val processor = WhisperProcessor(context)
        assertFalse(processor.loadResources())
    }

    // ------------------------------------------------------------------
    // processAudio — empty audio returns null
    // ------------------------------------------------------------------

    @Test
    fun `processAudio with empty audio returns null`() {
        val processor = WhisperProcessor(context)
        val result = processor.processAudio(ShortArray(0))
        assertNull(result)
    }

    // ------------------------------------------------------------------
    // processAudio — pad short audio to EXPECTED_SAMPLES (no mel filters)
    // ------------------------------------------------------------------

    @Test
    fun `processAudio pads short audio to EXPECTED_SAMPLES without mel filters`() {
        val processor = WhisperProcessor(context)
        // Without loading resources, melFilters is null -> returns raw normalized
        val shortAudio = ShortArray(100) { 1000 }
        val result = processor.processAudio(shortAudio)

        assertNotNull(result)
        assertEquals(WhisperProcessor.EXPECTED_SAMPLES, result!!.size)
        // First 100 samples should be non-zero, rest should be 0
        assertTrue(result[0] != 0f)
        assertEquals(0f, result[WhisperProcessor.EXPECTED_SAMPLES - 1], 0.0001f)
    }

    // ------------------------------------------------------------------
    // processAudio — trim long audio to EXPECTED_SAMPLES (no mel filters)
    // ------------------------------------------------------------------

    @Test
    fun `processAudio trims long audio to EXPECTED_SAMPLES without mel filters`() {
        val processor = WhisperProcessor(context)
        val longAudio = ShortArray(WhisperProcessor.EXPECTED_SAMPLES + 5000) { 500 }
        val result = processor.processAudio(longAudio)

        assertNotNull(result)
        assertEquals(WhisperProcessor.EXPECTED_SAMPLES, result!!.size)
    }

    // ------------------------------------------------------------------
    // processAudio — exact-length audio (no mel filters)
    // ------------------------------------------------------------------

    @Test
    fun `processAudio with exact EXPECTED_SAMPLES length returns same size`() {
        val processor = WhisperProcessor(context)
        val exactAudio = ShortArray(WhisperProcessor.EXPECTED_SAMPLES) { 100 }
        val result = processor.processAudio(exactAudio)

        assertNotNull(result)
        assertEquals(WhisperProcessor.EXPECTED_SAMPLES, result!!.size)
    }

    // ------------------------------------------------------------------
    // processAudio — output values are normalized to [-1, 1]
    // ------------------------------------------------------------------

    @Test
    fun `processAudio normalizes PCM values to float range`() {
        val processor = WhisperProcessor(context)
        val audio = shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE, 0)
        val result = processor.processAudio(audio)

        assertNotNull(result)
        // First sample: MAX_VALUE / MAX_VALUE ~= 1.0
        assertEquals(1.0f, result!![0], 0.001f)
        // Second sample: MIN_VALUE / MAX_VALUE ~= -1.0
        assertEquals(Short.MIN_VALUE.toFloat() / Short.MAX_VALUE.toFloat(), result[1], 0.001f)
        // Third sample: 0
        assertEquals(0f, result[2], 0.001f)
    }

    // ------------------------------------------------------------------
    // processAudio — with mel filters loaded, output shape is N_MELS * N_FRAMES
    // ------------------------------------------------------------------

    @Test
    fun `processAudio with mel filters returns mel spectrogram shape`() {
        // Build a valid bin so mel filters are loaded
        val numMelBins = WhisperProcessor.N_MELS
        val numFreqs = 201 // nFft/2 + 1 where nFft=400
        val melFloats = FloatArray(numMelBins * numFreqs) { 0.001f }
        val bin = buildValidFilterVocabBin(
            magic = 0x5749_4853,
            numMelBins = numMelBins,
            numFreqs = numFreqs,
            vocabSize = 0,
            melFloats = melFloats,
            vocab = emptyList()
        )
        whenever(assetManager.open("filters_vocab_en.bin"))
            .thenReturn(ByteArrayInputStream(bin))

        val processor = WhisperProcessor(context)
        assertTrue(processor.loadResources())

        val audio = ShortArray(1000) { 500 }
        val result = processor.processAudio(audio)

        assertNotNull(result)
        assertEquals(WhisperProcessor.N_MELS * WhisperProcessor.N_FRAMES, result!!.size)
    }

    // ------------------------------------------------------------------
    // decodeTokens — valid IDs
    // ------------------------------------------------------------------

    @Test
    fun `decodeTokens with valid IDs returns joined text`() {
        val bin = buildValidFilterVocabBin(
            magic = 0x5749_4853,
            numMelBins = 1,
            numFreqs = 1,
            vocabSize = 3,
            melFloats = floatArrayOf(0f),
            vocab = listOf("hello", " ", "world")
        )
        whenever(assetManager.open("filters_vocab_en.bin"))
            .thenReturn(ByteArrayInputStream(bin))

        val processor = WhisperProcessor(context)
        assertTrue(processor.loadResources())

        val result = processor.decodeTokens(intArrayOf(0, 1, 2))
        assertEquals("hello world", result)
    }

    // ------------------------------------------------------------------
    // decodeTokens — out-of-range IDs are filtered
    // ------------------------------------------------------------------

    @Test
    fun `decodeTokens filters out-of-range token IDs`() {
        val bin = buildValidFilterVocabBin(
            magic = 0x5749_4853,
            numMelBins = 1,
            numFreqs = 1,
            vocabSize = 2,
            melFloats = floatArrayOf(0f),
            vocab = listOf("a", "b")
        )
        whenever(assetManager.open("filters_vocab_en.bin"))
            .thenReturn(ByteArrayInputStream(bin))

        val processor = WhisperProcessor(context)
        assertTrue(processor.loadResources())

        // Token IDs 99 and -1 are out of range for vocab size 2
        val result = processor.decodeTokens(intArrayOf(0, 99, -1, 1))
        assertEquals("ab", result)
    }

    // ------------------------------------------------------------------
    // decodeTokens — without vocabulary returns empty string
    // ------------------------------------------------------------------

    @Test
    fun `decodeTokens without loaded vocabulary returns empty string`() {
        val processor = WhisperProcessor(context)
        val result = processor.decodeTokens(intArrayOf(0, 1, 2))
        assertEquals("", result)
    }

    // ------------------------------------------------------------------
    // decodeTokens — empty token array
    // ------------------------------------------------------------------

    @Test
    fun `decodeTokens with empty array returns empty string`() {
        val bin = buildValidFilterVocabBin(
            magic = 0x5749_4853,
            numMelBins = 1,
            numFreqs = 1,
            vocabSize = 2,
            melFloats = floatArrayOf(0f),
            vocab = listOf("a", "b")
        )
        whenever(assetManager.open("filters_vocab_en.bin"))
            .thenReturn(ByteArrayInputStream(bin))

        val processor = WhisperProcessor(context)
        assertTrue(processor.loadResources())

        val result = processor.decodeTokens(intArrayOf())
        assertEquals("", result)
    }

    // ------------------------------------------------------------------
    // Helper: build a valid filters_vocab_en.bin byte array
    // ------------------------------------------------------------------

    private fun buildValidFilterVocabBin(
        magic: Int,
        numMelBins: Int,
        numFreqs: Int,
        vocabSize: Int,
        melFloats: FloatArray,
        vocab: List<String>,
    ): ByteArray {
        // Header: 4 ints (16 bytes) + mel floats + vocab entries
        val vocabBytes = vocab.map { it.toByteArray(Charsets.UTF_8) }
        val vocabTotalSize = vocabBytes.sumOf { 4 + it.size }
        val totalSize = 16 + (melFloats.size * 4) + vocabTotalSize

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(magic)
        buf.putInt(numMelBins)
        buf.putInt(numFreqs)
        buf.putInt(vocabSize)
        for (f in melFloats) buf.putFloat(f)
        for (bytes in vocabBytes) {
            buf.putInt(bytes.size)
            buf.put(bytes)
        }
        return buf.array()
    }
}
