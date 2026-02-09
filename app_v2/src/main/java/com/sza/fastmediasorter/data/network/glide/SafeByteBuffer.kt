package com.sza.fastmediasorter.data.network.glide

import java.nio.ByteBuffer

/**
 * Wrapper around ByteBuffer to be used as a distinct type in Glide registry.
 * This prevents Glide's default Downsampler from picking it up and wrapping it
 * in RecyclableBufferedInputStream (which causes InvalidMarkException on >5MB files).
 */
data class SafeByteBuffer(val buffer: ByteBuffer)
