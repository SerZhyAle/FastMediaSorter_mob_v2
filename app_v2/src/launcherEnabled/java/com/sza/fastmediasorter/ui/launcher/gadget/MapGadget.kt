package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherMapBinding
import com.sza.fastmediasorter.domain.map.MapRefreshPolicy
import com.sza.fastmediasorter.domain.model.map.MapSnapshot
import com.sza.fastmediasorter.domain.model.sensors.SensorCapability
import com.sza.fastmediasorter.domain.repository.MapResult
import com.sza.fastmediasorter.domain.repository.SensorAvailabilityRepository
import com.sza.fastmediasorter.domain.usecase.map.GetLauncherMapUseCase
import com.sza.fastmediasorter.util.resolveActivityCompat
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/** S1175: where the user is now, on keyless OpenStreetMap tiles. */
class MapGadget @Inject constructor(
    private val availability: SensorAvailabilityRepository,
    private val getMap: Lazy<GetLauncherMapUseCase>,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_MAP
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 2
    override val minSpanW: Int = 1
    override val minSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_map
    override val iconRes: Int = R.drawable.ic_map
    override val requiresResourceParam: Boolean = false

    /**
     * Hardware only, never the permission: a refused grant must leave the tile on the desktop in its
     * honest no-permission state, while a device with no location hardware at all has nothing to show.
     */
    override fun isAvailable(): Boolean = availability.isAvailable(SensorCapability.LOCATION)

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        MapGadgetView(container.context, getMap.get())
}

private class MapGadgetView(
    context: Context,
    private val getMap: GetLauncherMapUseCase,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherMapBinding.inflate(LayoutInflater.from(context), this)

    private var lastPoint: String? = null

    init {
        contentDescription = context.getString(R.string.launcher_gadget_map_actions)
        setOnClickListener { openMapApp(context) }
    }

    override suspend fun CoroutineScope.onActive() {
        while (isActive) {
            val result = getMap()
            Timber.d("S1175: map gadget state %s", result::class.simpleName)
            when (result) {
                is MapResult.Fresh -> showSnapshot(result.snapshot, stale = false)
                is MapResult.Stale -> showSnapshot(result.snapshot, stale = true)
                is MapResult.PermissionMissing -> {
                    // The last picture is still where the user was, so a revoked grant explains
                    // itself over the map rather than replacing it with an error tile (§11.4).
                    result.snapshot?.let { showSnapshot(it, stale = true) }
                    showMessage(R.string.launcher_gadget_map_no_permission)
                }

                MapResult.Unavailable -> showMessage(R.string.launcher_gadget_map_unavailable)
            }
            delay(REFRESH_INTERVAL_MS)
        }
    }

    private fun showSnapshot(snapshot: MapSnapshot, stale: Boolean) {
        val file = File(snapshot.imagePath)
        // Glide, not a hand-rolled decode: it moves the read off the main thread and drops the request
        // when this view detaches, which a gadget rebuilt on every desktop rebind needs. The signature
        // is load-bearing: Glide keys a File by path alone, so a re-downloaded tile at the same path
        // would serve the previously decoded bitmap and a repaired tile would never redraw.
        Glide.with(this)
            .load(file)
            .signature(ObjectKey(file.lastModified()))
            .into(binding.gadgetMapImage)
        binding.gadgetMapMarker.isVisible = true
        binding.gadgetMapLabel.text = snapshot.label
        binding.gadgetMapAttribution.text = snapshot.attribution
        binding.gadgetMapMessage.isVisible = stale
        if (stale) binding.gadgetMapMessage.setText(R.string.launcher_gadget_map_stale)
        lastPoint = "${snapshot.point.latitude},${snapshot.point.longitude}"
        // TalkBack reads the cell, not the picture: without this the tile announces only its purpose.
        contentDescription = context.getString(R.string.launcher_gadget_map_actions_at, snapshot.label)
    }

    /** Keeps whatever tile is already on screen - an outage must not blank a readable cell. */
    private fun showMessage(messageRes: Int) {
        binding.gadgetMapMessage.setText(messageRes)
        binding.gadgetMapMessage.isVisible = true
    }

    /**
     * Opens whichever map application the device has through a plain `geo:` intent. A device with no
     * map application at all is a silent no-op rather than a crash on the home screen (same contract
     * as the weather gadget).
     */
    private fun openMapApp(context: Context) {
        val point = lastPoint
        val uri = if (point == null) GEO_ROOT else "geo:$point?q=$point"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivityCompat(intent) == null) {
            Timber.i("Launcher map gadget: no map application to open")
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Launcher map gadget: map application refused to open") }
    }

    private companion object {
        /** Read from the domain rather than repeated here, so the cell and the cache cannot drift. */
        val REFRESH_INTERVAL_MS = MapRefreshPolicy.POLL_INTERVAL_MS

        /** Opens the map application on whatever it considers home when no fix has arrived yet. */
        const val GEO_ROOT = "geo:0,0"
    }
}
