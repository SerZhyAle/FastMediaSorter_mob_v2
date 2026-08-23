# Спецификация (compact bugfix): S1890 - Отменённое сканирование MediaStore запускает полный legacy-обход

**Ticket:** S1890
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-21
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-21

**Текст:**

Найдено при анализе `logs/fastmediasorter_20260821_002013.log` (noLegal-DEBUG 2.60.8210.017, SM-S731B, Android 16).

Лог-эвиденс, строки 8891-8899:

```
09:37:14.648 D/App: ScanMetrics: begin scan resourceId=1 type=LOCAL expected_file_count=17
09:37:14.652 D/App: LocalMediaScanner.scanFolder: START - path='/storage/emulated/0'
09:37:14.655 D/App: LocalMediaScanner: Querying MediaStore for path='/storage/emulated/0'
09:37:15.106 W/App: MediaStore scan failed, falling back to legacy File API [JobCancellationException: Job was cancelled]
09:37:15.107 D/App: LocalMediaScanner: Falling back to scanFolderLegacy for path='/storage/emulated/0'
09:37:15.108 D/App: LocalMediaScanner.scanFolderLegacy: START - path='/storage/emulated/0'
```

Корутину отменили - и вместо того, чтобы остановиться, сканер трактует отмену как "MediaStore не сработал" и уходит в полный обход `/storage/emulated/0` через File API. Самый дорогой путь запускается ровно в тот момент, когда результат уже никому не нужен.

Наблюдение (не расследование): в `LocalMediaScanner` два блока `catch (e: Exception)` без `e.rethrowIfCancellation()` - ветка папки Camera и основная ветка (около строк 100 и 129). Помощник `rethrowIfCancellation()` уже есть в `core/util/CoroutineExt.kt` и применяется в других сканерах.

Смежное: этот же файл-помощник неправильно применён в облачном сканере, парковано отдельным тикетом.

---

## 1. Проблема / симптом

Отмена сканирования не останавливает работу, а переключает её на самый дорогой путь. На noLegal-DEBUG 2.60.8210.017 (SM-S731B, Android 16) отмена запроса к MediaStore записывается в журнал как отказ MediaStore, после чего запускается полный обход `/storage/emulated/0` через File API - обход всего внутреннего хранилища ради результата, который уже отменён. Пользователь видит это как затянувшееся сканирование и лишний расход батареи после ухода с экрана; на устройствах, где пересоздание экрана штатно (складные - каждое сложение отменяет работу в полёте), это повторяется при каждом таком событии.

---

## 2. Корневая причина

Отмена корутины приходит как `CancellationException`, а это обычный `Exception`, поэтому широкий `catch (e: Exception)` ловит её наравне с настоящим отказом MediaStore. Обе ветки сканирования папки в `LocalMediaScanner` устроены так, что за `catch` безусловно следует переход к `scanFolderLegacy`, то есть пойманная отмена не просто теряется - она превращается в команду начать самую тяжёлую работу.

Проверено по дереву 2026-08-21:

- `data/local/LocalMediaScanner.kt:100` - ветка папки Camera: `catch (e: Exception)` пишет предупреждение, а следующая строка безусловно возвращает `scanFolderLegacy(cameraPath, ..)`.
- `data/local/LocalMediaScanner.kt:129` - основная ветка: `catch (e: Exception)` пишет ровно то предупреждение, что стоит в логе, и управление уходит на `scanFolderLegacy(path, ..)`.
- Ни один из двух блоков не вызывает `rethrowIfCancellation()`, и помощник в этот файл даже не импортирован.
- Соседний метод `scanRecentFiles` (строка 160) ту же ситуацию обрабатывает верно, отдельным `catch (e: CancellationException) { throw e }` перед широким catch - то есть в этом же файле уже есть образец правильного поведения, просто он не применён к двум веткам выше.

---

## 3. Исправление

Оба широких блока переводятся на `e.warnUnlessCancellation("<сообщение>")` - однострочный помощник в `core/util/CoroutineExt.kt`, который первым делом вызывает `rethrowIfCancellation()`, а затем пишет то же предупреждение, что стояло в блоке раньше. Отмена снова становится отменой: она проходит наружу, `scanFolderLegacy` не запускается, а настоящий отказ MediaStore по-прежнему логируется и по-прежнему приводит к резервному обходу.

**Почему помощник однострочный, а не голый `rethrowIfCancellation()` плюс отдельный `Timber.w`.** Раздельная пара занимает на строку больше в каждом из двух блоков, а `scanFolder` уже близок к порогу длины метода в detekt - две лишние строки его превышают. Кроме того, guard, стоящий отдельной строкой без видимого эффекта, - ровно то, что следующий редактор удаляет как шум; внутри `warnUnlessCancellation` он неудаляем, потому что без него помощник теряет смысл. Рационал зафиксирован в KDoc помощника.

