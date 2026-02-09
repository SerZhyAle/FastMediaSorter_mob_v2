package com.sza.fastmediasorter.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Configuration activity for Resource Launch widget
 * Allows user to select which resource to open from widget
 */
@AndroidEntryPoint
class ResourceLaunchWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface ResourceWidgetEntryPoint {
        fun database(): AppDatabase
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED initially
        setResult(RESULT_CANCELED)

        // Get widget ID
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                val entryPoint = EntryPointAccessors.fromApplication(
                    applicationContext,
                    ResourceWidgetEntryPoint::class.java
                )
                ResourceSelectionScreen(
                    database = entryPoint.database(),
                    onResourceSelected = { resource ->
                        saveWidgetConfig(resource)
                        updateWidgetAndFinish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun saveWidgetConfig(resource: ResourceEntity) {
        val prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        prefs.edit()
            .putLong("resource_id_$appWidgetId", resource.id)
            .putString("resource_name_$appWidgetId", resource.name)
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
    onResourceSelected: (ResourceEntity) -> Unit,
    onCancel: () -> Unit
) {
    var resources by remember { mutableStateOf<List<ResourceEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            resources = database.resourceDao().getAllResources().first()
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_select_resource)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
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
                        items(resources) { resource ->
                            ResourceItem(
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
    resource: ResourceEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
