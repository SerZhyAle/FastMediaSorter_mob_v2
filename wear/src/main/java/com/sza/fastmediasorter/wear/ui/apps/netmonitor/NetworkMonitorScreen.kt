package com.sza.fastmediasorter.wear.ui.apps.netmonitor

import android.Manifest
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.HorizontalPageIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PageIndicatorState
import androidx.wear.compose.material.Text
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import timber.log.Timber

private val PAGE_PADDING = 8.dp

/**
 * One page per applicable section, swiped horizontally.
 *
 * The page set is whatever the state carries: the domain rule already dropped the sections this
 * watch has no hardware for, so a page here never has to ask whether it applies.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun NetworkMonitorScreen(
    viewModel: NetworkMonitorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pageCount = state.sections.size
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val indicatorState = remember(pagerState, pageCount) {
        object : PageIndicatorState {
            override val pageOffset: Float get() = pagerState.currentPageOffsetFraction
            override val selectedPage: Int get() = pagerState.currentPage
            override val pageCount: Int get() = pageCount
        }
    }
    // Held once for the whole pager rather than per page: seven pages asking separately would be
    // seven independent launchers for one answer.
    val requestable = remember { requestablePermissions() }
    val permissionsState = rememberMultiplePermissionsState(permissions = requestable)
    val canRequestPermissions = requestable.isNotEmpty() && !permissionsState.allPermissionsGranted

    WearScreenScaffold {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (pageCount == 0) {
                Text(
                    text = stringResource(R.string.wear_netmon_unavailable),
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(PAGE_PADDING)
                )
                return@Column
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { page ->
                NetworkMonitorSectionPage(
                    section = state.sections[page],
                    state = state,
                    canRequestPermissions = canRequestPermissions,
                    onRequestPermissions = {
                        Timber.d("S2008: netmon permission request for ${requestable.size} permission(s)")
                        permissionsState.launchMultiplePermissionRequest()
                    },
                    modifier = Modifier.fillMaxSize().padding(PAGE_PADDING)
                )
            }
            if (pageCount > 1) {
                HorizontalPageIndicator(pageIndicatorState = indicatorState)
            }
        }
    }
}

/**
 * The permissions the sampling reads and that the platform can still be asked for here.
 *
 * Both are declared in the manifest and read by the repository, but nothing ever requested them, so
 * every field behind them read "unavailable" for the life of the install (S2008). Below the API level
 * that introduced one, a request raises no dialog at all - the set is empty there and the screen
 * offers no control rather than a button that does nothing.
 */
private fun requestablePermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}
