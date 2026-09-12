package com.sza.fastmediasorter.wear.data.preferences.sections

import androidx.datastore.preferences.core.edit
import com.sza.fastmediasorter.wear.data.preferences.DocumentReadingPosition
import com.sza.fastmediasorter.wear.data.preferences.DocumentReadingPositions
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceKeys
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceSection
import com.sza.fastmediasorter.wear.data.preferences.WearSettingsDataStore
import com.sza.fastmediasorter.wear.domain.documents.DocumentFontSize
import com.sza.fastmediasorter.wear.domain.documents.DocumentReadingAnchor
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearDocumentPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearDocumentPreferencesImpl @Inject constructor(
    settings: WearSettingsDataStore
) : WearPreferenceSection(settings), WearDocumentPreferences {

    // An absent value has to read as MEDIUM, so a watch that never touched the control keeps the size
    // the reader shipped with rather than inheriting whichever step the enum happens to declare first.
    override val documentFontSize: Flow<DocumentFontSize> = store.data.map { prefs ->
        fontSizeOf(prefs[WearPreferenceKeys.DOCUMENT_FONT_SIZE])
    }

    override suspend fun setDocumentFontSize(size: DocumentFontSize) {
        store.edit { prefs ->
            prefs[WearPreferenceKeys.DOCUMENT_FONT_SIZE] = size.name
        }
    }

    override suspend fun readingPositionFor(key: String, sizeBytes: Long): DocumentReadingAnchor? {
        val stored = store.data.first()[WearPreferenceKeys.DOCUMENT_READING_POSITIONS]
        return DocumentReadingPositions.decode(stored)
            .firstOrNull { it.key == key && it.sizeBytes == sizeBytes }
            ?.let { DocumentReadingAnchor(it.index, it.offset) }
    }

    // Read-modify-write inside one `edit`, because two readers of two documents settling at once would
    // otherwise each re-encode the list they read before the other wrote, losing one of the records.
    override suspend fun setReadingPosition(key: String, sizeBytes: Long, index: Int, offset: Int) {
        store.edit { prefs ->
            val current = DocumentReadingPositions.decode(
                prefs[WearPreferenceKeys.DOCUMENT_READING_POSITIONS]
            )
            val pushed = DocumentReadingPositions.push(
                current,
                DocumentReadingPosition(key, index, offset, sizeBytes)
            )
            prefs[WearPreferenceKeys.DOCUMENT_READING_POSITIONS] =
                DocumentReadingPositions.encode(pushed)
        }
    }

    private fun fontSizeOf(stored: String?): DocumentFontSize =
        DocumentFontSize.entries.firstOrNull { it.name == stored } ?: DocumentFontSize.MEDIUM
}
