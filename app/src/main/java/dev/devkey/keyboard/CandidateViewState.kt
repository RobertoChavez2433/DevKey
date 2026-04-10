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

package dev.devkey.keyboard

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.widget.PopupWindow
import android.widget.TextView

/**
 * Shared mutable state for CandidateView and its helper objects.
 * All fields are package-level accessible so helpers can read and write them
 * without reflection. The owning CandidateView constructs this once; helpers
 * hold a reference to the same instance.
 */
internal class CandidateViewState(
    val paint: Paint,
    val descent: Int,
    val minTouchableWidth: Int,
    val colorNormal: Int,
    val colorRecommended: Int,
    val colorOther: Int,
    val selectionHighlight: Drawable?,
    val divider: Drawable?,
    val addToDictionaryHint: CharSequence,
    val previewPopup: PopupWindow,
    val previewText: TextView,
) {
    var service: LatinIME? = null

    val suggestions = ArrayList<CharSequence>()
    val wordWidth = IntArray(CANDIDATE_MAX_SUGGESTIONS)
    val wordX = IntArray(CANDIDATE_MAX_SUGGESTIONS)

    var showingCompletions = false
    var selectedString: CharSequence? = null
    var selectedIndex = -1
    var touchX = CANDIDATE_OUT_OF_BOUNDS_X_COORD
    var typedWordValid = false
    var haveMinimalSuggestion = false
    var bgPadding: Rect? = null
    var currentWordIndex = CANDIDATE_OUT_OF_BOUNDS_WORD_INDEX
    var popupPreviewX = 0
    var popupPreviewY = 0
    var scrolled = false
    var showingAddToDictionary = false
    var targetScrollX = 0
    var totalWidth = 0
}
