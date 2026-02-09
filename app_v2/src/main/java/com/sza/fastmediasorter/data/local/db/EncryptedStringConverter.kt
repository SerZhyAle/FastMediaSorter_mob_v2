package com.sza.fastmediasorter.data.local.db

/**
 * Wrapper class for encrypted strings to distinguish them from regular strings in Room.
 * Room requires unique types for TypeConverters to avoid conflicts.
 */
data class EncryptedString(val encrypted: String)

