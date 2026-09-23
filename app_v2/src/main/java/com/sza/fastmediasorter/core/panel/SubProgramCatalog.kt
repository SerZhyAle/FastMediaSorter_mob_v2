package com.sza.fastmediasorter.core.panel

/**
 * The single list of FMS sub-programs (strategic S1736).
 *
 * Every surface that shows sub-programs takes its composition and its order from here: the
 * programs menu and the programs panel, the app-launch panel, the widget picker and the launcher
 * desktop. None of them keeps a list of its own.
 *
 * Adding a sub-program is one entry here. A surface that then fails to resolve it fails
 * `SubProgramCatalogCompletenessTest` rather than going unnoticed, which is the whole point of the
 * table existing (ADR-4).
 *
 * Order is the programs menu's own order (ADR-5) as the owner sees it rendered - the `order`
 * argument each `popup.menu.add` passes, not the sequence the calls are written in. The two differ
 * for screen recording, which is added third and shown sixth.
 *
 * VR Cinema is the one programs-menu item deliberately left out. Alone among them it has no
 * `InternalRouteCatalog` key, no toggle of its own and no second-window branch, so adding it would
 * mean inventing a route rather than transcribing one. That is an owner decision, open as strategic
 * §6.5; until it is answered the menu has exactly one item this table does not govern.
 */
object SubProgramCatalog {

