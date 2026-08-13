# S1185 - Baseline detekt разошёлся с CameraCaptureSessionManager

**Status:** Archived
**Priority:** 55
**Created:** 2026-07-24
**Tier:** 2 - Easy (ad-hoc)

## 0. Исходный материал (verbatim)

Обнаружено при реализации S1181, 2026-07-24.

Текущий отчёт `app_v2/build/reports/detekt/detekt.txt` для `CameraCaptureSessionManager.kt` (после `:app_v2:detekt --rerun-tasks`):

```
TooManyFunctions - 40/40 - [CameraCaptureSessionManager] .. :53:7
MultiLineIfElse - [bindToLifecycle] .. :656:52   (applyExposureCompensationForNight)
ReturnCount - [applyHdr] .. :247:9
ReturnCount - [setManualSensor] .. :285:9
ReturnCount - [setAspectRatioAndResolution] .. :303:9
```

Что записано в `config/detekt/baseline-app_v2.xml` для этого файла:

```
ReturnCount:CameraCaptureSessionManager.kt$..$applyMode(videoMode: Boolean)
ReturnCount:CameraCaptureSessionManager.kt$..$applyNightMode(enabled: Boolean)
ReturnCount:CameraCaptureSessionManager.kt$..$switchCamera()
ReturnCount:CameraCaptureSessionManager.kt$..$startFocusAndMetering(x: Float, y: Float)
```

Пересечение пустое: ни одна из четырёх записей baseline не встречается в текущем отчёте, и ни одна из пяти текущих находок не покрыта baseline.

## 1. Симптом

Файл несёт пять находок detekt, ни одна из которых не заглушена baseline. Поэтому диффскоупленный гейт (`post-change.ps1 -ScopeToFile`) падает у любого, кто тронул этот файл, независимо от содержания правки.

В S1181 это проявилось так: правка была доказуемо нейтральной по detekt (см. §2), но `assert-detekt` всё равно выдал FAIL, назвав чужие находки.

## 2. Доказательство, что находки не принадлежат правке S1181

Внутри одной сессии, на одном и том же файле:

- когда вспомогательная функция S1181 была методом класса - `TooManyFunctions - 41/40`;
- когда её вынесли на уровень файла - `TooManyFunctions - 40/40`.

Значит до правки класс уже имел 40 функций и уже перешагивал порог. Остальные четыре находки лежат в функциях, которых правка не касалась: `applyExposureCompensationForNight`, `applyHdr`, `setManualSensor`, `setAspectRatioAndResolution`.

## 3. Корневая причина (уточнено при реализации)

Исходная гипотеза §0 - что 4 записи baseline ссылаются на исчезнувшие сигнатуры - **неверна**. Все четыре метода (`applyMode`, `applyNightMode`, `switchCamera`, `startFocusAndMetering`) в файле есть и на момент правки реально имели по 3 `return` каждый, то есть их записи baseline были **живыми суппрессиями старого долга**, а не устаревшими. Отчёт их «не показывал» ровно потому, что baseline их и глушит - это его работа.

Настоящая ситуация: файл нёс 5 **незаглушённых** находок (`TooManyFunctions 40/40`; `MultiLineIfElse` в `applyExposureCompensationForNight`; `ReturnCount` в `applyHdr`, `setManualSensor`, `setAspectRatioAndResolution`), которые и валили диффскоупленный гейт у любого, кто трогал файл. Плюс 4 заглушённых `ReturnCount` того же класса (`applyMode`, `applyNightMode`, `switchCamera`, `startFocusAndMetering`) - старый долг в baseline.

## 4. Исправление

Довести файл до нулевого detekt-долга по существу, без ратчета baseline:

**4.1** 6 `ReturnCount`-методов (`applyHdr`, `setManualSensor`, `setAspectRatioAndResolution` + заглушённые `applyMode`, `applyNightMode`, `switchCamera`, `startFocusAndMetering`) свернуть до <=2 `return`: объединить последовательные `?: return`/guard'ы в один `if (a == null || b == null || ..) return` (smart-cast локальных `val` держит вызовы non-null). Поведение не меняется.

**4.2** `MultiLineIfElse` в `bindToLifecycle` - обрамить обе ветки `if/else` скобками.

**4.3** `TooManyFunctions 40/40` - вынести чистую утилиту `restoreExif` (+ константу `PRESERVED_EXIF_TAGS`) из класса на файловый уровень (паттерн `handleFinalizeError` из S1181): не читает состояние сессии, вызовы `cropCenter`/`cropToSixteenNine` не меняются. Класс: 40 -> 39 функций.

**4.4** Удалить 4 записи `CameraCaptureSessionManager` из `config/detekt/baseline-app_v2.xml` - после 4.1 они действительно устарели (методы <=2 `return`). Baseline для файла становится пуст.

### 4.1 Вне области

- Не декомпозируем класс на менеджеры и не режем его 763 строки: потолок функций снят выносом одной чистой утилиты, крупный рефактор камеры с device-тестом не требуется.
- Не ратчетим baseline и не регенерируем его целиком: на грязном дереве это заглушило бы чужой долг. Здесь baseline только уменьшается (4 устаревшие записи удалены руками).
- Прочие файлы с устаревшим baseline (§0 гипотеза о масштабе) - не в этом тикете; при обнаружении - отдельный тикет.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1181

## 5. Проверка

- `:app_v2:compileStandardDebugKotlin` - PASS (smart-cast'ы валидны).
- `:app_v2:detekt --rerun-tasks` - `CameraCaptureSessionManager.kt`: 0 находок (были 5 незаглушённых + 4 заглушённых).
- `post-change -ScopeToFile` по файлу - все гейты PASS, detekt scoped PASS.
- Правка поведение-сохраняющая (свёртка guard'ов + вынос чистой функции), функциональных изменений нет - device-тест не требуется.

---

## Last Audit

**Date:** 2026-07-24
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 7 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Static checks

- `:app_v2:compileStandardDebugKotlin` - PASS (guard-fold smart-casts on `provider`/`preview`/`isoRange`/`shutterRange`/`control` compile).
- `:app_v2:detekt --rerun-tasks` (fresh, not cached): `CameraCaptureSessionManager.kt` reports **0** findings. Before: `TooManyFunctions 40/40`, `MultiLineIfElse`, 3x `ReturnCount` unbaselined + 4x `ReturnCount` baselined.
- Root-cause correction: the 4 baseline entries were **live** suppressions (methods still had 3 returns), not references to gone signatures as §0 supposed. After folding those 4 methods to <=2 returns, the entries are genuinely stale and were removed - the file's baseline is now empty.
- `TooManyFunctions` cleared by relocating the pure `restoreExif` + `PRESERVED_EXIF_TAGS` to file level (class 40 -> 39 functions); call sites `cropCenter`/`cropToSixteenNine` unchanged.
- `post-change -ScopeToFile`: ticket-log, neuroslop, flavor-flag, public-mutable-flow, fgs-notification, deprecated-pm, listener-symmetry, detekt(scoped) - all PASS.
- Behavior-preserving refactor (combined null-guards keep identical short-circuit semantics; relocated function reads no session state) - no device test required.
- Backup of the >500 LOC file taken to `temp/S1185/` before editing.

### FEATURES / inventory

- EXEMPT - internal code-quality hygiene, no user-visible capability; `docs/ALL_FEATURES.jsonl` not touched.
