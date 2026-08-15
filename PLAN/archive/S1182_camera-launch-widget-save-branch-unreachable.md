# S1182 - Ветка сохранения виджета «Камера» недостижима

**Status:** Archived
**Priority:** 50
**Created:** 2026-07-24
**Tier:** 2 - Easy (ad-hoc)

## 0. Исходный материал (verbatim)

Обнаружено при device-тесте S1174 на emulator-5554 (sdk_gphone64_x86_64, Android 15 / SDK 35), 2026-07-24.

Сообщение исполнителя:

> `CameraLaunchWidgetManager.onCaptureResult`'s `RESULT_OK` branch - the whole `SaveCapturedMediaUseCase`
> path plus its save/IO/generic toasts - is unreachable from every entry point. `CameraLaunchActivity`
> always opens the host via `CameraCaptureContract.createSwitchableIntent`
> (`app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchWidgetManager.kt`,
> `launchCaptureIntent()`), and that builder defaults `multiCapture = true`, so the host persists
> captures itself and returns `RESULT_CANCELED` on close. Either dead code or a lost feature,
> depending on intent.

Артефакты прогона: `temp/S1174/check2_cameralaunch.log`.

## 1. Симптом

Снимок через виджет «Камера» на диск попадает, но не тем путём, который описан в коде менеджера: файл сохраняет сам хост камеры, а `CameraLaunchWidgetManager` получает `RESULT_CANCELED` (`code=0`) и уходит в ветку отмены.

Прямые следствия:

- вызов `SaveCapturedMediaUseCase` из этого менеджера не происходит никогда;
- тосты «сохранено» / «ошибка ввода-вывода» / «ошибка сохранения» на этом маршруте не показываются;
- `clearPending()` удаляет `$base.jpg` и `$base.mp4` в scratch-папке на каждом закрытии камеры, полагая снимок неудавшимся.

## 2. Вероятная причина

`CameraCaptureContract.createSwitchableIntent` по умолчанию включает `multiCapture = true`. В этом режиме хост рассчитан на серию кадров и сохраняет их сам, а по закрытию возвращает `RESULT_CANCELED` - `RESULT_OK` не приходит в принципе.

`CameraLaunchWidgetManager` при этом написан под однокадровый контракт: ждёт `RESULT_OK`, читает путь из `data` и сохраняет через use case. Два контракта разошлись, и рассинхрон никем не ловится, потому что снимок всё равно оказывается на диске - просто чужими руками.

## 3. Замысел (разрешён из кода) и решение

Замысел выяснен из зеркала. `MainCameraCaptureManager` (обзорный вход «Камера») тоже зовёт `createSwitchableIntent(.., multiCapture = true)` и в этом режиме **намеренно не сохраняет** - комментарий S0566/ADR-2: «the host already saved every in-session capture to its public folder; the manager must not move anything». То есть host-save - это и есть замысел, а `SaveCapturedMediaUseCase`-ветка виджета - устаревший остаток однокадрового контракта до ADR-2. KDoc `CameraLaunchWidgetManager`, обещавший сохранение через use case, тоже устарел.

`CameraLaunchWidgetManager` всегда открывает хост через `createSwitchableIntent` (обе точки входа, включая `forceVideo`), то есть всегда `multiCapture = true`. Значит `RESULT_OK`-ветка недостижима на **всех** маршрутах - это чистый мёртвый код.

Побочные вопросы §4 закрыты: (1) умолчание `multiCapture` не трогаем - `MainCameraCaptureManager` на него опирается; (2) файл кладётся в публичную папку хостом (доказано пробой S1174 check2: `/sdcard/Movies/..mp4`); (3) `clearPending()` удаляет `dir/$base.jpg|.mp4` в **app-private** scratch, а не публичный результат хоста (имя/папка иные) - это корректная уборка, а не удаление чужого файла (та же логика в `clearPending` зеркала).

## 4. Исправление

Привести виджет-менеджер к доказанному host-save контракту (вариант «удалить мёртвый код»):

**4.1** `CameraLaunchWidgetManager.onCaptureResult(resultCode, data)` -> `onCaptureResult()`: только `clearPending()` + `finish()` (ветка `if (multiCapture)` зеркала). Удалить весь `RESULT_OK`-путь (`readResultMediaKind`/`readResultOutputPath`, вызов `saveCapturedMedia`, тосты save/IO/generic).

**4.2** Убрать зависимость `saveCapturedMedia: SaveCapturedMediaUseCase` из конструктора менеджера и неиспользуемые импорты (`SaveResult`, `SaveCapturedMediaUseCase`).

**4.3** `CameraLaunchActivity`: убрать `@Inject saveCapturedMedia` + его импорт, аргумент конструктора и упростить `captureLauncher` до `onCaptureResult()`.

**4.4** Обновить устаревший KDoc менеджера: описать host-save (ADR-2) вместо сохранения через use case.

### 4.1 Вне области

- Не выключаем `multiCapture` и не вводим однокадровый save для виджета: host-save - подтверждённый ADR-2 контракт, а не дефект; файл доходит до публичной папки.
- Не трогаем `clearPending()`: удаляет только app-private scratch, корректно.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1174, S0568, S0566

## 5. Проверка

- `:app_v2:compileStandardDebugKotlin` - BUILD SUCCESSFUL (DI-граф `CameraLaunchActivity` без `SaveCapturedMediaUseCase` собирается; неиспользуемых символов/импортов нет).
- `CameraLaunchWidgetManager.onCaptureResult` не содержит `RESULT_OK`/`saveCapturedMedia`; конструктор без `saveCapturedMedia`.
- `:app_v2:detekt` - оба файла без находок.
- Поведение-сохраняюще: удалённая ветка была недостижима (host-save доказан пробой S1174), функциональных изменений нет - device-тест не требуется.

---

## Last Audit

**Date:** 2026-07-24
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Static checks

- `:app_v2:compileStandardDebugKotlin` - PASS (no `e:` errors, no unused-symbol warnings after removing the `SaveCapturedMediaUseCase` field from the `@AndroidEntryPoint` activity and the manager ctor).
- `CameraLaunchWidgetManager`: `onCaptureResult()` is now `clearPending()` + `finish()`; the `RESULT_OK` / `readResult*` / `saveCapturedMedia` path and its save/IO/generic toasts are gone; ctor no longer takes `saveCapturedMedia`; `SaveResult` / `SaveCapturedMediaUseCase` imports removed.
- `CameraLaunchActivity`: `@Inject saveCapturedMedia` + import removed, ctor arg dropped, `captureLauncher` simplified to `onCaptureResult()`.
- Design resolved from code, not guessed: the mirror `MainCameraCaptureManager` uses `multiCapture = true` and deliberately does not save (S0566/ADR-2 comment); the widget is always multi-capture, so the removed branch was unreachable on every route (incl. `forceVideo`).
- `:app_v2:detekt --rerun-tasks`: `CameraLaunchWidgetManager.kt` and `CameraLaunchActivity.kt` - 0 findings.
- `post-change -ScopeToFile`: gates PASS.
- Behavior-preserving (dead-code removal; host-save proven by S1174 check2 `/sdcard/Movies/`) - no device test required.

### FEATURES / inventory

- EXEMPT - internal dead-code removal, no user-visible capability change (the widget already saved via the host); `docs/ALL_FEATURES.jsonl` not touched.