    private val entries: List<SubProgramEntry> = listOf(
        // No widget pairing, for the same reason `stream_launch` has none (ADR-6): the
        // `camera_quick_capture` widget captures to whatever target that instance was configured with,
        // while this route pins EXTRA_APPWIDGET_ID to the panel sentinel so it always captures to the
        // camera folder. Same Activity, different destination, so they are not one program.
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_QUICK_CAMERA,
            order = 0,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
            ),
            disable = { it.copy(disableCameraCapture = true, disableVideoCapture = true) },
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_QUICK_VOICE,
            order = 10,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
                SubProgramSurface.WIDGET,
            ),
            widgetKey = "quick_audio_recorder",
            disable = { it.copy(micRecordingEnabled = false) },
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_CALCULATOR,
            order = 30,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
                SubProgramSurface.WIDGET,
            ),
            widgetKey = "calculator",
            disable = { it.copy(enableCalculator = false) },
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_NETWORK_MONITOR,
            order = 40,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
                SubProgramSurface.WIDGET,
            ),
            widgetKey = "network_monitor",
            disable = { it.copy(enableNetworkMonitor = false) },
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_OCR,
            order = 50,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
                SubProgramSurface.WIDGET,
            ),
            widgetKey = "camera_ocr_translate",
            disable = { it.copy(cameraOcrTranslationEnabled = false) },
        ),
        // Order 55, not 25: the menu adds this item third but renders it sixth, because PopupMenu
        // sorts by the order argument and MENU_ORDER_SCREEN_RECORDING is 8 while camera-OCR is 7.
        // ADR-5 fixes the canonical order as the one the owner already sees, so the rendered position
        // wins over the call sequence.
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_SCREEN_RECORDING,
            order = 55,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
            ),
            disable = { it.copy(screenRecordingEnabled = false) },
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_LINK_DOWNLOAD,
            order = 60,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
            ),
            disable = { it.copy(linkAutoDownloadEnabled = false) },
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_BROADCAST,
            order = 65,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
                SubProgramSurface.WIDGET,
            ),
            widgetKey = "broadcast_entry",
            disable = { it.copy(enableBroadcasting = false) },
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_GAME,
            order = 70,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
                SubProgramSurface.WIDGET,
            ),
            widgetKey = "game_launch",
            disable = { it.copy(embeddedGameEnabled = false) },
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_SYSTEM_INFO,
            order = 80,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
            ),
            disable = { it.copy(enableSystemInfo = false) },
        ),
        // S2922: Tourist dashboard sub-program.
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_TOURIST_INFO,
            order = 85,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
                SubProgramSurface.WIDGET,
            ),
            disable = { it.copy(enableTourist = false) },
            widgetKey = "tourist_info",
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_WEAR_COMPANION,
            order = 90,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
            ),
            disable = { it.copy(enableWearCompanion = false) },
        ),
        // S2881: the two listen calls stand beside the companion they extend, both keyed to the same
        // switch - removing the companion removes its listening with it. Two entries, not one (ADR-6):
        // a registry entry resolves to exactly one intent. The widget pairs with the plain listen
        // entry only; the record variant is the widget's second button, not a second widget.
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_WATCH_LISTEN,
            order = 91,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
                SubProgramSurface.WIDGET,
            ),
            widgetKey = "watch_listen",
            disable = { it.copy(enableWearCompanion = false) },
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_WATCH_LISTEN_RECORD,
            order = 92,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
            ),
            disable = { it.copy(enableWearCompanion = false) },
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_FRONT_FLASHLIGHT,
            order = 100,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
                SubProgramSurface.WIDGET,
            ),
            widgetKey = "front_flashlight",
            disable = { it.copy(frontFlashlightEnabled = false) },
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_PHYSICAL_FLASHLIGHT,
            order = 101,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
            ),
        ),
        // S2516: order 102 so the three lights stand together in the menu; the mirror moved to 103 for
        // it, which changes no position relative to anything else because 103 and 104 were both free.
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_WATER_FLASHLIGHT,
            order = 102,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
                SubProgramSurface.WIDGET,
            ),
            widgetKey = "water_flashlight",
            disable = { it.copy(waterFlashlightEnabled = false) },
        ),
        // S1924: no widget pairing - none is planned, and an entry declaring WIDGET must name a
        // paired HomeWidgetCatalog key or the completeness test fails.
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_MIRROR,
            order = 103,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
            ),
            disable = { it.copy(mirrorEnabled = false) },
        ),
        // S3216: order 104, the one free slot left between the mirror and the black screen - the
        // distress signal is the fourth member of the light family, and ADR-5 fixes this table's order
        // as the position the owner already sees, so slotting it here moves nothing.
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_SOS,
            order = 104,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
            ),
            disable = { it.copy(enableSos = false) },
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_BLACK_SCREEN,
            order = 105,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
            ),
        ),
        // S1411. Appended rather than slotted next to the calculator: this table's order must match the
        // position the owner actually sees (the ADR-5 note on screen recording above), and the programs
        // menu appends a new program precisely so no familiar position shifts (S1733, S1735). Slotting
        // the stopwatch beside the calculator would have moved nine existing entries down one row.
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_STOPWATCH,
            order = 106,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
                SubProgramSurface.WIDGET,
            ),
            widgetKey = "stopwatch",
            disable = { it.copy(enableStopwatch = false) },
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_TAKE_PHOTO_SEND_TO,
            order = 110,
            surfaces = setOf(
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
            ),
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_TAKE_PHOTO_EDIT,
            order = 120,
            surfaces = setOf(
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
            ),
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_TAKE_PHOTO_OCR_TRANSLATE,
            order = 130,
            surfaces = setOf(
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
            ),
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_START_VIDEO_RECORDING,
            order = 140,
            surfaces = setOf(
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
            ),
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_CAMERA_PHOTOS,
            order = 150,
            surfaces = setOf(
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
                SubProgramSurface.WIDGET,
            ),
            widgetKey = "camera_photos",
        ),
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_CAMERA_LAUNCH,
            order = 160,
            surfaces = setOf(
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
                SubProgramSurface.WIDGET,
            ),
            widgetKey = "camera_launch",
        ),
        // S3365: the scheduled-operations program joins the registry - one entry puts it on every
        // sub-program surface. The off-switch is the master toggle the settings card used to own;
        // availability, widget gate and enable-all already read it, so no settings migration.
        SubProgramEntry(
            routeKey = InternalRouteCatalog.KEY_SCHEDULED_TASKS,
            order = 165,
            surfaces = setOf(
                SubProgramSurface.PROGRAMS_MENU,
                SubProgramSurface.QUICK_ACCESS_PANEL,
                SubProgramSurface.LAUNCHER_SHORTCUT,
                SubProgramSurface.OS_APP_SHORTCUT,
                SubProgramSurface.WIDGET,
            ),
            widgetKey = "scheduled_tasks",
            disable = { it.copy(enableScheduledOperations = false) },
        ),
    )

    /** Every sub-program, in the one order shown on every surface (ADR-5). */
    fun all(): List<SubProgramEntry> = entries.sortedBy { it.order }

    /** The entry owning [routeKey], or null when that route is not a sub-program. */
    fun byRouteKey(routeKey: String): SubProgramEntry? =
        entries.firstOrNull { it.routeKey == routeKey }

    /** The entries declaring themselves fit for [surface], in the same canonical order. */
    fun forSurface(surface: SubProgramSurface): List<SubProgramEntry> =
        all().filter { surface in it.surfaces }
}
