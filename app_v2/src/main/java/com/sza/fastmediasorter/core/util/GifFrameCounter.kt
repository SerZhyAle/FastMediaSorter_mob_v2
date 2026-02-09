package com.sza.fastmediasorter.core.util

import timber.log.Timber
import java.io.File
import java.io.FileInputStream

/**
 * Helper object for counting frames in GIF files.
 * Parses GIF binary format to determine animation frame count.
 * Supports extrapolation for partially downloaded files.
 */
object GifFrameCounter {
    
    /**
     * Count frames in GIF file with optional extrapolation for partial files.
     * 
     * @param file GIF file to analyze
     * @param totalFileSize Total file size (for extrapolation), or null if file is complete
     * @return Number of frames (extrapolated if partial), or 0 if parsing fails
     */
    fun countFrames(file: File, totalFileSize: Long? = null): Int {
        val parsedFrames = countFramesInternal(file)
        
        // If file is partial and we know total size, extrapolate
        if (totalFileSize != null && totalFileSize > file.length() && parsedFrames > 0) {
            val downloadedSize = file.length()
            val estimatedTotal = (parsedFrames * totalFileSize / downloadedSize).toInt()
            Timber.d("GifFrameCounter: Extrapolating - parsed $parsedFrames frames from ${downloadedSize / 1024}KB, " +
                     "total file ${totalFileSize / 1024}KB → estimated ~$estimatedTotal frames")
            return estimatedTotal
        }
        
        return parsedFrames
    }
    
    /**
     * Count the number of frames in a GIF file by parsing its structure.
     * Returns 0 if file is invalid or parsing fails.
     * 
     * @param file GIF file to analyze
     * @return Number of frames found in file, or 0 if parsing fails
     */
    private fun countFramesInternal(file: File): Int {
        try {
            FileInputStream(file).use { stream ->
                // Skip header (6 bytes) + Logical Screen Descriptor (7 bytes)
                if (stream.skip(13) != 13L) return 0
                
                // Check for Global Color Table
                // We need to read the packed field from Logical Screen Descriptor to know if GCT exists
                // But we skipped it. Let's re-read properly.
            }
            // Re-open for proper parsing
            FileInputStream(file).use { stream ->
                // Header: GIF89a (6 bytes)
                stream.skip(6)
                
                // Logical Screen Descriptor (7 bytes)
                // Width (2), Height (2), Packed (1), BgColor (1), Aspect (1)
                val lsd = ByteArray(7)
                if (stream.read(lsd) != 7) return 0
                
                val packed = lsd[4].toInt()
                val hasGct = (packed and 0x80) != 0
                val gctSize = 2 shl (packed and 0x07)
                
                if (hasGct) {
                    stream.skip((3 * gctSize).toLong())
                }
                
                var frameCount = 0
                var blockType: Int
                
                while (true) {
                    blockType = stream.read()
                    if (blockType == -1 || blockType == 0x3B) { // Trailer
                        break
                    }
                    
                    if (blockType == 0x2C) { // Image Descriptor
                        frameCount++
                        // Skip Image Descriptor (9 bytes)
                        // Left(2), Top(2), Width(2), Height(2), Packed(1)
                        val id = ByteArray(9)
                        if (stream.read(id) != 9) break
                        
                        val idPacked = id[8].toInt()
                        val hasLct = (idPacked and 0x80) != 0
                        val lctSize = 2 shl (idPacked and 0x07)
                        
                        if (hasLct) {
                            stream.skip((3 * lctSize).toLong())
                        }
                        
                        // Skip Image Data
                        // LZW Minimum Code Size (1 byte)
                        stream.read()
                        
                        // Data Sub-blocks
                        while (true) {
                            val blockSize = stream.read()
                            if (blockSize == -1 || blockSize == 0) break
                            stream.skip(blockSize.toLong())
                        }
                    } else if (blockType == 0x21) { // Extension
                        val label = stream.read()
                        if (label == -1) break
                        
                        // Skip Extension Blocks
                        while (true) {
                            val blockSize = stream.read()
                            if (blockSize == -1 || blockSize == 0) break
                            stream.skip(blockSize.toLong())
                        }
                    } else {
                        // Unknown block, shouldn't happen in valid GIF
                        break
                    }
                }
                return frameCount
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to count GIF frames: ${file.path}")
            return 0
        }
    }
}
