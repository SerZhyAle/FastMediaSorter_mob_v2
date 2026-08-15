# S1587 / research 02 - What the first screen shows

**Question (§6.2):** which group opens the phone desktop once the launcher's own service actions stop occupying the first screen?

## How much fits on one screen

Device 1080x2400 @450dpi, 4 columns: one cell = 1080/4 = 270px = 96dp. Vertical budget on the observed screenshots: status bar ~72px, taskbar ~180px, system navigation ~110px, leaving ~2040px = **~7.5 rows** before the fold.

## What the current order puts there

Order today (`LauncherStarterSets.itemsFor`, `LauncherStarterSets.kt:194-215`): header, 5 launcher actions, header, clock 4x2, search 2x1, weather 2x1, resources, profile gadgets, feature shortcuts, third-party apps, tail.

Rows consumed before the first media resource:

1. row 0 - header "App functions"
2. rows 1-2 - five launcher actions (4 + 1)
3. row 3 - header "Everything else"
4. rows 4-5 - clock 4x2

The first resource lands on row 6 of ~7.5, which matches the screenshot: "All Music" and "All Images" sit at the bottom edge and the rest of the resources are below the fold (`temp/S1587/01_desktop_top.png`).

Measured on the same screenshots, the bleed makes it worse rather than better: the search gadget and "Recent Media" filled the hole in row 2, so two content items appear inside the service section while the content section itself starts below the fold.

## Verdict

Open the desktop with the content section and move the launcher's own actions to the end of the set.

Resulting row budget at 4 columns:

1. row 0 - header "Everything else"
2. rows 1-2 - clock 4x2
3. row 3 - search 2x1 + weather 2x1
4. rows 4-5 - the six resource shortcuts (4 + 2)
5. row 6 - altitude 2x1 + satellites 2x1

Everything the user installed the app for is above the fold, the clock stays the visual anchor at the top, and the service section keeps its own header lower down.

Why the actions may move without losing reachability: every one of them is also in the Start menu (`10_start_menu.png` lists Android settings, App settings, Launcher settings, Edit desktop and Exit launcher mode), so the desktop copy is a shortcut, not the only path. That is what makes the reordering safe, and it is the mitigation recorded against the §7 risk.

The clock keeps its 4x2 seed - owner ruling 2026-08-12 (strategic §3.3). It costs two of the ~7.5 rows and the budget above already accounts for that. Making the clock's content scale to whatever size the cell was given is a separate ticket, S1610.
