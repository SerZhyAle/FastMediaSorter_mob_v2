package com.sza.fastmediasorter.wear.ui.listen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * S2550 ADR-6: the window the request notification opens, and the only window that may start the
 * microphone for a listening session.
 *
 * It is its own Activity rather than a route inside `MainActivity` because what lifts both platform
 * barriers is the user's interaction with the notification producing this start - routing the tap
 * through the app's entrance would put the navigation stack, and whatever it restores, between the
 * tap and the service creation the exemption is granted for.
 *
 * Not exported: nothing outside this process may open it, which is half of "the phone cannot listen
 * without a tap on the watch".
 */
@AndroidEntryPoint
class ListenRequestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearAppTheme {
                ListenRequestScreen(onFinished = ::finish)
            }
        }
    }
}
