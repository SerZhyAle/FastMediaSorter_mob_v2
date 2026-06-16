package com.sza.fastmediasorter.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.databinding.ActivityResourceLaunchWidgetConfigBinding
import com.sza.fastmediasorter.ui.common.input.InputSurface
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class RandomPhotoFrameConfigActivity : BaseActivity<ActivityResourceLaunchWidgetConfigBinding>() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface RandomPhotoFrameConfigEntryPoint {
        fun database(): AppDatabase
    }

    override fun getViewBinding(): ActivityResourceLaunchWidgetConfigBinding =
        ActivityResourceLaunchWidgetConfigBinding.inflate(layoutInflater)

    override fun keepScreenAwakeFor(settings: AppSettings): Boolean = false

    @Suppress("unused")
    private val multimodalInputSurface: InputSurface = InputSurface.WIDGET_CONFIG

    override fun getInitialFocusView(): View? = binding.widgetConfigComposeView

    override fun getMouseScrollTargetView(): View? = binding.widgetConfigComposeView

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        binding.widgetConfigComposeView.setContent {
            MaterialTheme {
                val entryPoint = EntryPointAccessors.fromApplication(
                    applicationContext,
                    RandomPhotoFrameConfigEntryPoint::class.java
                )
                val database = entryPoint.database()
                val resourcesFlow = remember(database) { database.resourceDao().getAllResources() }
                // Reuse the existing generic resource picker so widget config stays behaviorally aligned.
                key(resourcesFlow) {
                    ResourceSelectionScreen(
                        database = database,
                        requestInitialFocus = shouldRequestInitialFocus(),
                        onResourceSelected = { resource ->
                            triggerInitialRefresh(resource)
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    override fun observeData() = Unit

    private fun saveWidgetConfig(resource: ResourceEntity) {
        RandomPhotoFrameSnapshotStore.write(
            this,
            appWidgetId,
            RandomPhotoFrameSnapshotStore.Snapshot(
                resourceId = resource.id,
                resourceName = resource.name,
                fallbackMessage = getString(R.string.widget_random_photo_frame_cache_empty)
            ),
            notifyWidgets = false
        )
    }

    private fun triggerInitialRefresh(resource: ResourceEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            saveWidgetConfig(resource)
            RandomPhotoFrameWidgetRefresher.refresh(applicationContext, appWidgetId)
            withContext(Dispatchers.Main) {
                updateWidgetAndFinish()
            }
        }
    }

    private fun updateWidgetAndFinish() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        RandomPhotoFrameWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }
}