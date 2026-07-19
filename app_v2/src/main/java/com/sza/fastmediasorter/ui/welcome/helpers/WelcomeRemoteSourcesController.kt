package com.sza.fastmediasorter.ui.welcome.helpers

import android.content.ActivityNotFoundException
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.databinding.PageWelcomeNetworksBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the welcome networks page (S0391): three remote-source group toggles (SMB / (S)FTP / Cloud)
 * over the six per-source [AppSettings] flags. A group reads ON when any of its members is enabled;
 * tapping it mass-writes every member. A group row is hidden on flavors lacking that group: the
 * cloud row without cloud support, the SMB/(S)FTP rows without local-network support (S0448).
 *
 * Hilt-injected into WelcomeActivity and wired into the page via [bind]; mirrors
 * [WelcomeFunctionalityController] so the page stays a thin renderer (Clean+MVVM).
 */
class WelcomeRemoteSourcesController @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val gate: RemoteSourceAvailabilityGate,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {

    fun bind(binding: PageWelcomeNetworksBinding, owner: LifecycleOwner) {
        // One-shot read of the current settings drives the initial checked state. The welcome page is
        // the only writer during onboarding, so a live observer is not needed here.
        owner.lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            bindRows(binding, settings)
        }
    }

    private fun bindRows(binding: PageWelcomeNetworksBinding, settings: AppSettings) {
        bindSmbRow(binding.rowSourceSmb, settings)
        bindFtpRow(binding.rowSourceFtp, settings)
        bindCloudRow(binding.rowSourceCloud, settings)
        bindCompanionPromo(binding)
    }

    // The Windows companion publishes PC folders over SMB/SFTP, so the promo is only meaningful where
    // the local-network group is available - hidden on cloud-only flavors alongside the SMB/(S)FTP rows.
    private fun bindCompanionPromo(binding: PageWelcomeNetworksBinding) {
        val visible = gate.isNetworkGroupSupported()
        binding.tvCompanionNote.visibility = if (visible) View.VISIBLE else View.GONE
        binding.btnCompanionSite.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return
        binding.btnCompanionSite.setOnClickListener { view ->
            try {
                view.context.startActivity(SupportIntentFactory.openUrl(SupportIntentFactory.companionHomeUrl()))
            } catch (e: ActivityNotFoundException) {
                // No browser installed - nothing to open; the promo is optional so just log and move on.
                Timber.i(e, "WelcomeRemoteSourcesController: no browser for companion site")
            }
        }
    }

    private fun bindSmbRow(row: SettingsToggleRow, settings: AppSettings) {
        if (!gate.isNetworkGroupSupported()) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        row.setCheckedSilently(settings.smbEnabled)
        row.setOnCheckedChangeListener { isChecked ->
            persist { it.copy(smbEnabled = isChecked) }
        }
    }

    private fun bindFtpRow(row: SettingsToggleRow, settings: AppSettings) {
        if (!gate.isNetworkGroupSupported()) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        row.setCheckedSilently(settings.sftpEnabled || settings.ftpEnabled)
        row.setOnCheckedChangeListener { isChecked ->
            persist { it.copy(sftpEnabled = isChecked, ftpEnabled = isChecked) }
        }
    }

    private fun bindCloudRow(row: SettingsToggleRow, settings: AppSettings) {
        if (!gate.isCloudGroupSupported()) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        row.setCheckedSilently(settings.googleDriveEnabled || settings.oneDriveEnabled || settings.dropboxEnabled)
        row.setOnCheckedChangeListener { isChecked ->
            persist { it.copy(googleDriveEnabled = isChecked, oneDriveEnabled = isChecked, dropboxEnabled = isChecked) }
        }
    }

    // Read-modify-write of the latest snapshot so concurrent toggle taps do not clobber each other's
    // fields. Runs on the application scope so the write survives the page being torn down right after
    // a tap - the settings change must not be lost on navigation.
    // S0876: the transform overload is mutex-serialized against every other transform() writer
    // (see SettingsRepository KDoc) - a plain getSettings().first()+updateSettings() pair here could
    // race the Welcome enable-all flow's concurrent deliverable-install writers.
    private fun persist(transform: suspend (AppSettings) -> AppSettings) {
        appScope.launch {
            settingsRepository.updateSettings(transform)
        }
    }
}
