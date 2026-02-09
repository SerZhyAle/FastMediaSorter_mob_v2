package com.sza.fastmediasorter.core.util

/**
 * Format file size to human-readable format with bytes grouped by thousands
 * 
 * Examples:
 * - Small files (< 10KB): "1 234 567 B" (exact bytes with space separators)
 * - Medium files: "45.67 KB", "123.45 MB"
 * - Large files: "2.34 GB"
 */
fun formatFileSize(bytes: Long): String {
    // For small files (< 10KB), show exact bytes with thousand separators
    if (bytes < 10240) {
        val formatted = String.format("%,d", bytes).replace(',', ' ')
        return "$formatted B"
    }
    
    // For larger files, show in KB/MB/GB
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.2f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
