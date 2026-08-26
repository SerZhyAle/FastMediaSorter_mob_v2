package com.sza.fastmediasorter.wear.domain.model

/**
 * What kind of thing a cell stands for, independent of how it is drawn.
 *
 * The glyph and the semantic tone for each entry live in `ui/common/ContentTypeCatalog`, not here:
 * the module keeps drawable ids and Compose types out of the domain layer, which is the same reason
 * the home screen holds its own section icons rather than hanging them on the section model.
 *
 * S2003: before this enum the same handful of types was described on three screens in three
 * unrelated vocabularies - emoji, generic platform vectors, and no icon at all - with no single
 * point of truth to correct.
 */
enum class WearContentType {
    MUSIC,
    VIDEO,
    IMAGE,
    DOCUMENT,
    FOLDER,
    STREAM,
    OTHER
}
