package com.sza.fastmediasorter.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.databinding.ActivityResourceLaunchWidgetConfigBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.common.compose.FastMediaSorterComposeTheme
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Configuration activity for Resource Launch widget
 * Allows user to select which resource to open from widget
 */
@AndroidEntryPoint
class ResourceLaunchWidgetConfigActivity : BaseActivity<ActivityResourceLaunchWidgetConfigBinding>() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface ResourceWidgetEntryPoint {
        fun database(): AppDatabase
    }

    override fun getViewBinding(): ActivityResourceLaunchWidgetConfigBinding =
        ActivityResourceLaunchWidgetConfigBinding.inflate(layoutInflater)

    override fun keepScreenAwakeFor(settings: AppSettings): Boolean = false

    /** S0289 Phase 09: multimodal surface marker - widget config activity. */
    @Suppress("unused")
    private val multimodalInputSurface: UiSurface = UiSurface.WIDGET_CONFIG

    override fun getInitialFocusView(): View? {
        return binding.widgetConfigComposeView
    }

    /** S0289 Phase 09: route mouse wheel onto the Compose host that owns the LazyColumn list. */
    override fun getMouseScrollTargetView(): View? = binding.widgetConfigComposeView

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED initially
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
    }

    override fun setupViews() {
        // Rule 17: keep the picker inside system-bar + cutout safe bounds in both orientations.
        // BaseActivity enables edge-to-edge but a Scaffold inside a ComposeView host does not
        // consume the window insets on its own; mirror CameraQuickCaptureConfigActivity.
        binding.widgetConfigComposeView.applySystemBarInsetPadding()
        binding.widgetConfigComposeView.setContent {
            FastMediaSorterComposeTheme {
                val entryPoint = EntryPointAccessors.fromApplication(
                    applicationContext,
                    ResourceWidgetEntryPoint::class.java
                )
                ResourceSelectionScreen(
                    database = entryPoint.database(),
                    requestInitialFocus = shouldRequestInitialFocus(),
                    onResourceSelected = { resource ->
                        saveWidgetConfig(resource)
                        updateWidgetAndFinish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    override fun observeData() = Unit

    private fun saveWidgetConfig(resource: ResourceEntity) {
        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        prefs.edit()
            .putLong("resource_id_$appWidgetId", resource.id)
            .putString("resource_name_$appWidgetId", resource.name)
            .putString("resource_path_$appWidgetId", resource.path)
            .putString("resource_type_$appWidgetId", resource.type.name)
            .apply()
    }

    private fun updateWidgetAndFinish() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        ResourceLaunchWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceSelectionScreen(
    database: AppDatabase,
    requestInitialFocus: Boolean,
    onResourceSelected: (ResourceEntity) -> Unit,
    onCancel: () -> Unit
) {
    var resources by remember { mutableStateOf<List<ResourceEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var initialFocusApplied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cancelFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        scope.launch {
            resources = database.resourceDao().getAllResources().first()
            isLoading = false
        }
    }

    LaunchedEffect(requestInitialFocus, isLoading, resources) {
        if (!requestInitialFocus || isLoading || initialFocusApplied) return@LaunchedEffect
        if (resources.isNotEmpty()) {
            firstItemFocusRequester.requestFocus()
        } else {
            cancelFocusRequester.requestFocus()
        }
        initialFocusApplied = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_select_resource)) },
                navigationIcon = {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .focusRequester(cancelFocusRequester)
                            .testTag("resource_config_cancel")
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                resources.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_resources_available),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(resources, key = { _, resource -> resource.id }) { index, resource ->
                            ResourceItem(
                                modifier = if (index == 0) {
                                    Modifier.focusRequester(firstItemFocusRequester)
                                } else {
                                    Modifier
                                },
                                resource = resource,
                                onClick = { onResourceSelected(resource) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResourceItem(
    modifier: Modifier = Modifier,
    resource: ResourceEntity,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .then(modifier)
            .testTag("resource_config_item")
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = resource.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = resource.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    when (resource.type) {
                        com.sza.fastmediasorter.domain.model.ResourceType.LOCAL -> R.string.resource_type_local
                        com.sza.fastmediasorter.domain.model.ResourceType.SMB -> R.string.resource_type_smb
                        com.sza.fastmediasorter.domain.model.ResourceType.SFTP -> R.string.resource_type_sftp
                        com.sza.fastmediasorter.domain.model.ResourceType.FTP -> R.string.resource_type_ftp
                        com.sza.fastmediasorter.domain.model.ResourceType.CLOUD -> R.string.resource_type_cloud
                        com.sza.fastmediasorter.domain.model.ResourceType.HTTP_STREAM,
                        com.sza.fastmediasorter.domain.model.ResourceType.RTSP_STREAM -> R.string.resource_type_stream
                        com.sza.fastmediasorter.domain.model.ResourceType.WEAR_WATCH ->
                            R.string.resource_type_wear_watch
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
