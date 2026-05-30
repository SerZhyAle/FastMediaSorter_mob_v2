package com.sza.fastmediasorter.data.common

object OfficeDocumentFamilyCatalog {
    val supportedFamilies: Set<OfficeDocumentFamily> = setOf(OfficeDocumentFamily.WORD)

    val extensionToFamily: Map<String, OfficeDocumentFamily> = mapOf(
        "doc" to OfficeDocumentFamily.WORD,
        "docx" to OfficeDocumentFamily.WORD,
        "rtf" to OfficeDocumentFamily.WORD,
        "odt" to OfficeDocumentFamily.WORD,
    )
}