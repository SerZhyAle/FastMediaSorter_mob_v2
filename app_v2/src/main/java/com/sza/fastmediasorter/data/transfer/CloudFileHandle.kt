package com.sza.fastmediasorter.data.transfer

import java.io.File

/** java.io.File wrapper that preserves the cloud display-name and cached size alongside the cloud:// path. */
class CloudFileHandle(
    private val cloudPath: String,
    val displayName: String,
    private val size: Long = 0L,
) : File(cloudPath) {

    override fun getAbsolutePath(): String = cloudPath

    override fun getPath(): String = cloudPath

    override fun getName(): String = displayName

    override fun length(): Long = size
}
