package com.sza.fastmediasorter.ui.main.helpers

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.tabs.TabLayout
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import com.sza.fastmediasorter.databinding.ActivityMainBinding
import com.sza.fastmediasorter.databinding.ViewMainProgramsPanelBinding
import com.sza.fastmediasorter.databinding.ViewMainStreamsPanelBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.StreamResumeStateRepository
import com.sza.fastmediasorter.domain.usecase.ClearResumeStateUseCase
import com.sza.fastmediasorter.domain.usecase.GetResumeStateUseCase
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.main.MainViewModel
import com.sza.fastmediasorter.ui.main.ResourceTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * S1329: owns the five domain dependencies the home screen used to field-inject and hands them to each
 * helper itself, so `MainActivity` declares no repository or use case of its own (CLAUDE.md Rule 3).
 *
 * Every method adapts to its helper exactly as that helper is written today - **no helper constructor
 * signature may change** (strategic ADR-1) - so the host-supplied surface is mirrored one-to-one: views,
 * scopes and callbacks are what Hilt cannot supply.
 *
 * `resourceRepository` is held eagerly rather than behind `dagger.Lazy`, because
 * [MainResumePlaybackHelper] takes the resolved type and this factory may not widen its signature.
 *
 * Unscoped on purpose - what it builds is Activity-scoped and must not outlive the host that asked.
 */
class MainHelperFactory @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val getResumeStateUseCase: GetResumeStateUseCase,
    private val clearResumeStateUseCase: ClearResumeStateUseCase,
    // S1152: last-active-stream store, read by the resume helper to resume a stream on cold start.
    private val streamResumeStateRepository: StreamResumeStateRepository,
) {

    fun createResumePlaybackHelper(
        activity: AppCompatActivity,
        binding: ActivityMainBinding,
    ): MainResumePlaybackHelper = MainResumePlaybackHelper(
        activity = activity,
        binding = binding,
        settingsRepository = settingsRepository,
        resourceRepository = resourceRepository,
        getResumeStateUseCase = getResumeStateUseCase,
        clearResumeStateUseCase = clearResumeStateUseCase,
        streamResumeStateRepository = streamResumeStateRepository,
    )

    fun createPanelItemActionsManager(
        activity: AppCompatActivity,
        unpinStreamSource: (id: String) -> Unit,
        currentSettings: () -> AppSettings?,
        resourceVrCinema: ResourceVrCinemaLaunchManager,
    ): MainPanelItemActionsManager = MainPanelItemActionsManager(
        activity = activity,
        settingsRepository = settingsRepository,
        unpinStreamSource = unpinStreamSource,
        currentSettings = currentSettings,
        resourceVrCinema = resourceVrCinema,
    )

    @Suppress("LongParameterList") // Mirrors the manager's host-supplied surface one-to-one.
    fun createProgramsPanelManager(
        panel: ViewMainProgramsPanelBinding,
        populateMenu: (PopupMenu, Boolean) -> Unit,
        onItemSelected: (Int) -> Unit,
        newWindowActionFor: (Int) -> (() -> Unit)?,
        removeActionFor: (Int) -> (() -> Unit)?,
        onConfigure: () -> Unit,
        onHidePanel: () -> Unit,
        scope: CoroutineScope,
        collapsedChip: View,
        onChipVisibilityChanged: () -> Unit,
    ): MainProgramsPanelManager = MainProgramsPanelManager(
        panel = panel,
        populateMenu = populateMenu,
        onItemSelected = onItemSelected,
        newWindowActionFor = newWindowActionFor,
        removeActionFor = removeActionFor,
        onConfigure = onConfigure,
        onHidePanel = onHidePanel,
        settingsRepository = settingsRepository,
        scope = scope,
        collapsedChip = collapsedChip,
        onChipVisibilityChanged = onChipVisibilityChanged,
    )

    @Suppress("LongParameterList") // Mirrors the manager's host-supplied surface one-to-one.
    fun createStreamsPanelManager(
        panel: ViewMainStreamsPanelBinding,
        lifecycleOwner: LifecycleOwner,
        scope: CoroutineScope,
        observePinnedStreamSources: () -> Flow<List<StreamSourceEntity>>,
        faviconAtlasStore: FaviconAtlasStore,
        onOpenStreams: () -> Unit,
        inlineAudio: MainStreamsInlineAudioManager,
        menuActions: StreamsPanelMenuActions,
        collapsedChip: View,
        onChipVisibilityChanged: () -> Unit,
    ): MainStreamsPanelManager = MainStreamsPanelManager(
        panel = panel,
        lifecycleOwner = lifecycleOwner,
        scope = scope,
        observePinnedStreamSources = observePinnedStreamSources,
        faviconAtlasStore = faviconAtlasStore,
        onOpenStreams = onOpenStreams,
        inlineAudio = inlineAudio,
        menuActions = menuActions,
        settingsRepository = settingsRepository,
        collapsedChip = collapsedChip,
        onChipVisibilityChanged = onChipVisibilityChanged,
    )

    /** Internal because [MainEventHandler] is - the host keeps the same visibility it had. */
    internal fun createEventHandler(
        activity: MainActivity,
        binding: ActivityMainBinding,
        viewModel: MainViewModel,
        passwordManager: ResourcePasswordManager,
        onOpenSettings: () -> Unit,
        onRecordLastPlayed: (Long) -> Unit,
    ): MainEventHandler = MainEventHandler(
        activity = activity,
        binding = binding,
        viewModel = viewModel,
        settingsRepository = settingsRepository,
        passwordManager = passwordManager,
        onOpenSettings = onOpenSettings,
        onRecordLastPlayed = onRecordLastPlayed,
    )

    @Suppress("LongParameterList") // Mirrors the manager's host-supplied surface one-to-one.
    fun createResourceTabsManager(
        tabLayout: TabLayout,
        collapsedStrip: View,
        gate: RemoteSourceAvailabilityGate,
        scope: CoroutineScope,
        onTabSelected: (ResourceTab) -> Unit,
        onFavoritesReselected: () -> Unit,
        getActiveTab: () -> ResourceTab,
        getPreviousTab: () -> ResourceTab?,
        onChipVisibilityChanged: () -> Unit,
    ): MainResourceTabsManager = MainResourceTabsManager(
        tabLayout = tabLayout,
        collapsedStrip = collapsedStrip,
        gate = gate,
        settingsRepository = settingsRepository,
        scope = scope,
        onTabSelected = onTabSelected,
        onFavoritesReselected = onFavoritesReselected,
        getActiveTab = getActiveTab,
        getPreviousTab = getPreviousTab,
        onChipVisibilityChanged = onChipVisibilityChanged,
    )
}
