package dev.devkey.keyboard.compose

import android.content.Context
import dev.devkey.keyboard.R
import org.json.JSONObject

/**
 * Loads compose sequences from the raw JSON resource into [ComposeSequence].
 *
 * Call [load] once during app startup (e.g. in [LatinIME.onCreate]) before
 * any compose sequence lookups are performed.
 */
object ComposeSequenceLoader {
    fun load(context: Context) {
        val json = context.resources.openRawResource(R.raw.compose_sequences)
            .bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        for (key in obj.keys()) {
            ComposeSequence.put(key, obj.getString(key))
        }
    }
}
