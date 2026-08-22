# Стратегическая спецификация: S1769 - Перезагрузка и выключение в меню «Пуск»

**Ticket:** S1769
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-16
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - эпик S1615 (кластер C-33)

---

## Goal

1. В меню «Пуск» добавлены пункты «Перезагрузить» (`rowReboot`) и «Выключить» (`rowShutdown`).
2. Обе команды запрашивают подтверждение в диалоге с правильной парой стилей кнопок.
3. После подтверждения выполняется команда перезагрузки или выключения.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1615 (родительский эпик, запись L-030), S1766.
- **UI placement contract:** в нижней части меню «Пуск» (`fragment_launcher_start_menu.xml`).
- **Validation level:** кнопки отображаются в «Пуске», перед вызовом открывается диалог подтверждения.
- **Owner sign-off:** делегировано конвейеру /spec-all эпика S1615 - 2026-08-16.

<!-- auto-approved by /spec-all - 2026-08-18 -->

---

# Phase 01 - Add Power Actions to Launcher Start Menu

**Strategic spec:** `PLAN/S1769_launcher-start-menu-power-actions.md`
**Status:** ✅ Done

## Objective

Add Reboot and Shutdown rows to `fragment_launcher_start_menu.xml`, implement confirmation dialogs in `LauncherStartMenuFragment.kt`, and invoke system power/reboot actions.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ic_power.xml` | New | ≤ 20 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 10 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 10 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 10 |
| `app_v2/src/launcherEnabled/res/layout/fragment_launcher_start_menu.xml` | Modified | ≤ 140 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt` | Modified | ≤ 260 |

## Steps

### Step 01.1 - Create ic_power icon and add localized strings for power actions

**Files:** `app_v2/src/main/res/drawable/ic_power.xml`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`

**Prompt for developer:**

> Create `ic_power.xml` vector drawable and add string resources for `launcher_menu_reboot`, `launcher_menu_reboot_confirm_title`, `launcher_menu_reboot_confirm_message`, `launcher_menu_shutdown`, `launcher_menu_shutdown_confirm_title`, `launcher_menu_shutdown_confirm_message` in EN/RU/UK.

**Why:**

Provides icons and text required for Reboot and Shutdown options and their confirmation dialogs.

**Verification:**

- `.\a.ps1 fr` compiles cleanly.

**Status:** `[x]` done

---

### Step 01.2 - Add Reboot and Shutdown rows to start menu layout and handle confirmation dialogs

**Files:** `app_v2/src/launcherEnabled/res/layout/fragment_launcher_start_menu.xml`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt`

**Prompt for developer:**

> Add `rowReboot` and `rowShutdown` buttons to `fragment_launcher_start_menu.xml`. In `LauncherStartMenuFragment.kt`, bind click listeners to show confirmation dialogs and execute system reboot and shutdown commands upon confirmation.

**Why:**

Gives launcher users accessible power controls from the Start menu with confirmation protection.

**Verification:**

- `.\a.ps1 fk` compiles cleanly.
- Tapping Reboot or Shutdown opens confirmation dialog.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every Step 01.* above is `[x]` done.
- [x] Project compiles cleanly.
