# Стратегическая спецификация: S1362 - Фоновая массовая операция переживает закрытие Browse

**Ticket:** S1362
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-02
**Tier:** 3 - Moderate (ad-hoc)
**Tactical plan:** `PLAN/S1362_bugfix-bulk-file-operation-dies-with-browse-screen/INDEX.md`

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-02

**Текст:**

Автозахват при анализе удалённого лог-бандла (`/newlog`), сессия `logs/fastmediasorter_20260801_183450.log`, устройство SM-S731B, Android 16 / API 36, сборка `2.60.7302.058-NoLegal-DEBUG`.

Запущен перенос 616 файлов в облачный ресурс (Google Drive). Через 7 секунд пользователь нажал Back, затем ещё раз. Операция была снята вместе с экраном: успела уйти одна первая загрузка, остальные 615 файлов не обработаны. В лог ушла ошибка уровня E, никакого уведомления, подтверждения или возобновления.

Лог-строки:

```
[29734] 20:31:27  I  performOperation: ENTRY - destination=temp (cloud:/google_drive/0B2Wse..), operationType=MOVE, sourceFiles=616
[30354] 20:31:27  D  [1202b180|file-operation] START executeWithProgress
[30364] 20:31:27  D  uploadToCloudFromPath: START - sourcePath=/storage/emulated/0/Download/762864111_..._n.jpg
[30399] 20:31:34  D  CLICK: Back (BrowseActivity)
[30430] 20:31:34  D  onDestroy: BrowseActivity
[30544] 20:31:48  D  CLICK: Back (BrowseActivity)
[30565] 20:31:49  D  BrowseShutdownCoordinator.onShutdown: cancelled ops for cloud://GOOGLE_DRIVE/0B2Wse..
[30576] 20:31:49  D  onDestroy: BrowseActivity
[30587] 20:32:01  E  [1202b180|file-operation] EXCEPTION in executeInternal
```

Стек:

```
kotlinx.coroutines.JobCancellationException: Job was cancelled; job=JobImpl{Cancelling}@7b674dd
```

Тот же сценарий повторился через минуту: `[30845] 20:32:31 E [820aecc9|file-operation] EXCEPTION in executeInternal`.

---

## 1. Проблема

Долгая массовая операция копирования/переноса, запущенная из диалога выбора назначения, живёт в scope этого экрана. Уход с экрана (Back, поворот, вытеснение системой) тихо убивает её на любом файле.

Наблюдалось на облачном назначении, где каждый файл занимает секунды, поэтому потеря видна: из 616 файлов уехал один. На локальном назначении та же дыра просто редко успевает проявиться.

Что делает поведение именно дефектом, а не осознанной отменой:

- Пользователь не отменял операцию, он ушёл с экрана.
- Обработчик отмены в `FileOperationDestinationDialog` показывает Toast "перенос отменён", но Activity к этому моменту уничтожена, то есть пользователь не узнаёт ничего.
- В приложении уже есть `worker/BrowseFileTransferWorker.kt`, который вызывает тот же `executeWithProgress` и переживает уход с экрана - путь через диалог его не использует.
- Результат для MOVE особенно неприятен: часть файлов перенесена, часть нет, состояние наполовину применено и нигде не зафиксировано.

---

## 2. Цели

### Посылка тикета опровергнута

Операция **не** выполнялась в scope экрана. Тот же лог-бандл (`logs/fastmediasorter_20260801_183450.log`) содержит строку, которой нет в исходной выдержке:

```
30352  20:31:27.771  I  BrowseFileTransferCoordinator: enqueued workId=296fc6fd-79df-4855-98bb-0b2180e27c94
30353  20:31:27.772  I  BrowseFileOperationsManager: background transfer enqueued workId=296fc6fd..
30354  20:31:27      D  [1202b180|file-operation] START executeWithProgress
```

То есть `BrowseFileOperationsManager.showMoveDialog` передал `onOperationRequested`, `BrowseFileTransferCoordinator.enqueueIfIdle` записал заявку и поставил уникальную работу, а `executeWithProgress` крутился уже внутри `BrowseFileTransferWorker` - процессного `CoroutineWorker`, который переживает смерть Activity. Все пять массовых переносов той сессии прошли через воркер, а не через диалог.

Подтверждает это и то, что первый Back (20:31:34) с `onDestroy: BrowseActivity` операцию не убил - она продолжалась ещё 27 секунд.

### Что реально произошло

Полное окно вокруг гибели операции:

```
20:31:48.663  ConnectionThrottle: Reset state for cloud://GOOGLE_DRIVE/0B2Wse..
20:31:48.664  BrowseShutdownCoordinator: cancelled background thumbnail loading for cloud://GOOGLE_DRIVE/0B2Wse..
20:31:49.142  BrowseViewModel.onCleared: START - resourceId=36, fileCount=148
20:31:49.143  ConnectionThrottle: Reset state for cloud://GOOGLE_DRIVE/0B2Wse..
20:31:49.143  BrowseShutdownCoordinator.onShutdown: cancelled ops for cloud://GOOGLE_DRIVE/0B2Wse..
20:31:49.144  UnifiedFileCache: Cleared cache - deleted 0 files
20:31:49.146  onDestroy: BrowseActivity
20:32:01.549  UnifiedFileCache: Cleared cache - deleted 0 files   (новый BrowseActivity, resourceId=6)
20:32:01.362  E  [1202b180|file-operation] EXCEPTION in executeInternal
```

