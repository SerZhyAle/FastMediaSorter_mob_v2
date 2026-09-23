package com.sza.fastmediasorter.domain.transfer

import java.io.File

/**
 * S3408: the folder a FileDO result is written into when the file it sits beside is not a local path -
 * a document-tree folder or a network share directory.
 *
 * An address is whatever the location uses to name one child again (a document URI, a network path);
 * only the implementation that returned it may interpret it. Every refusal is an [java.io.IOException],
 * so the caller handles one failure type whatever the location.
 */
interface SiblingFolder {

    /** True when a child named [name] exists. */
    suspend fun contains(name: String): Boolean

    /**
     * Writes [source] as a new child named [name] and returns its address. Never overwrites, and leaves
     * no partial child behind when it throws - the caller has no address to clean up yet.
     */
    suspend fun write(source: File, name: String): String

    /** Copies the child at [address] into [target], replacing it. */
    suspend fun read(address: String, target: File)

    /** Renames the child at [address] to [newName] inside this folder and returns its new address. */
    suspend fun rename(address: String, newName: String): String

    suspend fun delete(address: String)
}
