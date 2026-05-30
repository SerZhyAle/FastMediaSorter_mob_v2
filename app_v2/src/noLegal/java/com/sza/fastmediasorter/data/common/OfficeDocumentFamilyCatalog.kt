package com.sza.fastmediasorter.data.common

object OfficeDocumentFamilyCatalog {
    val supportedFamilies: Set<OfficeDocumentFamily> = setOf(
        OfficeDocumentFamily.WORD,
        OfficeDocumentFamily.SPREADSHEET,
        OfficeDocumentFamily.PRESENTATION,
    )

    val extensionToFamily: Map<String, OfficeDocumentFamily> = mapOf(
        "doc" to OfficeDocumentFamily.WORD,
        "docx" to OfficeDocumentFamily.WORD,
        "rtf" to OfficeDocumentFamily.WORD,
        "odt" to OfficeDocumentFamily.WORD,
        "xls" to OfficeDocumentFamily.SPREADSHEET,
        "xlsx" to OfficeDocumentFamily.SPREADSHEET,
        "ods" to OfficeDocumentFamily.SPREADSHEET,
        "ppt" to OfficeDocumentFamily.PRESENTATION,
        "pptx" to OfficeDocumentFamily.PRESENTATION,
        "odp" to OfficeDocumentFamily.PRESENTATION,
    )
}