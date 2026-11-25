package com.sza.fastmediasorter.domain.remotecontrol.verbs

import android.content.Context
import android.os.Process
import com.google.gson.JsonObject
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.AnnounceBroadcastEndUseCase
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.core.playback.BackgroundAudioStopper
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandAnswer
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConfirm
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbExit
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbHandler
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbSpec
import com.sza.fastmediasorter.widget.QuickAudioRecorderService
import com.sza.fastmediasorter.worker.ScheduledOperationsWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * `quit`: a clean close of the app. A file operation or a recording in progress is never cut - the answer is
 * `busy` instead. The close itself runs only after the answer is on the wire, in the order of the full exit:
 * audio playback first (a live foreground service would make the system restart the process), then the ordered
 * end of a live broadcast so its viewers see an end and not a lost connection, then the process.
 */
class QuitRemoteVerb @Inject constructor(
    @ApplicationContext private val context: Context,
    private val broadcast: BroadcastSourceController,
) : RemoteVerbHandler {
    override val spec: RemoteVerbSpec = SPEC

    override fun describe(context: Context, args: JsonObject): String =
        context.getString(R.string.remote_command_verb_quit)

    override suspend fun run(args: JsonObject): RemoteCommandAnswer =
        if (ScheduledOperationsWorker.isRunning || QuickAudioRecorderService.isRecording) {
            RemoteCommandAnswer.busy()
        } else {
            RemoteCommandAnswer.done()
        }

    override suspend fun afterAnswer(answer: RemoteCommandAnswer) {
        if (answer.status != RemoteCommandAnswer.STATUS_DONE) return
        BackgroundAudioStopper.stopIfRunning(context)
        if (broadcast.state.value is BroadcastState.Live) {
            broadcast.stop()
            withTimeoutOrNull(AnnounceBroadcastEndUseCase.EXIT_WAIT_TIMEOUT_MS) {
                broadcast.state.first { it !is BroadcastState.Live }
            }
        }
        Process.killProcess(Process.myPid())
    }

    companion object {
        val SPEC = RemoteVerbSpec(
            name = "quit",
            summary = "Close the app cleanly; a file operation or a recording in progress makes it answer busy.",
            riskClass = RemoteRiskClass.SAFE,
            confirm = RemoteConfirm.GRANT,
            coldStart = RemoteVerbSpec.COLD_START_ONESHOT,
            exitCodes = listOf(
                RemoteVerbExit(RemoteCommandAnswer.EXIT_OK, "the app closes"),
                RemoteVerbExit(RemoteCommandAnswer.EXIT_NOT_READY, "busy with a file operation or a recording"),
            ),
        )
    }
}
