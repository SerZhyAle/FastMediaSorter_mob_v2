package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.repository.preferences.WearAppearancePreferences
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearBrowsePreferences
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearDocumentPreferences
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearMediaTypePreferences
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearMiniAppPreferences
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearPlaybackPreferences
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearSettingsSyncPreferences
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearStreamsPreferences

/**
 * Every Wear OS application preference, as one type.
 *
 * S2655: declares nothing of its own - the members live in the themed interfaces it extends,
 * each of which is counted separately by detekt's `TooManyFunctions`. Before the split both this
 * type and its implementation stood at 38 and 39 functions against a threshold of 40, so the next
 * setting anyone added broke a gate for a reason that had nothing to do with their ticket.
 *
 * The composite exists rather than the seven types being injected directly because 33 classes and 15
 * tests already address this name; splitting the API instead of only its declaration would have cost
 * that many edits and bought the wearer nothing.
 */
interface WearPreferencesRepository :
    WearMediaTypePreferences,
    WearPlaybackPreferences,
    WearBrowsePreferences,
    WearStreamsPreferences,
    WearAppearancePreferences,
    WearMiniAppPreferences,
    WearDocumentPreferences,
    WearSettingsSyncPreferences
