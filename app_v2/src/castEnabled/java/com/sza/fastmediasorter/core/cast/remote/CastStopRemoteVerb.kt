package com.sza.fastmediasorter.core.cast.remote

import android.content.Context
import com.google.gson.JsonObject
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cast.ActiveCastControllerHolder
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandAnswer
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConfirm
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbHandler
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** `cast-stop`: ends the cast of the player screen that is up; done also when nothing is cast. */
class CastStopRemoteVerb @Inject constructor(
    private val holder: ActiveCastControllerHolder,
) : RemoteVerbHandler {
    override val spec: RemoteVerbSpec = SPEC

    override fun describe(context: Context, args: JsonObject): String =
        context.getString(R.string.remote_command_verb_cast_stop)

    override suspend fun run(args: JsonObject): RemoteCommandAnswer {
        // The cast session belongs to the player screen and is driven from the main thread only.
        withContext(Dispatchers.Main) { holder.current()?.stopCasting() }
        return RemoteCommandAnswer.done()
    }

    companion object {
        val SPEC = RemoteVerbSpec(
            name = "cast-stop",
            summary = "Stop casting from this phone to a screen; done also when nothing is cast.",
            riskClass = RemoteRiskClass.SAFE,
            confirm = RemoteConfirm.GRANT,
        )
    }
}
