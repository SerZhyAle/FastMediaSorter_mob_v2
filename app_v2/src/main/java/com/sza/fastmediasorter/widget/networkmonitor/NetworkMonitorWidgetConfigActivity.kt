package com.sza.fastmediasorter.widget.networkmonitor

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.databinding.ActivityNetworkMonitorWidgetConfigBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.common.compose.FastMediaSorterComposeTheme
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.widget.ResourceSelectionScreen
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S1440: asks which of the eight indicators a freshly placed widget should show.
 *
 * Strategic 4.4 makes the indicator a per-instance choice, so this screen exists for the same reason
 * the three other configurable widgets have one: the answer is keyed by `EXTRA_APPWIDGET_ID` and
 * cannot live in settings.
 *
 * Binding and routing only (CLAUDE.md Rule 3) - the eight-way table belongs to
 * [NetworkMonitorIndicator] and the persistence to [NetworkMonitorWidgetIndicatorStore].
 */
@AndroidEntryPoint
class NetworkMonitorWidgetConfigActivity : BaseActivity<ActivityNetworkMonitorWidgetConfigBinding>() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface NetworkMonitorWidgetConfigEntryPoint {
        fun database(): AppDatabase
    }

    override fun getViewBinding(): ActivityNetworkMonitorWidgetConfigBinding =
        ActivityNetworkMonitorWidgetConfigBinding.inflate(layoutInflater)

    override fun keepScreenAwakeFor(settings: AppSettings): Boolean = false

    /** S0289 Phase 09: multimodal surface marker - widget config activity. */
    @Suppress("unused")
    private val multimodalInputSurface: UiSurface = UiSurface.WIDGET_CONFIG

    override fun getInitialFocusView(): View? = binding.widgetNetworkMonitorConfigComposeView

    override fun getMouseScrollTargetView(): View? = binding.widgetNetworkMonitorConfigComposeView

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The launcher treats anything but RESULT_OK as "the user backed out", which is the correct
        // answer for every path out of here except the confirmation below.
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
        }
    }

    override fun setupViews() {
        // Rule 17: a Scaffold inside a ComposeView host does not consume window insets by itself.
        binding.widgetNetworkMonitorConfigComposeView.applySystemBarInsetPadding()
        binding.widgetNetworkMonitorConfigComposeView.setContent {
            FastMediaSorterComposeTheme {
                val entryPoint = EntryPointAccessors.fromApplication(
                    applicationContext,
                    NetworkMonitorWidgetConfigEntryPoint::class.java
                )
                NetworkMonitorIndicatorPickerScreen(
                    database = entryPoint.database(),
                    requestInitialFocus = shouldRequestInitialFocus(),
                    onConfigured = ::commitAndFinish,
                    onCancel = { finish() }
                )
            }
        }
    }

    override fun observeData() = Unit

    private fun commitAndFinish(indicator: NetworkMonitorIndicator, resourceId: Long?) {
        NetworkMonitorWidgetIndicatorStore.write(this, appWidgetId, indicator)
        if (resourceId != null) {
            NetworkMonitorWidgetIndicatorStore.writeResourceId(this, appWidgetId, resourceId)
        }
        val manager = AppWidgetManager.getInstance(this)
        lifecycleScope.launch {
            // The first render collects a cold indicator flow; keep that off the main thread.
            withContext(Dispatchers.IO) {
                NetworkMonitorWidgetProvider.updateAppWidget(applicationContext, manager, appWidgetId)
            }
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        }
    }
}

private val LIST_PADDING = 16.dp
private val LIST_ITEM_SPACING = 8.dp
private val ROW_ICON_SIZE = 32.dp

/**
 * The indicator list, followed by the existing resource picker when the chosen indicator needs one.
 *
 * `RESOURCE_REACHABILITY` is the only indicator that carries a second answer, and the picker it needs
 * is the same Composable the resource-launch widget already configures itself with.
 */
@Composable
fun NetworkMonitorIndicatorPickerScreen(
    database: AppDatabase,
    requestInitialFocus: Boolean,
    onConfigured: (NetworkMonitorIndicator, Long?) -> Unit,
    onCancel: () -> Unit
) {
    var pendingResourceIndicator by remember { mutableStateOf<NetworkMonitorIndicator?>(null) }
    val pending = pendingResourceIndicator
    if (pending == null) {
        IndicatorList(
            requestInitialFocus = requestInitialFocus,
            onCancel = onCancel,
            onPicked = { indicator ->
                if (indicator == NetworkMonitorIndicator.RESOURCE_REACHABILITY) {
                    pendingResourceIndicator = indicator
                } else {
                    onConfigured(indicator, null)
                }
            }
        )
    } else {
        ResourceSelectionScreen(
            database = database,
            requestInitialFocus = requestInitialFocus,
            onResourceSelected = { resource -> onConfigured(pending, resource.id) },
            // Backing out of the picker returns to the indicator list rather than abandoning setup.
            onCancel = { pendingResourceIndicator = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndicatorList(
    requestInitialFocus: Boolean,
    onPicked: (NetworkMonitorIndicator) -> Unit,
    onCancel: () -> Unit
) {
    val cancelFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            firstItemFocusRequester.requestFocus()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_network_monitor_config_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .focusRequester(cancelFocusRequester)
                            .testTag("network_config_cancel")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(LIST_PADDING),
            verticalArrangement = Arrangement.spacedBy(LIST_ITEM_SPACING)
        ) {
            itemsIndexed(
                items = NetworkMonitorIndicator.entries,
                key = { _, indicator -> indicator.key }
            ) { index, indicator ->
                IndicatorRow(
                    modifier = if (index == 0) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    },
                    indicator = indicator,
                    onClick = { onPicked(indicator) }
                )
            }
        }
    }
}

@Composable
private fun IndicatorRow(
    modifier: Modifier = Modifier,
    indicator: NetworkMonitorIndicator,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .testTag("network_config_indicator_item")
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LIST_PADDING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(indicator.iconRes),
                contentDescription = null,
                modifier = Modifier.size(ROW_ICON_SIZE),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(LIST_ITEM_SPACING))
            Text(
                text = stringResource(indicator.labelRes),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
