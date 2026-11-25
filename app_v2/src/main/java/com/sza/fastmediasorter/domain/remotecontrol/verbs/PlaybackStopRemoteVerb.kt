package com.sza.fastmediasorter.domain.remotecontrol.verbs

import android.content.Context
import com.google.gson.JsonObject
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.playback.BackgroundAudioStopper
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandAnswer
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConfirm
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbHandler
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** `playback-stop`: ends background audio; the service banks the position in its own teardown. */
class PlaybackStopRemoteVerb @Inject constructor(
    @ApplicationContext private val context: Context,
) : RemoteVerbHandler {
    override val spec: RemoteVerbSpec = SPEC

    override fun describe(context: Context, args: JsonObject): String =
        context.getString(R.string.remote_command_verb_playback_stop)

    override suspend fun run(args: JsonObject): RemoteCommandAnswer {
        BackgroundAudioStopper.stopIfRunning(context)
        return RemoteCommandAnswer.done()
    }

    companion object {
        val SPEC = RemoteVerbSpec(
            name = "playback-stop",
            summary = "Stop the audio that plays on this phone; done also when nothing plays.",
            riskClass = RemoteRiskClass.SAFE,
            confirm = RemoteConfirm.GRANT,
        )
    }
}
