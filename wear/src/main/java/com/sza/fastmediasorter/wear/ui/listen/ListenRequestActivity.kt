package com.sza.fastmediasorter.wear.ui.listen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * S2550 ADR-6 / S2941: the window the request notification opens (now also via `setFullScreenIntent`),
 * and the only window that may start the microphone for a listening session.
 *
 * It is its own Activity rather than a route inside `MainActivity` because what lifts both platform
 * barriers is the user's interaction with the notification producing this start - routing the tap
 * through the app's entrance would put the navigation stack, and whatever it restores, between the
 * tap and the service creation the exemption is granted for.
 *
 * Not exported: nothing outside this process may open it, which is half of "the phone cannot listen
 * without a tap on the watch". S2941 removes the tap via `setFullScreenIntent`; the foreground
 * notification's `contentIntent` also opens this window so the owner can return to the live screen.
 */
@AndroidEntryPoint
class ListenRequestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearAppTheme {
                ListenRequestScreen(
                    onFinished = ::finish,
                    onDimScreen = ::finish
                )
            }
        }
    }
}
