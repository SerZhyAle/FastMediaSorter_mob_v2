package com.sza.fastmediasorter.domain.model

/**
 * Share-file format constants for resource import/export (S0422).
 *
 * The format reuses the bundled-config XML element/attribute names so the existing importer reads
 * exported files unchanged. [ATTR_VERSION] on the root lets future revisions add fields without
 * breaking older files (unknown root attributes are ignored by the parser).
 */
object ResourceShareFormat {
    const val EXTENSION = "fmsr"
    const val MIME_TYPE = "application/vnd.fms.resources+xml"
    const val FORMAT_VERSION = 1
    const val ROOT_TAG = "media-resources"
    const val RESOURCE_TAG = "resource"
    const val ATTR_VERSION = "version"
}
