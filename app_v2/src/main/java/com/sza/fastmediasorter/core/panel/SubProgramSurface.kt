package com.sza.fastmediasorter.core.panel

/**
 * The surfaces a sub-program can be offered on (strategic S1736 §5.3).
 *
 * The set is closed at four deliberately. Launcher informer tiles are excluded permanently by
 * ADR-3 - they are not programs - and the OS shortcut surface is moved by S1925, which adds its
 * own constant then. A new surface is a new constant here plus a case in the completeness test,
 * never a new table.
 */
enum class SubProgramSurface {

    /** The main-window dropdown menu, and the programs panel that replays its population. */
    PROGRAMS_MENU,

    /** The app-launch panel overlay. */
    QUICK_ACCESS_PANEL,

    /** The in-app picker that pins one of our home-screen widgets. */
    WIDGET,

    /** A launcher desktop cell added automatically when the program is switched on. */
    LAUNCHER_SHORTCUT,
}
