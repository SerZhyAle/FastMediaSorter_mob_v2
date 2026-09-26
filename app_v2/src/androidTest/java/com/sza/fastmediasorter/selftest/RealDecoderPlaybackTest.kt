package com.sza.fastmediasorter.selftest

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * S3741: the device's own decoders prepare the fixture media. JVM tests never touch a codec, so a
 * decoder that refuses a stream on real hardware is visible only here.
 */
@RunWith(JUnit4::class)
class RealDecoderPlaybackTest {

    // Constrained Baseline H.264 + AAC-LC, which every hardware decoder must accept. The s0116 .ts
    // fixture is High 4:4:4 (avc1.F4000A) - fine for the remux tests it was made for, refused by the
    // Galaxy S21+ decoder with NO_EXCEEDS_CAPABILITIES, so it cannot judge playback.
    @Test
    fun baselineAvcAacMp4ReachesReady() {
        val tracks = prepare("selftest/selftest_baseline_avc_aac.mp4")
        assertTrue("video track selected", C.TRACK_TYPE_VIDEO in tracks)
        assertTrue("audio track selected", C.TRACK_TYPE_AUDIO in tracks)
    }

    @Test
    fun opusWebmReachesReady() {
        val tracks = prepare("s0116_fixtures/tiny_opus.webm")
        assertTrue("audio track selected", C.TRACK_TYPE_AUDIO in tracks)
    }

    /** Returns the selected track types once the player is READY; fails on an error or a timeout. */
    private fun prepare(asset: String): Set<Int> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val file = File(target.cacheDir, asset.substringAfterLast('/'))
        instrumentation.context.assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } }

        val done = CountDownLatch(1)
        var error: PlaybackException? = null
        var state = Player.STATE_IDLE
        val selected = mutableSetOf<Int>()
        lateinit var player: ExoPlayer
        val main = Handler(Looper.getMainLooper())
        main.post {
            player = ExoPlayer.Builder(target).build()
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    state = playbackState
                    if (playbackState == Player.STATE_READY) {
                        player.currentTracks.groups.filter { it.isSelected }.forEach { selected += it.type }
                        done.countDown()
                    }
                }

                override fun onPlayerError(e: PlaybackException) {
                    error = e
                    done.countDown()
                }
            })
            player.setMediaItem(MediaItem.fromUri(file.toURI().toString()))
            player.prepare()
        }
        val finished = done.await(PREPARE_TIMEOUT_S, TimeUnit.SECONDS)
        main.post { player.release() }
        file.delete()
        assertTrue("$asset did not reach READY within $PREPARE_TIMEOUT_S s", finished)
        assertNull("$asset player error: ${error?.errorCodeName}", error)
        assertEquals(Player.STATE_READY, state)
        return selected
    }

    private companion object {
        const val PREPARE_TIMEOUT_S = 20L
    }
}
