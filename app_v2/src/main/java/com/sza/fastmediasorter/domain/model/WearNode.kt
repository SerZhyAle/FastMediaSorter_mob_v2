package com.sza.fastmediasorter.domain.model

/**
 * GMS-free representation of a connected Wear OS node (paired watch).
 *
 * Keeps the domain `WearableDataLayerRepository` interface free of the Play Services Wearable SDK
 * so non-Wear flavors (and the FOSS flavor, S0403) can bind a no-op without the proprietary SDK on
 * their classpath. The GMS-backed impl in `src/wearGms` maps the GMS node type to this at the
 * boundary.
 */
data class WearNode(val id: String, val displayName: String)
