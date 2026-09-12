package com.sza.fastmediasorter.wear.domain.documents

/**
 * Where a document was left, expressed without naming anything the screen owns.
 *
 * S2532: the stored position used to travel as the UI's own list-position type, which made
 * [com.sza.fastmediasorter.wear.domain.repository.preferences.WearDocumentPreferences] - a domain
 * contract - depend on the Compose list it is eventually fed to. The pair of numbers is the same
 * either way, so the anchor carries them and the reader translates at its own boundary; a second
 * surface onto the same documents then needs no list at all to ask where reading stopped.
 *
 * @param index the paragraph the reader came to rest on.
 * @param offset how far into that paragraph, in the units the surface scrolls in.
 */
data class DocumentReadingAnchor(val index: Int, val offset: Int)
