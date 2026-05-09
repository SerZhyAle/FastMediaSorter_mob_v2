package com.sza.fastmediasorter.data.link.streaming

import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar I (cache lifecycle).
 *
 * Owns the layout of `cacheDir/url-stream/<sessionId>/` directories. Cleanup is
 * mandatory after a successful remux **or** any cancellation/error to avoid
 * leaving partial Media3 cache files behind.
 */
@Singleton
class StreamingCacheCleaner @Inject constructor() {

    fun newSessionId(): String = UUID.randomUUID().toString().substring(0, 8)

    /** Returns `cacheDir/url-stream/<sessionId>/`, creating parent dirs if needed. */
    fun sessionDir(cacheDir: File, sessionId: String): File =
        File(cacheDir, "url-stream/$sessionId").apply { mkdirs() }

    /** Pre-flight free-space gate; reserves a 20% safety margin over the requested bytes. */
    fun preflightCheck(cacheDir: File, requiredBytes: Long): Boolean {
        val target = cacheDir.takeIf { it.exists() } ?: cacheDir.also { it.mkdirs() }
        val usable = target.usableSpace
        val ok = usable >= (requiredBytes * 12 / 10).coerceAtLeast(requiredBytes)
        LinkDownloadTrace.verbose(
            "streaming-cache-cleaner preflight required=$requiredBytes usable=$usable ok=$ok",
        )
        return ok
    }

    fun cleanupSession(cacheDir: File, sessionId: String) {
        val target = File(cacheDir, "url-stream/$sessionId")
        if (!target.exists()) return
        val deleted = target.deleteRecursively()
        LinkDownloadTrace.verbose(
            "streaming-cache-cleaner cleanup session=$sessionId deleted=$deleted",
        )
    }
}
