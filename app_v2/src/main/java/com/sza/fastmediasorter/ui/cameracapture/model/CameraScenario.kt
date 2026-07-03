package com.sza.fastmediasorter.ui.cameracapture.model

import androidx.annotation.StringRes
import com.sza.fastmediasorter.R

/**
 * Names the calling scenario so the visible camera host can show a context label (S0812).
 *
 * Only scenarios that actually reach the interactive camera carry a label; headless auto-capture
 * gestures (S0791/S0792/S0794) never show this UI. [NONE] has no label and hides the view.
 */
enum class CameraScenario(@StringRes val labelRes: Int) {
    NONE(0),
    OCR_TRANSLATE(R.string.camera_scenario_ocr_translate),
    ;

    companion object {
        /** Defaults to [NONE] for callers that pass no explicit scenario. */
        fun fromName(name: String?): CameraScenario =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}
