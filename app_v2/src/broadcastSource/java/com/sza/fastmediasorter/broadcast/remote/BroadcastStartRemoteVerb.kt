package com.sza.fastmediasorter.broadcast.remote

import android.content.Context
import com.google.gson.JsonObject
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.domain.remotecontrol.RemoteArgKind
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandAnswer
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConfirm
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbArg
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbExit
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbHandler
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbSpec
import com.sza.fastmediasorter.domain.usecase.broadcast.CheckPreparedBroadcastStartUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * `broadcast-start`: starts the broadcast prepared on this phone. It sends the camera or the microphone off the
 * machine (`leaves-machine`), and `confirm: device` keeps every start behind the person's own tap, which is also
 * what lets the broadcast's foreground service start while the app is in the background.
 */
class BroadcastStartRemoteVerb @Inject constructor(
    private val controller: BroadcastSourceController,
    private val checkPrepared: CheckPreparedBroadcastStartUseCase,
) : RemoteVerbHandler {
    override val spec: RemoteVerbSpec = SPEC

    override fun describe(context: Context, args: JsonObject): String {
        val mode = when (modeOf(args)) {
            BroadcastMode.AUDIO_ONLY -> R.string.remote_command_mode_audio
            BroadcastMode.VIDEO_ONLY -> R.string.remote_command_mode_video
            else -> R.string.remote_command_mode_video_audio
        }
        return context.getString(R.string.remote_command_verb_broadcast_start, context.getString(mode))
    }

    override suspend fun run(args: JsonObject): RemoteCommandAnswer {
        val mode = modeOf(args)
        if (mode == null || checkPrepared(mode) != CheckPreparedBroadcastStartUseCase.Verdict.Matches) {
            return RemoteCommandAnswer.targetMissing()
        }
        controller.start(mode)
        val settled = withTimeoutOrNull(START_TIMEOUT_MILLIS) {
            controller.state.first { it is BroadcastState.Live || it is BroadcastState.Failed }
        }
        return if (settled is BroadcastState.Live) {
            RemoteCommandAnswer.done()
        } else {
            RemoteCommandAnswer(
                RemoteCommandAnswer.STATUS_FAILED,
                RemoteCommandAnswer.EXIT_FAILED,
                message = "The broadcast did not start.",
            )
        }
    }

    private fun modeOf(args: JsonObject): BroadcastMode? = when (args.get(ARG_MODE)?.asString) {
        MODE_AUDIO -> BroadcastMode.AUDIO_ONLY
        MODE_VIDEO_AUDIO -> BroadcastMode.VIDEO_AUDIO
        MODE_VIDEO -> BroadcastMode.VIDEO_ONLY
        else -> null
    }

    companion object {
        private const val ARG_MODE = "mode"
        private const val MODE_AUDIO = "audio"
        private const val MODE_VIDEO_AUDIO = "video-audio"
        private const val MODE_VIDEO = "video"

        // As for the watch's camera start: the camera and the encoder open on a cold start within it.
        private const val START_TIMEOUT_MILLIS = 15_000L

        val SPEC = RemoteVerbSpec(
            name = "broadcast-start",
            summary = "Start the prepared broadcast of this phone after the person on the phone confirms it.",
            riskClass = RemoteRiskClass.LEAVES_MACHINE,
            confirm = RemoteConfirm.DEVICE,
            args = listOf(
                RemoteVerbArg(
                    name = ARG_MODE,
                    kind = RemoteArgKind.ENUM,
                    description = "what the broadcast sends",
                    required = true,
                    values = listOf(MODE_AUDIO, MODE_VIDEO_AUDIO, MODE_VIDEO),
                ),
            ),
            idempotent = false,
            exitCodes = listOf(
                RemoteVerbExit(RemoteCommandAnswer.EXIT_OK, "the broadcast is live"),
                RemoteVerbExit(RemoteCommandAnswer.EXIT_FAILED, "the broadcast did not start"),
                RemoteVerbExit(RemoteCommandAnswer.EXIT_NOT_READY, "nothing is prepared for this mode"),
            ),
        )
    }
}
