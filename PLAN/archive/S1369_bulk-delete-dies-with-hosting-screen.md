# Стратегическая спецификация: S1369 - Массовое удаление переживает закрытие Browse

**Ticket:** S1369
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-03
**Tier:** 3 - Moderate (ad-hoc)
**Tactical plan:** `PLAN/S1369_bulk-delete-dies-with-hosting-screen/INDEX.md`

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-03

**Текст:**

Найдено при исследовании S1362 (массовая копия/перенос умирает вместе с экраном Browse).

`DeleteDialog` выполняет массовое удаление в scope хостящего экрана:

```
app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DeleteDialog.kt:64
(context as? LifecycleOwner)?.lifecycleScope?.launch { .. fileOperationUseCase.executeWithProgress(..) }
```

Та же архитектурная дыра, что в S1362: уход с экрана (Back, поворот, вытеснение системой) убивает операцию на произвольном файле. Для локального удаления это редко успевает проявиться, но для удаления на сетевом или облачном ресурсе, где каждый файл занимает секунды, состояние остаётся применённым наполовину и нигде не зафиксированным.

В приложении уже есть рабочий образец фонового исполнения - `BrowseFileTransferRequest` + `BrowseFileTransferCoordinator` + `BrowseFileTransferWorker`. Путь удаления его не использует.

Дедуп: `search.ps1` по "DeleteDialog" и "bulk delete" - совпадений нет.

---

## 0. Approval Gate (owner input)

- **Goal:** Provided by user - prevent bulk delete from dying with its Browse host screen.
- **Scope:** Delegated by user - /spec-all auto-approval. Only the Browse bulk-delete path and its existing WorkManager transfer infrastructure; no unrelated deletion surfaces.
- **UX:** Delegated by user - /spec-all auto-approval. Keep the existing confirmation, progress, cancellation and terminal-message patterns already used by Browse copy/move.
- **Data safety:** Delegated by user - /spec-all auto-approval. Preserve the current soft-delete decision, explicit cancel semantics and Android permission flow; no schema migration or destructive default change.

## 1. Проблема / симптом

Массовое удаление на медленном назначении обрывается при уходе с экрана, без уведомления и без возможности возобновить.

---

## 2. Корневая причина

Первоначальный якорь из inbox неточен: `DeleteDialog` не имеет production call site. Реальный путь Browse идёт через `BrowseDeleteManager.deleteSelectedFiles`, который запускает работу в `viewModelScope`. Уничтожение Browse отменяет scope и прерывает файловое или сетевое удаление между элементами.

Уже существующий `BrowseFileTransferWorker` живёт в WorkManager и переживает lifecycle UI, но поддерживает только COPY/MOVE. Поэтому массовое удаление не пользуется тем же устойчивым путём и не публикует его terminal event.

---

## 3. Цели

1. Массовое удаление файлов и каталогов из Browse продолжается после Back, rotation и пересоздания Browse.
2. Явная отмена остаётся единственным действием, отменяющим фоновую работу.
3. Успех, частичный успех, ошибка, повторная авторизация и Android batch-delete permission показываются через существующий Browse transfer UI.
4. Soft delete, удаление каталогов и per-file overflow удаление сохраняют текущую семантику.

**Non-goals:**

- Не менять не-Browse пути удаления, включая неиспользуемый `DeleteDialog`.
- Не менять формат хранения, Room-схему, Hilt scopes или пользовательские настройки.

## 4. Исправление

Расширить persistable `BrowseFileTransferRequest` и `BrowseFileTransferWorker` операцией DELETE. `BrowseDeleteManager` формирует request из текущего selection и ставит его через coordinator вместо запуска lifecycle-scoped coroutine. Общий observer terminal events выполняет обновление списка, selection, undo и ошибок после возвращения UI.

### 4.1 Основные модули

- Request/worker: DELETE operation, file and directory execution, foreground notification and terminal payload.
- Browse delete launch: перевод selection в request без потери network/cloud path и soft-delete policy.
- Browse terminal UI: единая обработка DELETE результата, permission и undo.

## 5. Риски и решения

- Directory delete не проходит через `FileOperationUseCase`; worker должен вызывать существующий `DeleteDirectoriesUseCase` и агрегировать результат с файлам.
- `BrowseFileTransferRequest` persisted JSON уже используется COPY/MOVE; новые поля должны иметь безопасные defaults и explicit wire names.
- Android batch-delete permission требует живой Activity; worker сохраняет terminal event, а Browse при reattach запускает existing permission callback.

## 6. Открытые вопросы / Research items

1. **Delete launch path and worker capability**
   - **Статус:** Resolved
   - **Артефакт:** `temp/S1369/research.md`

## 7. Архитектурное решение

**ADR-1: Browse bulk delete uses the existing persisted WorkManager transfer contract.**

The existing coordinator is the sole owner of interactive background-work state. Reusing it keeps lifecycle ownership out of the ViewModel and preserves one foreground-notification/terminal-event contract for all Browse bulk operations.

## 8. Влияние на пользователя (docs/FEATURES)

Новая capability не добавляется. Existing delete behavior становится устойчивым при уходе с экрана; `docs/FEATURES*.md` не меняются.

## 9. Критерии готовности

1. Worker accepts a persisted DELETE request and emits the matching terminal event.
2. Browse starts a multi-file DELETE through `BrowseFileTransferCoordinator`, not `viewModelScope` execution.
3. Closing and reopening Browse does not cancel a slow network/cloud delete; the user can reattach to progress and receive the terminal result.
4. Explicit Cancel still cancels the worker.
5. Existing soft-delete, directory and Android permission paths retain their expected results.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1362 (bulk-file-operation-dies-with-browse-screen) - тот же класс дефекта для копии/переноса, найден в одном исследовании; S1224 (player-copy-move-background, Draft).

---

## 10. Проверка

- Unit-test persisted DELETE request and DELETE terminal mapping.
- Unit-test worker conversion and aggregate result behavior where dependencies are injectable.
- On-device: delete 50+ files on a slow cloud or network resource, leave Browse, return from the notification and confirm completion or partial state is visible.

## Last Audit

**Date:** 2026-08-03
**Verdict:** BlockNeedUserTest

- P0: none found. DELETE execution is owned by the existing WorkManager worker rather than an Activity or ViewModel scope.
- P1: none found in static review. Cancellation is still surfaced through the worker's existing `CancellationException` terminal event and explicit cancel action.
- P2: the old direct delete implementation remains as an unused internal legacy path and should be removed when the device scenario confirms the new path.
- P3: none.

**Automated evidence:**

- expected: Kotlin compile succeeds | actual: `pwsh -NoProfile -File a.ps1 fk` exit 0 on 2026-08-03.
- expected: scoped fast gates pass | actual: `assert-fast-gates.ps1 -ChangedFiles ...` exit 0.
- expected: transfer serialization tests have no failures | actual: fresh `BrowseFileTransferModelsSerializationTest` XML reports 5 tests, 0 failures, 0 errors.

**Manual gate:** Delete 50+ files from a deliberately slow cloud/network resource, press Back or rotate while the operation is active, reopen Browse from the notification, and confirm a terminal result. Capture logcat line `S1369: enqueued persistent Browse bulk delete`.
