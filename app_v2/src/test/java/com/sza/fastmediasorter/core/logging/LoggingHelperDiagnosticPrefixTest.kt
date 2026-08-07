package com.sza.fastmediasorter.core.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1468: the release file tree persists WARN and above, so stream and audio diagnostics emitted at
 * INFO/DEBUG never reached the log a user sends us. This pins the exemption list that lets them
 * through, and pins that it matches the start of the message rather than any occurrence of it.
 */
class LoggingHelperDiagnosticPrefixTest {

    @Test
    fun `stream session summary is persisted`() {
        assertTrue(isAlwaysPersistedDiagnostic("Stream session: ttff=812ms stalls=3 dropped=0"))
    }

    @Test
    fun `stream diag lines are persisted`() {
        assertTrue(isAlwaysPersistedDiagnostic("Stream diag: first frame ttff=812ms path=http://x"))
        assertTrue(isAlwaysPersistedDiagnostic("Stream diag: decoder=c2.mtk.avc.decoder (hw)"))
    }

    @Test
    fun `stream quality lines are persisted`() {
        assertTrue(isAlwaysPersistedDiagnostic("Stream quality: renditions=4 single=false"))
        assertTrue(isAlwaysPersistedDiagnostic("Stream quality: stepped down to <=1280x720 @1500bps"))
    }

    @Test
    fun `stream state transitions are persisted`() {
        assertTrue(isAlwaysPersistedDiagnostic("Stream state=BUFFERING reconnecting=true"))
    }

    @Test
    fun `audio diag lines are persisted`() {
        assertTrue(isAlwaysPersistedDiagnostic("Audio diag: decoder initialized name=c2.android.aac"))
    }

    @Test
    fun `an unrelated message stays subject to the threshold`() {
        assertFalse(isAlwaysPersistedDiagnostic("Thumbnail cache hit ratio=0.82"))
    }

    @Test
    fun `an empty message stays subject to the threshold`() {
        assertFalse(isAlwaysPersistedDiagnostic(""))
    }

    @Test
    fun `a prefix appearing mid-message does not exempt it`() {
        // Containment would let any line quoting a diagnostic bypass the threshold.
        assertFalse(isAlwaysPersistedDiagnostic("Retry after Stream diag: decoder=sw"))
    }
}
