package com.sza.fastmediasorter.ui.common

/**
 * The colour granularity the canonical media palette can distinguish.
 *
 * S2046: deliberately not a domain type and never serialised. The domain enums it serves keep their
 * own members - MediaType's constant names are the wire format of cached file-list blobs (S1661), so
 * collapsing them to five would change data that earlier releases wrote and later ones read.
 *
 * Adding a member here requires a matching color_media_* pair in both values/colors.xml and
 * values-night/colors.xml; MediaTypeColorCatalogTest fails until that pair exists and is distinct.
 */
enum class MediaColorCategory {
    MUSIC,
    VIDEO,
    IMAGE,
    DOCUMENT,
    OTHER,
}
