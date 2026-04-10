/*
 * Copyright (C) 2011 Darren Salt
 *
 * Licensed under the Apache License, Version 2.0 (the "Licence"); you may
 * not use this file except in compliance with the Licence. You may obtain
 * a copy of the Licence at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * Licence for the specific language governing permissions and limitations
 * under the Licence.
 */

package dev.devkey.keyboard

import android.content.Context
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
