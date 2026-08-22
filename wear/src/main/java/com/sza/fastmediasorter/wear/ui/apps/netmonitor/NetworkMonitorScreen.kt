package com.sza.fastmediasorter.wear.ui.apps.netmonitor

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
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold

private val PAGE_PADDING = 8.dp

/**
 * One page per applicable section, swiped horizontally.
 *
 * The page set is whatever the state carries: the domain rule already dropped the sections this
 * watch has no hardware for, so a page here never has to ask whether it applies.
 */
@OptIn(ExperimentalFoundationApi::class)
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
                    modifier = Modifier.fillMaxSize().padding(PAGE_PADDING)
                )
            }
            if (pageCount > 1) {
                HorizontalPageIndicator(pageIndicatorState = indicatorState)
            }
        }
    }
}