Существующий явный `catch (e: CancellationException)` в `scanRecentFiles` не трогается: он уже корректен, а переписывание работающей ветки на помощника меняло бы содержимое без изменения поведения и без нужды.

**Вызывающая сторона, которую нельзя оставить как есть.** Раз отмена теперь выходит из `scanFolder`, проверено, что с ней делают вызывающие:

- `domain/usecase/GetMediaFilesUseCase.kt` (строки 289 и 355) уже ловит `CancellationException` отдельными блоками до широкого - здесь всё верно, менять нечего.
- `ui/player/standalone/StandaloneFolderPagingManager.kt:58` оборачивает вызов в `runCatching { .. }.getOrElse { .. }`, а `runCatching` ловит `Throwable`, то есть и отмену. До этого исправления отмена туда не доходила - её съедал сам сканер; после исправления дойдёт и будет проглочена там, только уже с записью «scan failed». Это ровно то же проглатывание, лишь этажом выше, поэтому оно чинится тем же изменением: `error.rethrowIfCancellation()` первым оператором в `getOrElse`. Оставить это на отдельный тикет означало бы сдать исправление, которое переносит дефект, а не убирает его.

**Побочное следствие, которое придётся закрыть тем же изменением.** Импорты этого файла уже отсортированы неверно - `utils.SafHelper` стоит перед `util.VirtualPathUtils`, хотя `util.` сортируется раньше `utils.` - и это зафиксировано в `config/detekt/baseline-app_v2.xml` записью `ImportOrdering`. Подпись такой записи - весь блок импортов целиком, поэтому добавление одной строки перестанет ей соответствовать, и диффскоупный гейт покажет чужой долг как новую находку этого тикета. Поэтому пара переставляется в правильный порядок в том же изменении: долг чинится, а не заглушается.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1889 (та же защита, облачный сканер)

---

## 4. Проверка

1. `pwsh -NoProfile -File ./a.ps1 fk` завершается кодом 0.
2. Юнит-тест: отменённый запрос к MediaStore приводит к выбросу `CancellationException` из `scanFolder`. Отсутствие обхода проверяется этим же утверждением, а не отдельным: `scanFolderLegacy` вызывается строкой после блока `catch`, поэтому выброс наружу означает, что до него не дошло.
3. Юнит-тест: обычное исключение из MediaStore по-прежнему приводит к резервному обходу, то есть исправление не отключило сам резерв.
4. `Grep` - `warnUnlessCancellation` встречается в `LocalMediaScanner.kt` дважды, файл импортирует помощника, и ни один из двух блоков не содержит безусловного перехода к `scanFolderLegacy` без него.
5. `Grep` - `StandaloneFolderPagingManager.kt` вызывает `rethrowIfCancellation()` первым оператором в `getOrElse`.
6. `post-change.ps1 -ScopeToFile` завершается кодом 0.

---

## Last Audit

**Date:** 2026-08-22
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

Evidence:

- `a.ps1 fk` - exit 0 (`compileStandardDebugKotlin`, BUILD SUCCESSFUL).
- `check-standard-fast.ps1 -Mode Unit -Tests com.sza.fastmediasorter.data.local.LocalMediaScannerTest` - exit 0; class report 16 tests, 0 failures, 0 errors, covering both the cancellation rethrow and the surviving legacy fallback on a real failure.
- `LocalMediaScanner.kt` imports `warnUnlessCancellation` and calls it in both broad blocks (Camera branch line 102, main branch line 133); neither block reaches `scanFolderLegacy` without it.
- `StandaloneFolderPagingManager.kt` calls `error.rethrowIfCancellation()` as the first statement of `getOrElse`.
- Imports of `LocalMediaScanner.kt` sorted (`util.VirtualPathUtils` before `utils.SafHelper`); the stale `ImportOrdering` baseline entry it was frozen under is pruned from `config/detekt/baseline-app_v2.xml`, verified by a fresh `detekt.xml` with zero `LocalMediaScanner.kt` findings.
- `post-change.ps1 -Files <5 files> -ScopeToFile -ChangeType Mixed` - exit 0, `post-change: PASS`.
- Debug-tag invariant: zero `Timber.d("S1890:` lines in `.kt` (status is not `BlockNeedUserTest`).
- `check-open-items-carried.ps1 -Id S1890` - exit 0 (no research section).
