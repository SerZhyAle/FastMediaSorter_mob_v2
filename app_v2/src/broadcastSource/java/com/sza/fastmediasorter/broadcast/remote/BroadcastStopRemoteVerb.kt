package com.sza.fastmediasorter.broadcast.remote

import android.content.Context
import com.google.gson.JsonObject
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandAnswer
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConfirm
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbHandler
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbSpec
import javax.inject.Inject

/** `broadcast-stop`: the ordered end of a live broadcast; done also when nothing is on air. */
class BroadcastStopRemoteVerb @Inject constructor(
    private val controller: BroadcastSourceController,
) : RemoteVerbHandler {
    override val spec: RemoteVerbSpec = SPEC

    override fun describe(context: Context, args: JsonObject): String =
        context.getString(R.string.remote_command_verb_broadcast_stop)

    override suspend fun run(args: JsonObject): RemoteCommandAnswer {
        // A stop sent to an idle broadcast service has cost the process, so only a live one is stopped.
        if (controller.state.value is BroadcastState.Live) controller.stop()
        return RemoteCommandAnswer.done()
    }

    companion object {
        val SPEC = RemoteVerbSpec(
            name = "broadcast-stop",
            summary = "End the live broadcast of this phone; done also when nothing is on air.",
            riskClass = RemoteRiskClass.SAFE,
            confirm = RemoteConfirm.GRANT,
        )
    }
}
