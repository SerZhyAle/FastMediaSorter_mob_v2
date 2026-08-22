# Стратегическая спецификация: S1766 - Полное замещение системной строки состояния с сохранением жеста

**Ticket:** S1766
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-16
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - эпик S1615 (кластер C-30)

---

## Goal

1. При включённом замещении («замещать системную область статуса») своя строка состояния рисуется на самом верху (y = 0), на месте системной; чёрного поля сверху нет.
2. Жест смахивания сверху продолжает открывать системную шторку уведомлений и область управления Android (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`).
3. При отключённом замещении возвращается системная строка состояния с соответствующим отступом сверху.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1615 (родительский эпик, запись L-004), S1087, S1099, S1737.
- **UI placement contract:** своя строка состояния (`launcherStatusStrip`) на y = 0 при включённом замещении.
- **Validation level:** на устройстве: своя строка на самом верху без черного поля, шторка открывается свайпом.
- **Owner sign-off:** делегировано конвейеру /spec-all эпика S1615 - 2026-08-16.

<!-- auto-approved by /spec-all - 2026-08-18 -->

---

# Phase 01 - Full System Status Bar Replacement with Gesture

**Strategic spec:** `PLAN/S1766_launcher-full-status-bar-replacement-gesture.md`
**Status:** ✅ Done

## Objective

Ensure `LauncherHomeActivity` removes top inset padding on `launcherRoot` when replacing system status area so custom status strip occupies y = 0, while keeping transient swipe gesture for Android notification shade.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 650 |

## Steps

### Step 01.1 - Configure edge-to-edge and dynamic top inset padding in LauncherHomeActivity

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`

**Prompt for developer:**

> In `LauncherHomeActivity.kt`, set `WindowCompat.setDecorFitsSystemWindows(window, false)` in `setupViews()` and update `applyStatusBarPolicy(replaceSystemStatusArea)` to apply top inset padding (`applyTop = true`) only when `replaceSystemStatusArea` is false, and remove top inset padding (`applyTop = false`) when `replaceSystemStatusArea` is true.

**Why:**

When status bar replacement is active, custom status strip sits at y = 0 directly over camera cutout space with no top black gap, while `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` retains top swipe to show notification shade.

**Verification:**

- `.\a.ps1 fk` compiles cleanly.
- `applyStatusBarPolicy(true)` sets `applyTop = false`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every Step 01.* above is `[x]` done.
- [x] Project compiles cleanly.
