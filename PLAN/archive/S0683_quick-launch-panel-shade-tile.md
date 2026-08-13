# Спецификация: S0683 - Кнопка в шторке (Quick Settings tile) для Панели быстрого запуска

**Ticket:** S0683
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-25
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-25

<!-- auto-approved by /spec-all - 2026-06-25 -->

> **Scope:** COMPACT (Simple path). Цель + фазы. Фича уже существует (AppLaunchPanel), добавляется новая точка входа.

---

## Цель

Зарегистрировать Quick Settings tile (кнопку в системной шторке) как второй способ открыть уже реализованную Панель быстрого запуска (`AppLaunchPanelActivity`), помимо жеста с левого края. Тайл особенно полезен в конфигурациях, где edge-gesture overlay выключен (standard по умолчанию). Пользователь сам размещает тайл в шторке через системный редактор.

Панель запускается напрямую: `Intent(context, AppLaunchPanelActivity::class.java)` + `FLAG_ACTIVITY_NEW_TASK` (как в `ScreenshotGestureActionDispatcher.launchPanel`). Образец тайла - существующий `AudioToggleTileService`.

**Non-goals:**

- Без изменений самой панели, её содержимого, редактора.
- Без зависимости от S0672 (edge-gesture compliance) - тайл открывает панель напрямую.

---

## 0. Захваченный материал (inbox)

**Текст:** "Зарегистрировать кнопку в шторке для реализованной Панели 'быстрого запуска' (второй способ запуска помимо жеста с левого края). Пользователь сам та может её разместить повыше."

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0623 (реализовала AppLaunchPanel), S0672 (edge-gesture compliance, независим)
- **UI placement:** системная шторка Quick Settings; пользователь сам добавляет/перемещает плитку штатным редактором QS. В приложении новых элементов UI нет.
- **Flavor scope:** все flavor (панель доступна везде, включая photos); тайл в `src/main`, без удаления по flavor.

---

## Ограничения

- **Flavor:** все (панель `AppLaunchPanelActivity` в `src/main`, не удаляется ни в одном flavor, включая photos). Тайл - тоже в `src/main`, без `tools:node="remove"`.
- **API level:** Quick Settings tile - API 24+. На legacy (minSdk 23) `<service>` объявляется, но система не инстанцирует `TileService` до API 24 (как у `AudioToggleTileService`). Спец-гейт не нужен.
- **Локализация:** метка тайла переиспользует существующую локализованную `app_launch_panel_title` (EN/RU/UK), новой строки нет.
- **Доступность:** тайл наследует системную доступность Quick Settings.

---

## Фазы

### Phase 01 - TileService

- [ ] Создать `core/AppLaunchPanelTileService.kt` (мини-аналог `AudioToggleTileService`):
  - `onClick()`: построить `Intent(this, AppLaunchPanelActivity::class.java)` с `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP`; запустить через `startActivityAndCollapse` с обработкой API 34+ (PendingIntent) / older (Intent), как в образце.
  - `onStartListening()`: задать `qsTile.state = STATE_INACTIVE`, label из `R.string.app_launch_panel_title`, icon `R.drawable.ic_view_grid`, `updateTile()`.
  - Логирование только `Timber`.
- **Verification:** `.\a.ps1 fk` компилируется; класс не использует запрещённые API.

### Phase 02 - Manifest + строки + иконка

- [ ] В `app_v2/src/main/AndroidManifest.xml` рядом с `AudioToggleTileService` добавить `<service android:name=".core.AppLaunchPanelTileService" android:icon="@drawable/ic_view_grid" android:label="@string/app_launch_panel_title" android:permission="android.permission.BIND_QUICK_SETTINGS_TILE" android:exported="true">` с `intent-filter` action `android.service.quicksettings.action.QS_TILE_PREFERENCES` и meta `ACTIVE_TILE=false`.
- [ ] Метка тайла - переиспользовать существующую локализованную `app_launch_panel_title` ("Quick launch", уже EN/RU/UK), новую строку не добавлять.
- [ ] Иконка - переиспользовать `ic_view_grid` (сетка плиток), новый ассет не создавать.
- **Verification:** `.\a.ps1 fr` (манифест/ресурсы) проходит.

