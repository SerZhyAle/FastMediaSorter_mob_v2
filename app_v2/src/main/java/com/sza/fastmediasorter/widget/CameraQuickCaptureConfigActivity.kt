package com.sza.fastmediasorter.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.databinding.ActivityCameraQuickCaptureWidgetConfigBinding
import com.sza.fastmediasorter.ui.common.input.InputSurface
import com.sza.fastmediasorter.util.VirtualPathUtils
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S0369 - configuration activity for the quick camera-capture widget. The user picks one target:
 * a writable non-virtual resource or the synthetic "device camera folder" pseudo-target shown as
 * the first row. The chosen target is persisted per [appWidgetId] under the `cam_capture_*` key
 * namespace.
 *
 * Mirrors [ResourceLaunchWidgetConfigActivity]'s Compose host pattern; Rule 18 system-bar safety
 * is applied via [applySystemBarInsetPadding] on the Compose host before the Scaffold renders.
 */
@AndroidEntryPoint
class CameraQuickCaptureConfigActivity : BaseActivity<ActivityCameraQuickCaptureWidgetConfigBinding>() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface CameraQuickCaptureWidgetEntryPoint {
        fun database(): AppDatabase
    }

    override fun getViewBinding(): ActivityCameraQuickCaptureWidgetConfigBinding =
        ActivityCameraQuickCaptureWidgetConfigBinding.inflate(layoutInflater)

    override fun keepScreenAwakeFor(settings: AppSettings): Boolean = false

    /** Multimodal surface marker - widget config activity (mirrors the resource-launch config). */
    @Suppress("unused")
    private val multimodalInputSurface: InputSurface = InputSurface.WIDGET_CONFIG

    override fun getInitialFocusView(): View? = binding.cameraWidgetConfigComposeView

    override fun getMouseScrollTargetView(): View? = binding.cameraWidgetConfigComposeView

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cancelled by default: a back-press before selection leaves no half-configured widget.
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
        // Rule 18: keep the picker inside system-bar + cutout safe bounds in both orientations.
        binding.cameraWidgetConfigComposeView.applySystemBarInsetPadding()
        binding.cameraWidgetConfigComposeView.setContent {
            MaterialTheme {
                val entryPoint = EntryPointAccessors.fromApplication(
                    applicationContext,
                    CameraQuickCaptureWidgetEntryPoint::class.java
                )
                CameraTargetSelectionScreen(
                    database = entryPoint.database(),
                    requestInitialFocus = shouldRequestInitialFocus(),
                    onCameraFolderSelected = { isVideo ->
                        saveCameraFolderConfig(isVideo)
                        updateWidgetAndFinish()
                    },
                    onResourceSelected = { resource, isVideo ->
                        saveResourceConfig(resource, isVideo)
                        updateWidgetAndFinish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    override fun observeData() = Unit

    private fun saveResourceConfig(resource: ResourceEntity, isVideo: Boolean) {
        getSharedPreferences(CameraQuickCaptureWidgetProvider.PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(CameraQuickCaptureWidgetProvider.keyTargetIsCameraFolder(appWidgetId), false)
            .putLong(CameraQuickCaptureWidgetProvider.keyTargetId(appWidgetId), resource.id)
            .putString(CameraQuickCaptureWidgetProvider.keyTargetName(appWidgetId), resource.name)
            .putString(CameraQuickCaptureWidgetProvider.keyTargetPath(appWidgetId), resource.path)
            .putString(CameraQuickCaptureWidgetProvider.keyTargetType(appWidgetId), resource.type.name)
            .putString(CameraQuickCaptureWidgetProvider.keyCaptureMode(appWidgetId), captureModePref(isVideo))
            .apply()
    }

    private fun saveCameraFolderConfig(isVideo: Boolean) {
        getSharedPreferences(CameraQuickCaptureWidgetProvider.PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(CameraQuickCaptureWidgetProvider.keyTargetIsCameraFolder(appWidgetId), true)
            .remove(CameraQuickCaptureWidgetProvider.keyTargetId(appWidgetId))
            .remove(CameraQuickCaptureWidgetProvider.keyTargetName(appWidgetId))
            .remove(CameraQuickCaptureWidgetProvider.keyTargetPath(appWidgetId))
            .remove(CameraQuickCaptureWidgetProvider.keyTargetType(appWidgetId))
            .putString(CameraQuickCaptureWidgetProvider.keyCaptureMode(appWidgetId), captureModePref(isVideo))
            .apply()
    }

    private fun captureModePref(isVideo: Boolean): String =
        if (isVideo) CameraQuickCaptureWidgetProvider.CAPTURE_MODE_VIDEO
        else CameraQuickCaptureWidgetProvider.CAPTURE_MODE_PHOTO

    private fun updateWidgetAndFinish() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        CameraQuickCaptureWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraTargetSelectionScreen(
    database: AppDatabase,
    requestInitialFocus: Boolean,
    onCameraFolderSelected: (isVideo: Boolean) -> Unit,
    onResourceSelected: (resource: ResourceEntity, isVideo: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var resources by remember { mutableStateOf<List<ResourceEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var initialFocusApplied by remember { mutableStateOf(false) }
    // S0371: capture mode chosen before the target; default photo (matches pre-S0371 behaviour).
    var isVideo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cancelFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        scope.launch {
            // Only writable, non-virtual resources are eligible targets (S0369 §3.2): a virtual
            // aggregate (incl. Camera Photos) is never a direct writable destination.
            resources = database.resourceDao().getAllResources().first()
                .filter { !it.isReadOnly && !VirtualPathUtils.isVirtualPath(it.path) }
            isLoading = false
        }
    }

    LaunchedEffect(requestInitialFocus, isLoading) {
        if (!requestInitialFocus || isLoading || initialFocusApplied) return@LaunchedEffect
        // The camera-folder row is always present, so it always owns initial focus.
        firstItemFocusRequester.requestFocus()
        initialFocusApplied = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_camera_quick_capture_pick_target)) },
                navigationIcon = {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.focusRequester(cancelFocusRequester)
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
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        CaptureModeSelector(
                            isVideo = isVideo,
                            onModeChange = { isVideo = it }
                        )
                    }
                    item {
                        CameraFolderItem(
                            modifier = Modifier.focusRequester(firstItemFocusRequester),
                            onClick = { onCameraFolderSelected(isVideo) }
                        )
                    }
                    itemsIndexed(resources, key = { _, resource -> resource.id }) { _, resource ->
                        ResourceItem(
                            resource = resource,
                            onClick = { onResourceSelected(resource, isVideo) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * S0371: photo/video mode selector shown above the target list. Two filter chips, focusable for
 * D-pad / keyboard parity with the rest of the picker; the default is Photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureModeSelector(
    isVideo: Boolean,
    onModeChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.widget_camera_quick_capture_mode_label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !isVideo,
                onClick = { onModeChange(false) },
                label = { Text(stringResource(R.string.widget_camera_quick_capture_mode_photo)) }
            )
            FilterChip(
                selected = isVideo,
                onClick = { onModeChange(true) },
                label = { Text(stringResource(R.string.widget_camera_quick_capture_mode_video)) }
            )
        }
    }
}

/** Synthetic first row that binds the widget to the device camera folder (DCIM/Camera). */
@Composable
fun CameraFolderItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.widget_camera_quick_capture_camera_folder),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
