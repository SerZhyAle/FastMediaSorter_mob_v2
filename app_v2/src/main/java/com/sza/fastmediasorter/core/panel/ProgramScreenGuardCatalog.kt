package com.sza.fastmediasorter.core.panel

import android.app.Activity
import com.sza.fastmediasorter.ui.broadcast.BroadcastEntryActivity
import com.sza.fastmediasorter.ui.broadcast.BroadcastShareActivity
import com.sza.fastmediasorter.ui.cameraocr.CameraOcrTranslateActivity
import com.sza.fastmediasorter.ui.filler.FillerActivity
import com.sza.fastmediasorter.ui.flashlight.FrontFlashlightActivity
import com.sza.fastmediasorter.ui.flashlight.WaterFlashlightActivity
import com.sza.fastmediasorter.ui.game.GameActivity
import com.sza.fastmediasorter.ui.game.GameHelpActivity
import com.sza.fastmediasorter.ui.mirror.MirrorActivity
import com.sza.fastmediasorter.ui.sos.SosActivity
import com.sza.fastmediasorter.ui.systeminfo.SystemInfoActivity
import com.sza.fastmediasorter.ui.tourist.TouristInfoActivity
import com.sza.fastmediasorter.ui.tourist.counter.TouristCounterActivity
import com.sza.fastmediasorter.ui.wear.WatchListenLaunchActivity
import com.sza.fastmediasorter.ui.wear.WearCompanionActivity
import com.sza.fastmediasorter.widget.CameraQuickCaptureActivity
import com.sza.fastmediasorter.widget.LinkDownloadLaunchActivity
import com.sza.fastmediasorter.widget.QuickAudioRecorderActivity
import com.sza.fastmediasorter.widget.ScreenRecordingLaunchActivity

/**
 * S4544: which destination guards which program's off-switch.
 *
 * A pinned OS shortcut, an old launcher cell and a recents instance all reach a program's screen without
 * asking [com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase], so only the
 * destination can refuse to open while the program is switched off. Every [SubProgramCatalog] entry with
 * an off-switch is listed here once - as a guarded screen, a guarded launcher or an exemption with its
 * reason - and `ProgramScreenGuardCatalogCompletenessTest` fails on an entry that is none of the three.
 *
 * Screens are observed from the application's activity callbacks for as long as they live. Launchers act
 * inside `onCreate`, before any callback could stop them, so each one admits its starting branch itself;
 * the stop branches of the toggling launchers stay ungated, because switching a program off must never
 * strand a recording or a listen session that is already running. For the same reason the broadcast
 * control screen is absent: a live broadcast is stopped there.
 */
object ProgramScreenGuardCatalog {

    val screens: Map<Class<out Activity>, String> = mapOf(
        FrontFlashlightActivity::class.java to InternalRouteCatalog.KEY_FRONT_FLASHLIGHT,
        WaterFlashlightActivity::class.java to InternalRouteCatalog.KEY_WATER_FLASHLIGHT,
        SosActivity::class.java to InternalRouteCatalog.KEY_SOS,
        MirrorActivity::class.java to InternalRouteCatalog.KEY_MIRROR,
        TouristInfoActivity::class.java to InternalRouteCatalog.KEY_TOURIST_INFO,
        TouristCounterActivity::class.java to InternalRouteCatalog.KEY_TOURIST_INFO,
        GameActivity::class.java to InternalRouteCatalog.KEY_GAME,
        GameHelpActivity::class.java to InternalRouteCatalog.KEY_GAME,
        // The filler's availability already admits it while the journal lists leftovers of an interrupted
        // run (S4542), so the guard leaves that cleanup reachable without an exemption of its own.
        FillerActivity::class.java to InternalRouteCatalog.KEY_FILLER,
        // The take-photo OCR route opens this same screen, so the OCR switch governs both entries.
        CameraOcrTranslateActivity::class.java to InternalRouteCatalog.KEY_OCR,
        SystemInfoActivity::class.java to InternalRouteCatalog.KEY_SYSTEM_INFO,
        WearCompanionActivity::class.java to InternalRouteCatalog.KEY_WEAR_COMPANION,
    )

    val launchers: Map<Class<out Activity>, String> = mapOf(
        QuickAudioRecorderActivity::class.java to InternalRouteCatalog.KEY_QUICK_VOICE,
        ScreenRecordingLaunchActivity::class.java to InternalRouteCatalog.KEY_SCREEN_RECORDING,
        LinkDownloadLaunchActivity::class.java to InternalRouteCatalog.KEY_LINK_DOWNLOAD,
        CameraQuickCaptureActivity::class.java to InternalRouteCatalog.KEY_QUICK_CAMERA,
        // One launcher serves both listen routes; they share the companion's switch (S2881).
        WatchListenLaunchActivity::class.java to InternalRouteCatalog.KEY_WATCH_LISTEN,
        BroadcastEntryActivity::class.java to InternalRouteCatalog.KEY_BROADCAST,
        BroadcastShareActivity::class.java to InternalRouteCatalog.KEY_BROADCAST,
    )

    /** Route keys with an off-switch that deliberately carry no guard from this catalog. */
    val exemptions: Map<String, String> = mapOf(
        InternalRouteCatalog.KEY_SCHEDULED_TASKS to
            "the screen hosts the program's own off-switch (S3365), so it must open while the switch is off",
        InternalRouteCatalog.KEY_CALCULATOR to "guards its own screen with a fallback block (S1856)",
        InternalRouteCatalog.KEY_STOPWATCH to "guards its own screen with a fallback block (S2613)",
        InternalRouteCatalog.KEY_NETWORK_MONITOR to "guards its own screen through its view model (S1433)",
        InternalRouteCatalog.KEY_WATCH_LISTEN_RECORD to
            "served by the same launcher as the plain listen route, which is guarded under that key",
    )

    fun screenRouteKeyFor(activityClass: Class<out Activity>): String? = screens[activityClass]
}