### Phase 03 - Build + device verify

- [ ] `.\a.ps1 d` (standard debug) собирается.
- [ ] На устройстве: тайл "Quick launch" появляется в редакторе QS-плиток; добавление + тап открывает панель быстрого запуска поверх текущего экрана.
- **Verification:** device-тест на emulator-5556 - тайл присутствует и открывает `AppLaunchPanelActivity`.

---

## Device verification (2026-06-25, emulator-5556)

Подтверждено на устройстве:

- Сборка `standard debug` - BUILD SUCCESSFUL.
- Сервис в merged-манифесте: `dumpsys package` показывает `.core.AppLaunchPanelTileService` с `BIND_QUICK_SETTINGS_TILE`, `exported=true`.
- Система приняла привязку: `settings get secure sysui_qs_tiles` содержит `custom(com.sza.fastmediasorter.debug/.core.AppLaunchPanelTileService)`.
- `onClick` срабатывает: `cmd statusbar click-tile` стартует процесс приложения (`FastMediaSorterApp.onCreate` в logcat).

Не подтверждено на эмуляторе (нужно реальное устройство):

- Визуал «тайл в шторке -> тап -> панель поверх экрана». Этот AVD не рендерит кастомные QS-тайлы в сетке вообще - отсутствует и сторонний `GrayscaleTileService` от Google, при том что все built-in тайлы на месте. Ограничение окружения, не дефекта кода.
- `startActivityAndCollapse` через `cmd statusbar click-tile` блокируется Android 12+ BAL (нет токена реального тапа) - тоже артефакт автоматизации.

Реализация - точный аналог shipped-тайла `AudioToggleTileService` (тот же механизм запуска). На реальном устройстве тап подтверждается logcat-тегом `Timber.d("S0683: ..")` в `onClick`.

---

## Критерии готовности

1. В системной шторке (Quick Settings) доступна плитка приложения, открывающая Панель быстрого запуска.
2. Тайл работает независимо от состояния edge-gesture overlay.
3. Метка тайла локализована EN/RU/UK.

---

## Связи

- S0623 - реализовала AppLaunchPanel (панель + транспарент-хост `AppLaunchPanelActivity`). База.
- S0672 - edge-gesture compliance; независим (тайл - альтернативный вход).

---

## Last Audit

### Manual device test - 2026-06-27 (Galaxy S21+ SM-G996U1, Android 15 / SDK 35, standard debug)

**Verdict: PASS**

The exact gap the emulator could not cover (One UI rendering of a custom QS tile + real-tap launch) is now confirmed on hardware.

- **Tile present in shade editor (expected: tile listed; actual: present).** QS edit mode ("Touch and hold to move buttons") shows the app tile "Быстрый запуск" (Quick launch, `ic_view_grid` icon) as an already-active tile in the live grid. One UI renders the custom tile correctly.
- **Tile tap fires onClick (expected: `S0683:` probe + launch; actual: both).** Logcat: `22:40:49.515 D/AppLaunchPanelTileService S0683: AppLaunchPanel QS tile clicked - launching panel`.
- **Panel opens over foreground app (expected: AppLaunchPanelActivity displayed on top; actual: displayed +104ms, RESUMED on top of MainActivity).** Logcat: `START .. AppLaunchPanelActivity .. (BAL_ALLOW_VISIBLE_WINDOW) result code=0` then `Displayed .. AppLaunchPanelActivity for user 0: +104ms`, `scheduleTopResumedActivityChanged onTop=true`. The Android 12+ BAL block seen on the emulator (no real-tap token) does NOT occur with a genuine tile tap. Panel stayed visible ~3 s (49.5 -> 52.4) until focus changed back to MainActivity on dismiss.

Evidence: `temp/S0683_devtest/` (06_tile_editor.png shows the tile in the editor; 07_editor_scroll.png shows active grid incl. "Быстрый запуск"; logcat extract in this session).

No crash (`mobile_list_crashes` not triggered; LeakCanary watched TileService onDestroy normally after unbind).
