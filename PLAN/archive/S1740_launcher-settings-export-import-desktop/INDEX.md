# Tactical Plan: S1740 - launcher-settings-export-import-desktop

**Strategic spec:** [`../S1740_launcher-settings-export-import-desktop.md`](../S1740_launcher-settings-export-import-desktop.md)
**Research inputs:** none
**Feature:** Launcher settings and desktop shortcuts export and import
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-17

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | dto-and-mapper | - | ✅ Done | 3/3 | [PHASE_01__dto-and-mapper.md](PHASE_01__dto-and-mapper.md) |
| 02 | build-and-apply-payload | 01 | ✅ Done | 3/3 | [PHASE_02__build-and-apply-payload.md](PHASE_02__build-and-apply-payload.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None.

---

## Facts established during planning

1. Settings export/import uses `BackupPayload`, `BackupSettings`, `BuildBackupPayloadUseCase`, `ApplyBackupPayloadUseCase`, `BackupMapper`.
2. Launcher settings in `AppSettings` include `launcherDensityFactor`, `launcherTaskbarPlacement`, `launcherTaskbarShowRecents`, `launcherTaskbarShowPinned`, `launcherTaskbarShowTray`, `launcherReplaceSystemStatusArea`, `launcherTopStatusStripMode`, `launcherForeignNotificationsEnabled`, `launcherTrayShowClock`, `launcherTrayShowBluetooth`, `launcherTrayShowSim1`, `launcherTrayShowSim2`, `launcherTrayShowNetwork`, `launcherTrayShowBattery`, `launcherRotationHintShown`, `launcherDesktopLocked`, `launcherWallpaperMode`, `launcherWallpaperImagePath`, `allAppsSortOrder`, `allAppsSortDescending`.
3. Launcher desktop cells live in Room table `launcher_cells` (`LauncherCellEntity`, `LauncherCellDao`).
4. On import: if a cell target points to an uninstalled package (`app:<packageName>` or `pin:<packageName>:...`), it is skipped without throwing an error so other cells are still restored.
5. All new payload fields are optional/nullable in `BackupPayload` and have default values in `BackupSettings`, ensuring full backward and forward compatibility with existing v4/v5/v6 backups.
