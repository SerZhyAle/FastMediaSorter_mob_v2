package com.sza.fastmediasorter.data.common

/**
 * S0403, owner ruling 2026-07-14: foss reads EPUB, PDF and plain text, but claims no Office family.
 * SUPPORT_DOCUMENTS is still true - the two are different questions, and this one is empty because
 * the conservative first iteration does not promise doc/docx/rtf/odt on a build nobody has yet
 * exercised on a de-googled device.
 */
object OfficeDocumentFamilyCatalog {
    val supportedFamilies: Set<OfficeDocumentFamily> = emptySet()

    val extensionToFamily: Map<String, OfficeDocumentFamily> = emptyMap()
}
