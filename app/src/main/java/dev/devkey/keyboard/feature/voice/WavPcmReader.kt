package dev.devkey.keyboard.feature.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object WavPcmReader {
    private data class Layout(
        val audioFormat: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val dataOffset: Int,
        val dataSize: Int,
    )

    fun readPcm16(bytes: ByteArray): ShortArray? {
        if (bytes.size < MIN_WAV_BYTES) return null
        if (!hasRiffWaveHeader(bytes)) return null

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val layout = readLayout(bytes, buffer) ?: return null
        if (layout.audioFormat != PCM_FORMAT || layout.bitsPerSample != PCM_BITS) return null
        return readSamples(buffer, layout)
    }

    private fun hasRiffWaveHeader(bytes: ByteArray): Boolean =
        String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"

    private fun readLayout(bytes: ByteArray, buffer: ByteBuffer): Layout? {
        var channels = 1
        var bitsPerSample = PCM_BITS
        var audioFormat = PCM_FORMAT
        var dataOffset = -1
        var dataSize = 0
        var offset = RIFF_HEADER_BYTES
        var malformed = false
        while (offset + CHUNK_HEADER_BYTES <= bytes.size && dataOffset < 0 && !malformed) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = buffer.getInt(offset + 4)
            val chunkDataOffset = offset + CHUNK_HEADER_BYTES
            if (chunkSize < 0 || chunkDataOffset + chunkSize > bytes.size) {
                malformed = true
                break
            }
            when (chunkId) {
                "fmt " -> {
                    if (chunkSize >= MIN_FMT_CHUNK_BYTES) {
                        audioFormat = buffer.getShort(chunkDataOffset).toInt()
                        channels = buffer.getShort(chunkDataOffset + 2).toInt().coerceAtLeast(1)
                        bitsPerSample = buffer.getShort(chunkDataOffset + 14).toInt()
                    }
                }
                "data" -> {
                    dataOffset = chunkDataOffset
                    dataSize = chunkSize
                }
            }
            offset = chunkDataOffset + chunkSize + (chunkSize and 1)
        }

        return Layout(
            audioFormat = audioFormat,
            channels = channels,
            bitsPerSample = bitsPerSample,
            dataOffset = dataOffset,
            dataSize = dataSize,
        ).takeIf { !malformed && it.dataOffset >= 0 && it.dataSize > 0 }
    }

    private fun readSamples(buffer: ByteBuffer, layout: Layout): ShortArray {
        val bytesPerFrame = layout.channels * Short.SIZE_BYTES
        val frameCount = layout.dataSize / bytesPerFrame
        val samples = ShortArray(frameCount)
        var frame = 0
        while (frame < frameCount) {
            samples[frame] = buffer.getShort(layout.dataOffset + frame * bytesPerFrame)
            frame++
        }
        return samples
    }

    private const val MIN_WAV_BYTES = 44
    private const val RIFF_HEADER_BYTES = 12
    private const val CHUNK_HEADER_BYTES = 8
    private const val MIN_FMT_CHUNK_BYTES = 16
    private const val PCM_FORMAT = 1
    private const val PCM_BITS = 16
}
