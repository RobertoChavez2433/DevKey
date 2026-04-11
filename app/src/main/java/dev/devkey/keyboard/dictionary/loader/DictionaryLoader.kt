/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package dev.devkey.keyboard.dictionary.loader

import android.content.Context
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels

/**
 * Handles loading binary dictionary data from resources or raw streams into a
 * direct [ByteBuffer] that can be passed to the JNI layer.
 *
 * This class owns no native state; it only performs I/O and returns the result
 * to [BinaryDictionary] which holds the JNI handle.
 */
internal object DictionaryLoader {

    private const val TAG = "DevKey/DictionaryLoader"

    /**
     * Result of a successful load operation.
     *
     * @param buffer  Direct [ByteBuffer] containing the raw dictionary bytes.
     * @param length  Number of valid bytes in [buffer].
     */
    data class LoadResult(val buffer: ByteBuffer, val length: Int)

    /**
     * Load dictionary data from an array of [InputStream]s.
     *
     * Each stream is read in order into a single contiguous direct buffer.
     * All streams are closed in the `finally` block regardless of outcome.
     *
     * @return [LoadResult] on success, or `null` if loading fails.
     */
    fun load(streams: Array<InputStream>): LoadResult? {
        try {
            var total = 0
            for (stream in streams) {
                total += stream.available()
            }

            val buffer = ByteBuffer.allocateDirect(total).order(ByteOrder.nativeOrder())
            var got = 0
            for (stream in streams) {
                got += Channels.newChannel(stream).read(buffer)
            }

            if (got != total) {
                Log.e(TAG, "Read $got bytes, expected $total")
                return null
            }

            if (total > 10_000) Log.i(TAG, "Loaded dictionary bytes, len=$total")
            return LoadResult(buffer, total)
        } catch (e: IOException) {
            Log.w(TAG, "No available memory for binary dictionary")
            return null
        } finally {
            for (stream in streams) {
                try {
                    stream.close()
                } catch (e: IOException) {
                    Log.w(TAG, "Failed to close input stream")
                }
            }
        }
    }

    /**
     * Load dictionary data from raw Android resource IDs.
     *
     * Convenience wrapper that opens each resource and delegates to [load].
     *
     * @return [LoadResult] on success, or `null` if loading fails.
     */
    fun load(context: Context, resId: IntArray): LoadResult? {
        val streams = Array(resId.size) { i ->
            context.resources.openRawResource(resId[i])
        }
        return load(streams)
    }
}
