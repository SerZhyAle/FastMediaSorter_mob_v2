package com.sza.fastmediasorter.ui.player

import org.junit.Assert.assertNotNull
import org.junit.Test

class Media3MidiExtensionPresenceTest {

    @Test
    fun `media3 midi extension classes are present on runtime classpath`() {
        assertNotNull(Class.forName("androidx.media3.decoder.midi.MidiExtractor"))
        assertNotNull(Class.forName("androidx.media3.decoder.midi.MidiRenderer"))
    }
}