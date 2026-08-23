# Стратегическая спецификация: S1741 - Автоотключение экрана как настройка лаунчера

**Ticket:** S1741
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-16
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - эпик S1615 (кластер C-05)
**Tactical spec:** `PLAN/S1741_launcher-screen-timeout-setting/`

---

## 0. Approval Gate (owner input)

- **Related tickets:** Provided by user: S1615, parent epic entry L-056.
- **UI placement contract:** Provided by user: a launcher-settings selector offers Off, 5, 15, 30, 60 and 300 seconds plus manual seconds.
- **Timeout behavior:** Provided by user: after inactivity, show an app-private blackout overlay instead of changing Android's timeout or locking the device.
- **Interaction contract:** Delegated by user - /spec-all auto-approval: the first touch, mouse action, or key press dismisses the overlay and is not passed to the desktop; any interaction restarts the timer.
- **Manual entry contract:** Delegated by user - /spec-all auto-approval: accept positive whole seconds and retain the selected value.
- **Validation level:** Provided by user: verify the launcher blackout behavior without changing Android system settings.
- **Owner sign-off:** Provided by user: option 3 on 2026-08-17.

## 1. Проблема

У лаунчера нужна собственная настройка автоотключения экрана, не связанная с системной, но управлять ею в настройках лаунчера нельзя.

---

## 2. Цели

1. В настройках лаунчера есть пункт автоотключения экрана с вариантом «не отключать» и предустановками 5 / 15 / 30 / 60 / 300 секунд.
2. Пользователь может ввести своё число секунд вручную.
3. При простое активного лаунчера настройка показывает чёрный оверлей; она не меняет системный таймаут Android и не блокирует устройство.

**Non-goals:**

- Изменение системной настройки экрана Android.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Набор предустановок зафиксирован владельцем: 5, 15, 30, 60, 300 секунд плюс ручной ввод.

### 3.2 Жёсткие ограничения

- **Flavor:** по `docs/FLAVOR_MATRIX.md`.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** вариант «не отключать» держит экран включённым - влияние на батарею отражено в описании настройки.
- **Локализация:** EN/RU/UK для названия, описания и подписей вариантов.
- **Системный API:** не использовать `Settings.System.SCREEN_OFF_TIMEOUT`, `WRITE_SETTINGS` или `DevicePolicyManager`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1615 (родительский эпик, запись L-056).
- **UI placement contract:** новый пункт в настройках лаунчера со списком предустановок и ручным вводом.
- **Validation level:** проверка, что при активном лаунчере экран гаснет по выбранному таймауту, а системная настройка не затронута.
- **Owner sign-off:** делегировано конвейеру /spec-all эпика S1615 - 2026-08-16.

## 6. Исследование

1. **Status: Resolved.** Android `SCREEN_OFF_TIMEOUT` является системной настройкой и не подходит для локального поведения лаунчера. Решение владельца от 2026-08-17: чёрный оверлей внутри активного `LauncherHomeActivity`.
2. **Status: Resolved.** Существующий `BlackScreenOverlayManager` управляет полноэкранным плеерным оверлеем и системными панелями; новый менеджер лаунчера должен не менять системные панели и принимать touch, mouse и D-pad/keyboard первым событием.

---

## 11. Критерии готовности (strategic-level)

1. В настройках лаунчера выбирается таймаут из списка или вводится вручную; при простое активного стола лаунчера появляется чёрный оверлей.
2. Первое действие touch, mouse или D-pad/keyboard скрывает оверлей, не выполняя действие под ним; системный таймаут Android остаётся неизменным.

---

## Приложение. Записи инбокса (дословно)

- **L-056** - «нам нужно еще добавить настройки лаунчера на настройку автоотключение экрана потому что теперь она у нас тут есть она не связана с настройкой Android наша личная настройка там можно задать "не отключать" или в секундах . Он должен быть выбор из секунд пять, пятнадцать, тридцать, шестьдесят, триста. Ну и человек может внести цифру руками.»

---

## Last Audit

- **Date:** 2026-08-17
- **Verdict:** Verified (full contract met)
- **Checks:**
  - `AppSettings.kt`: `launcherScreenBlackoutTimeoutSeconds` declared with default 0 and `LAUNCHER_SCREEN_TIMEOUT_PRESETS = listOf(0, 5, 15, 30, 60, 300)` (PASS)
  - `SettingsRepositoryImpl.kt`: read/write mapping with non-negative coercion (PASS)
  - `BackupMapper.kt`: round-trip mapping preserved and tested (PASS)
  - `ResetLauncherToDefaultsUseCase.kt`: restores default blackout timeout (PASS)
  - `LauncherScreenBlackoutManager.kt`: lifecycle-bound blackout overlay with input interception, no system setting/DevicePolicyManager mutation (PASS)
  - `LauncherHomeActivity.kt`: lifecycle + dispatch interception (PASS)
  - `LauncherSettingsDialogFragment.kt`: selector with 6 presets + custom duration dialog (PASS)
  - Layouts & Strings: dialog layouts updated in portrait and landscape with identical binding IDs; EN/RU/UK strings verified (PASS)
  - Settings documentation: annotations + manifest regenerated; `assert-settings-doc-sync.ps1 -Gate` (PASS)
