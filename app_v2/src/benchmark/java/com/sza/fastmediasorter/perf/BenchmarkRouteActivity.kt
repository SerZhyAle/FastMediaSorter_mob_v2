package com.sza.fastmediasorter.perf

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.player.PlayerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BenchmarkRouteActivity : AppCompatActivity() {

    @Inject
    lateinit var resourceRepository: ResourceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }

        lifecycleScope.launch {
            val resource = resolveResource()
            if (resource == null) {
                Timber.w("BenchmarkRouteActivity: no resource available for benchmark route")
                finish()
                return@launch
            }

            when (intent.getStringExtra(BenchmarkContract.EXTRA_JOURNEY)) {
                BenchmarkContract.JOURNEY_BROWSE -> openBrowse(resource)
                BenchmarkContract.JOURNEY_PLAYER -> openPlayer(resource)
                else -> {
                    val journey = intent.getStringExtra(BenchmarkContract.EXTRA_JOURNEY)
                    Timber.w(
                        "BenchmarkRouteActivity: unknown journey '%s'",
                        journey
                    )
                    finish()
                }
            }
        }
    }

    private suspend fun resolveResource(): MediaResource? = withContext(Dispatchers.IO) {
        val explicitResourceId = intent.getLongExtra(BenchmarkContract.EXTRA_RESOURCE_ID, 0L)
            .takeIf { it > 0L }
        if (explicitResourceId != null) {
            return@withContext resourceRepository.getResourceById(explicitResourceId)
        }

        val resources = resourceRepository.getAllResourcesSync()
        val explicitPath = intent.getStringExtra(BenchmarkContract.EXTRA_RESOURCE_PATH)

        explicitPath?.let { path ->
            resources.firstOrNull { it.path == path }
        } ?: resources.firstOrNull { it.path == LocalMediaScanner.VIRTUAL_PATH_RECENT }
            ?: resources.firstOrNull()
    }

    private fun openBrowse(resource: MediaResource) {
        startActivity(
            BrowseActivity.createIntent(
                context = this,
                resourceId = resource.id,
                skipAvailabilityCheck = true,
                initialFilePath = intent.getStringExtra(BenchmarkContract.EXTRA_FILE_PATH),
            )
        )
        finish()
    }

    private fun openPlayer(resource: MediaResource) {
        startActivity(
            PlayerActivity.createIntent(
                context = this,
                resourceId = resource.id,
                initialIndex = 0,
                skipAvailabilityCheck = true,
                initialFilePath = intent.getStringExtra(BenchmarkContract.EXTRA_FILE_PATH),
            )
        )
        finish()
    }
}
