# S0741 - Cross-host command-panel visibility vs draw/overlay gate

**Status:** Archived
**Priority:** 40
**Date:** 2026-06-27
**Tier:** 3 - Moderate
**Source:** Parked finding from the S0676 release-safety audit (2026-06-27).

## Goal

Закрыть последний незакрытый хост в паттерне «не показывать Copy/Move панели поверх полноэкранного оверлея». Async-populate панелей может завершиться после того, как вход в оверлей уже их спрятал, и пере-показать панели поверх него (тот же класс гонки, что и S0676). Исследование показало: контракт callback уже переименован в `shouldShowDestinationPanels()`, а из пяти хостов четыре уже корректны - остался только in-app `PlayerActivity`, причём у него гонка идёт по двум async-путям. Цель: оба пути in-app плеера должны уважать `state.imageEditMode`, ровно тот же источник правды, по которому `PlayerImmersiveModeManager` (S0127) прячет панели при входе в draw/crop. Правка целиком в in-app плеере; контракт и остальные хосты не трогаются.

## 0. Raw capture + research findings

Original capture (S0676 audit): `DestinationButtonsManager.populateDestinationButtons()` решает показ панелей через host-callback, а несколько хостов реализуют его как «открыт ли файл» без учёта transient-оверлеев, поэтому populate-корутина, завершившись после синхронного входа в оверлей, пере-показывает панели поверх него.

Research correction (2026-06-27, this ticket) - state of the code now:

- Callback уже переименован: контракт `DestinationButtonsManager.DestinationButtonsCallback.shouldShowDestinationPanels()` с KDoc «used to prevent overlay/fullscreen races». Цель §2 (правильно названный гейт) уже выполнена на уровне контракта.
- `PhotoVideoStandaloneActivity` - гейт `mediaFile != null && !destinationPanelsSuppressed`; флаг ставится из `onDrawModeChanged`. Закрыт.
- `DocumentStandaloneActivity` - гейт `mediaFile != null && !destinationPanelsSuppressed`; флаг ставится при входе/выходе полноэкранных viewer'ов. Закрыт (несёт `// S0741:` комментарий).
- `TextStandaloneActivity` - `exitFullscreenMode() { /* not exposed in standalone */ }`; подавляющего оверлея не существует. Изменение не требуется.
- `AudioStandaloneActivity` - нет draw/crop/fullscreen-оверлея; аудио не имеет визуального draw-режима. Изменение не требуется.
- in-app `PlayerActivity` (`PlayerFileOpsInitializer.shouldShowDestinationPanels()`) - гейт `state.showCommandPanel || currentFile.type == AUDIO`, НЕ учитывает `state.imageEditMode`. Единственный незакрытый хост.

In-app race is real and double-pathed (neither path checks `imageEditMode`):

1. `DestinationButtonsManager.populateDestinationButtons()` - гейт `shouldShowDestinationPanels()` (выше).
2. `CommandPanelAvailabilityUpdater.update()` - вызывается через `onUpdateCommandAvailability()`, который populate зовёт В КОНЦЕ себя (`DestinationButtonsManager` ~249). Пере-показывает `copyToPanel`/`moveToPanel` по `effectiveShowCommandPanel`.

`ImageDrawOverlayManager.enterDrawMode()` / `ImageCropManager` НЕ меняют `showCommandPanel`; они зовут `editModeCallback` -> `viewModel.setImageEditMode(DRAW|CROP)`. `PlayerObserverManager` наблюдает `imageEditMode` и зовёт `PlayerImmersiveModeManager.apply()`, который при `!= NONE` синхронно прячет `copyToPanel`/`moveToPanel`. Любой из двух async-путей выше, завершившись после этого, пере-покажет панели поверх оверлея, потому что оба смотрят только на `showCommandPanel` (он остаётся true в draw/crop).

