# Спецификация (compact bugfix): S1212 - установка APK из облака проглатывает отмену корутины

**Ticket:** S1212
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-27
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-27

**Текст:**

Найдено при разборе логов через `/log-reader` (пять сессий noLegal-debug на SM-S731B, 2026-07-26..27). Не связано с задачей анализа логов, требует отдельного расследования.

Лог `logs/fastmediasorter_20260727_053445.log`, строки 1865-1868:

```
2026-07-27 14:54:00.321 E/App: cloud APK download threw
kotlinx.coroutines.JobCancellationException: UndispatchedCoroutine was cancelled; job=UndispatchedCoroutine{Cancelling}@38002db

kotlinx.coroutines.JobCancellationException: UndispatchedCoroutine was cancelled; job=UndispatchedCoroutine{Cancelling}@38002db
```

Контекст в логе: пользователь ушёл с экрана (соседние строки - TOUCH в `StreamsActivity`), скоуп загрузки был отменён.

Код-эвиденс, `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandlerImpl.kt:153-171`:

```kotlin
act.lifecycleScope.launchWhenStarted {
    val downloaded = withContext(Dispatchers.IO) {
        runCatching {
            cloudFileOperationHandler.downloadFromCloudToPublic(..)
        }.getOrElse { e ->
            Timber.e(e, "cloud APK download threw")
            false
        }
    }
    if (downloaded && cacheApkFile.exists() && cacheApkFile.length() > 0L) {
        launchSystemInstaller(cacheApkFile, file.name)
    } else {
        Timber.w("cloud APK download reported failure for ${file.name}")
        Toast.makeText(act, R.string.s0183_apk_install_failed, Toast.LENGTH_SHORT).show()
    }
}
```

Три связанных дефекта в одном месте:
1. `runCatching` ловит `CancellationException` наравне с настоящими ошибками - штатная отмена корутины пишется в лог как `Timber.e` со стеком.
2. После проглоченной отмены выполнение продолжается в ветку `else`, то есть отменённый поток ещё и показывает Toast «установка не удалась» - пользователь получает сообщение об ошибке там, где ошибки не было.
3. Используется устаревший `lifecycleScope.launchWhenStarted` - именно он и порождает описанную отмену/приостановку при уходе с экрана.

---

## 1. Проблема / симптом

Загрузка APK из облака для установки логирует штатную отмену корутины как ошибку уровня E и после этого продолжает работать: показывает пользователю Toast о неудачной установке, хотя операция была просто отменена уходом с экрана. Наблюдается на flavor `noLegal`, экран браузера файлов, при уходе с экрана во время скачивания.

---

## 2. Корневая причина

Все три дефекта живут в одном методе - `BrowseApkInstallHandlerImpl.downloadAndInstallFromCloud`, строки 153-172.

**2.1 `runCatching` глотает отмену (подтверждено логом).**

- `runCatching` перехватывает `Throwable`, то есть и `CancellationException` наравне с настоящими сбоями загрузки.
- Уход с экрана отменяет `lifecycleScope` активити, отмена доезжает до вложенного `withContext` внутри `CloudFileOperationHandler.downloadFromCloudTo` и всплывает как `JobCancellationException`.
- `getOrElse` пишет её через `Timber.e(e, "cloud APK download threw")` со стеком - штатное завершение выглядит в логе как ошибка уровня E.
- В проекте уже есть канонический приём для этого случая: `if (t is CancellationException) throw t` перед логированием (`StreamHealthProbeManager`, `StreamFrameSnapshotManager`, `Media3SegmentDownloader`, `MediaMuxerRemuxer`, `ManifestDrmDetector`). Здесь он просто не применён.

**2.2 Отмена превращается в «загрузка не удалась» (латентный дефект).**

- После проглоченной отмены `getOrElse` возвращает `false`, то есть отмена неотличима от реального провала загрузки.
- Когда отменён job самого внешнего `withContext`, тот всё равно завершится `CancellationException` и ветка `else` не выполнится - именно поэтому в захваченном логе видна запись `Timber.e`, но нет доказательства Toast.
- Отмена, пришедшая не от родителя, а изнутри дочернего скоупа, до `else` доедет и покажет Toast «установка не удалась» там, где ошибки не было. Опираться на это различие нельзя: значение `false` уже потеряло информацию о причине.

**2.3 Устаревший `launchWhenStarted`.**