Два кандидата на убийцу, оба - утечка UI-жизненного цикла в общее состояние, которым пользуется воркер:

1. `BrowseShutdownCoordinator.onShutdown` -> `ConnectionThrottleManager.cancelAllForResource(resourceKey)`. Метод корутин не отменяет - он обнуляет счётчик `activeTasks` и удаляет запись из `protocolStates` и `semaphores` (`ConnectionThrottleManager.kt:580-592`). Живая загрузка воркера при этом держит пермит уже удалённого семафора, а отпускает его в заново созданный - учёт параллелизма ломается для ресурса, в который прямо сейчас идёт перенос.
2. `BrowseShutdownCoordinator.launchPostShutdownCleanup` -> `unifiedCache.clearAll()`, срабатывающий и на закрытии старого экрана, и на открытии нового. Воркер переносит файлы через тот же кеш.

Ровно между вторым `clearAll()` (20:32:01.549) и открытием нового экрана падает `JobCancellationException` в `executeInternal`. `cancelAllForResource` сам по себе `JobCancellationException` бросить не может, поэтому отмена приходит из третьего места.

Общий знаменатель: закрытие и открытие экрана Browse дёргает разделяемые подсистемы (пул соединений, единый файловый кеш), не спрашивая, идёт ли по ним прямо сейчас фоновый перенос. Воркер живёт, а из-под него выдёргивают ресурсы.

1. Закрытие, пересоздание или повторное открытие Browse не должны разрушать зависимости активной фоновой массовой операции.
2. Пользователь может отменить активную передачу только явным действием Cancel в диалоге прогресса.
3. При отсутствии активной передачи очистка ресурсов Browse сохраняет прежнее поведение.
4. Регрессия покрыта проверками активного и простоявшего состояния, а также реальным переносом на медленное сетевое назначение.

**Non-goals:**

- Не менять точки запуска операций вне Browse; они относятся к S1224 и отдельным тикетам.
- Не менять протокол, формат WorkManager-заявки или семантику явной отмены.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

- Исправление должно быть минимальным и не менять видимый UI.
- Обычная очистка после простоя должна продолжать освобождать ресурсы.

### 3.2 Жёсткие ограничения

- **Flavor:** standard, lite, photos, legacy, noLegal; общий код `src/main`.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** не выполнять синхронный WorkManager-запрос на main thread.
- **Совместимость данных:** миграция данных не требуется.
- **Локализация:** без новых строк.
- **Доступность:** UI не меняется.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1227 (browse-background-transfer-indicator, BlockNeedUserTest) - индикатор уже отправленной в фон передачи, соседняя, но другая проблема; S1224 (player-copy-move-background, Draft); S1363 (проглатывание CancellationException, из того же лога).

---

## 4. Контекст текущей архитектуры

Массовая операция Browse исполняется в process-level WorkManager worker, а UI только отображает его прогресс и предоставляет явную отмену. Закрытие Browse одновременно запускает очистку сетевого ограничения и общего файлового кеша, хотя эти объекты разделены с фоновым worker.

## 5. Предлагаемый подход

Перед очисткой зависимостей Browse асинхронно определить наличие активной интерактивной передачи. При активной работе пропустить cleanup, который сбрасывает сетевое состояние и удаляет общий кеш; при простое сохранить cleanup. Переиспользовать существенный coordinator как единственного владельца знания о состоянии WorkManager.

### 5.1 Основные столпы / модули

- Охрана shutdown cleanup состоянием фоновой передачи.
- Охрана init cleanup тем же состоянием.
- Регрессионные тесты для обоих состояний.

### 5.2 Потоки данных и событий

Browse lifecycle → асинхронная проверка transfer state → либо cleanup shared dependencies, либо пропуск cleanup → WorkManager worker продолжает работу.

### 5.3 Точки расширяемости

Один predicate transfer coordinator остаётся источником истины для будущих владельцев Browse lifecycle.

## 6. Открытые вопросы / Research items

1. **Владелец отмены и небезопасные cleanup paths**
   - **Вопрос:** связана ли отмена с Browse lifecycle и какие cleanup paths затрагивают worker?
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S1362_bugfix-bulk-file-operation-dies-with-browse-screen/research/01__browse-shutdown-worker-cancellation.md`

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Cleanup пропущен дольше нужного | Низкая | временное занятие кеша/состояния | выполнять cleanup при idle и на следующем безопасном lifecycle edge |
| Проверка блокирует UI | Низкая | jank при закрытии | использовать suspend/IO путь coordinator |

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

## 9. Архитектурные решения (ADR)

**ADR-1: WorkManager transfer state guards shared Browse cleanup**

- **Решение:** пропускать cleanup общего throttle/cache при активной передаче.
- **Альтернативы:** отменять worker при закрытии Browse; создавать отдельный кеш для worker.
- **Почему:** worker является process-level задачей, а явная отмена уже существует в UI.

## 10. Связи с другими спеками

- S1227: отображение фоновой передачи, не блокирует это исправление.
- S1224: другие launch paths, не входят в scope.

## 11. Критерии готовности (strategic-level)

1. Активный перенос продолжает работу после двух Back и повторного открытия Browse.
2. Явный Cancel продолжает отменять текущий transfer worker.
3. При отсутствии активной передачи Browse выполняет прежний cleanup.
4. Нет `JobCancellationException` из `executeInternal` в сценарии активного переноса и закрытия Browse.

---