## Evidence

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerFileOpsInitializer.kt` ~134-137 (in-app gate, без `imageEditMode`).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt` ~140-145 (второй re-show путь, без `imageEditMode`).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerImmersiveModeManager.kt` ~26-31 (синхронно прячет панели при `imageEditMode != NONE`, S0127).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` 119,637-639 (`imageEditMode` state + `setImageEditMode`).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt` 59,225,249 (контракт + populate + tail `onUpdateCommandAvailability`).

## Phases

### Phase 1 - Gate both in-app re-show paths on imageEditMode

- [x] `PlayerFileOpsInitializer.shouldShowDestinationPanels()`: вернуть `false`, когда `state.imageEditMode != PlayerImageEditMode.NONE`, до существующего `showCommandPanel || AUDIO`. Закрывает populate-путь.
- [x] `CommandPanelAvailabilityUpdater.update()`: добавить `&& imageEditMode == NONE` к вычислению `copyPanelVisible` и `movePanelVisible` (и только к ним - остальная command-panel логика уже скрыта `PlayerImmersiveModeManager.enter()`, прячущим контейнер `topCommandPanel`). Закрывает `onUpdateCommandAvailability`-путь, который populate зовёт в хвосте.
- [x] Контракт `DestinationButtonsCallback` и standalone-хосты не трогать - они уже корректны (Photo/Document) или не имеют подавляющего оверлея (Text/Audio).
- **Verification:** `.\a.ps1 dq` (quiet standard debug) -> `BUILD SUCCESSFUL` (expected: compiles | actual: `BUILD SUCCESSFUL in 1m 3s`, `compileStandardDebugKotlin` recompiled).

### Phase 2 - Static correctness audit (provable invariant)

- [x] Подтвердить по коду: после `PlayerImmersiveModeManager.enter()` (при `imageEditMode != NONE`) оба async-пути теперь возвращают/вычисляют «скрыто», т.е. ни один не может пере-показать панели в edit-режиме. Гонка закрыта по построению, без зависимости от тайминга.
- **Verification:** оба seam'а грепаются с `imageEditMode`-условием; immersive `enter()` прячет те же два view (expected: both paths gated | actual: both gated, see `## Last Audit`).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0676 (the standalone draw fix that surfaced this), S0127 (immersive edit-mode panel hiding), S0192 (draw editor).

## Non-goals

- Изменение контракта `DestinationButtonsCallback` или standalone-хостов (уже корректны / без оверлея).
- Изменение поведения `imageEditMode` / immersive-режима.
- Деривация общего «overlay-state holder» между хостами - не требуется: in-app плеер уже имеет единый `imageEditMode` state, standalone-хосты имеют свои локальные suppressed-флаги, привязанные к их собственным оверлеям.

## Last Audit

**2026-06-27 (spec-all, Simple path)** - Verified.

- Change: in-app `PlayerActivity` only. Both async paths that can re-show the Copy/Move destination panels now gate on `state.imageEditMode == NONE`, the same state `PlayerImmersiveModeManager` (S0127) keys off when it hides those two panels on edit-mode enter:
  - `PlayerFileOpsInitializer.shouldShowDestinationPanels()` - early `return false` when `imageEditMode != NONE` (gates `populateDestinationButtons()`).
  - `CommandPanelAvailabilityUpdater.update()` - `copyPanelVisible`/`movePanelVisible` ANDed with `!editOverlayActive` (gates the `onUpdateCommandAvailability()` pass that populate re-enters at its tail).
- Other hosts unchanged and confirmed correct: `PhotoVideoStandaloneActivity` + `DocumentStandaloneActivity` already gate on `!destinationPanelsSuppressed`; `TextStandaloneActivity` (`exitFullscreenMode` not exposed) and `AudioStandaloneActivity` (no draw/crop overlay) have no suppressing overlay. Callback contract already named `shouldShowDestinationPanels()`.
- Regression safety (provable): when `imageEditMode == NONE` (the common non-edit path) both new conditions are no-ops - the early return is not taken and `!editOverlayActive` is `true` - so existing behaviour is byte-identical outside draw/crop. The gate only suppresses the panels while an image-edit overlay is active, which is exactly when immersive mode has already hidden them.
- Build gate: `.\a.ps1 dq` -> `BUILD SUCCESSFUL in 1m 3s`; `compileStandardDebugKotlin` recompiled (not UP-TO-DATE).
- Device test: not required - the fix is a static invariant tightening (no-op outside edit mode, provably closes both re-show seams). An on-device smoke of draw-mode panel suppression is an optional, non-blocking confidence check.