- `LifecycleCoroutineScope.launchWhenStarted` помечен `@Deprecated` начиная с lifecycle 2.6; проект собирается на `androidx.lifecycle:*:2.7.0`, то есть touched-файл компилируется с deprecation-предупреждением (CLAUDE.md Rule 7).
- Это единственное использование `launchWhenStarted` во всём `app_v2/src` - точечная замена, а не массовая миграция.
- Рекомендованная замена для одноразовой операции - `lifecycleScope.launch` для самой загрузки плюс `Lifecycle.withStarted` для показа UI. Такой приём в проекте уже есть: `LinkAutoDownloadResultPresenter` использует `host.lifecycle.withResumed`.

---

## 3. Исправление

Правится один файл: `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandlerImpl.kt`.

- Пробрасывать отмену: внутри `getOrElse` добавить `if (e is CancellationException) throw e` перед `Timber.e`. Отмена перестаёт логироваться как ошибка и перестаёт превращаться в `false`.
- Заменить `act.lifecycleScope.launchWhenStarted { .. }` на `act.lifecycleScope.launch { .. }` - сама загрузка идёт в `withContext(Dispatchers.IO)` и от диспатчера жизненного цикла не зависит.
- Обернуть решение о UI (запуск системного установщика либо Toast о неудаче) в `act.lifecycle.withStarted { .. }`, чтобы сохранить прежнюю гарантию: результат показывается только когда экран действительно виден, без старта активити из фона.
- Импорты: добавить `kotlinx.coroutines.CancellationException`, `kotlinx.coroutines.launch`, `androidx.lifecycle.withStarted`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- Компиляция flavor `noLegal`: `.\a.ps1 fkn` - exit 0.
- Файл больше не содержит `launchWhenStarted`: grep по `app_v2/src` даёт ноль совпадений.
- Файл содержит рефлекс отмены: `if (e is CancellationException) throw e` присутствует в `downloadAndInstallFromCloud`.
- Гейты качества: `scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile` - exit 0.
- Поведенческая проверка на устройстве не требуется для закрытия: дефект доказан статически (проглатывание `CancellationException`), а сценарий воспроизводится только на `noLegal` с облачным аккаунтом и APK в облаке. Регресс ловится компиляцией и grep-предикатами выше.

---

## Last Audit

**Дата:** 2026-07-27 · **Вердикт:** Verified

Реализация - `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandlerImpl.kt`, метод `downloadAndInstallFromCloud` (строки 143-181).

- §3 «пробрасывать отмену» - закрыто: `if (e is CancellationException) throw e` стоит первым в `getOrElse` (строка 168), до `Timber.e`. Отмена больше не попадает в лог уровня E и не деградирует в `false`.
- §3 «убрать `launchWhenStarted`» - закрыто: `act.lifecycleScope.launch`; grep по `app_v2/src` даёт 0 совпадений `launchWhenStarted`.
- §3 «UI только при видимом экране» - закрыто: решение об установщике либо Toast обёрнуто в `act.lifecycle.withStarted { .. }`, что снимает риск старта активити из фона, который появился бы при простой замене на `launch`.
- §3 «импорты» - закрыто: добавлены `androidx.lifecycle.withStarted`, `kotlinx.coroutines.CancellationException`, `kotlinx.coroutines.launch`.

Доказательства:

- `pwsh -NoProfile -File ./a.ps1 fkn` - `BUILD SUCCESSFUL in 1m 10s`, `Fast check passed.`, exit 0. Предупреждений по правленому файлу нет; deprecation `launchWhenStarted` исчез.
- `scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile` - `post-change: PASS (Kotlin, 40303 ms)`, exit 0. detekt scoped PASS (нет находок среди изменённых файлов), neuroslop PASS, listener-symmetry PASS, ticket-log-audit `expected: 0 | actual: 0`.
- Grep-предикаты §4: `launchWhenStarted` в `app_v2/src` - 0; `if (e is CancellationException) throw e` - присутствует.

Остаточные замечания:

- `CloudFileOperationHandler.downloadFromCloudTo` возвращает голый `Boolean`, поэтому вызывающая сторона не отличает «нет прав» от «сеть отвалилась» и показывает один общий Toast. Это ограничение контракта самого обработчика, вне области S1212.
- Ветка `else` по-прежнему объединяет «загрузка вернула false» и «файл пустой либо не создан». Разделять их без более информативного возврата из §5 бессмысленно.
