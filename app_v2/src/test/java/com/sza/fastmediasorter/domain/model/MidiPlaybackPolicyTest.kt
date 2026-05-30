package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiPlaybackPolicyTest {

    @Test
    fun `isMidiExtension accepts first version MIDI extensions`() {
        assertTrue("Expected mid", MidiPlaybackPolicy.isMidiExtension("mid"))
        assertTrue("Expected midi", MidiPlaybackPolicy.isMidiExtension("midi"))
        assertTrue("Expected MID", MidiPlaybackPolicy.isMidiExtension("MID"))
    }

    @Test
    fun `isMidiPath accepts paths with query strings and fragments`() {
        assertTrue("Expected local MID", MidiPlaybackPolicy.isMidiPath("/storage/emulated/0/Ringtones/Canon.MID"))
        assertTrue("Expected query MIDI", MidiPlaybackPolicy.isMidiPath("content://media/audio/42/song.midi?token=abc"))
        assertTrue("Expected fragment MIDI", MidiPlaybackPolicy.isMidiPath("cloud://googledrive/folder/TwinkleStar.mid#preview"))
    }

    @Test
    fun `isMidiPath rejects out of scope MIDI relatives and other audio`() {
        assertFalse("Expected false for kar", MidiPlaybackPolicy.isMidiPath("song.kar"))
        assertFalse("Expected false for rmi", MidiPlaybackPolicy.isMidiPath("song.rmi"))
        assertFalse("Expected false for xmf", MidiPlaybackPolicy.isMidiPath("song.xmf"))
        assertFalse("Expected false for mp3", MidiPlaybackPolicy.isMidiPath("song.mp3"))
        assertFalse("Expected false for missing extension", MidiPlaybackPolicy.isMidiPath("song"))
    }
}