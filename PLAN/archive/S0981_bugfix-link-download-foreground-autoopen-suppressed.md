# Спецификация (compact bugfix): S0981 - Foreground авто-открытие скачанного файла подавлено (мёртвая presenter-ветка)

**Ticket:** S0981
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-10

<!-- auto-approved by /spec-all - 2026-07-11 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-10

**Захвачено во время:** разбор S0980 (bugfix-open-downloaded-files-instagram), device-drain на emulator-5554.

**Текст (находка, эвиденс по живому дереву):**

Настройка `linkAutoDownloadOpenInPlayer` ("авто-открывать скачанные файлы в плеере") в foreground НЕ срабатывает: при завершении загрузки, когда приложение на переднем плане и настройка ВКЛ, файл должен авто-открыться, но не открывается. Открытие работает только по ручному тапу нотификации (канал S0257).

Причина (проверено):
- Единственный продюсер `ShareDownloadResultBus` - воркер, и он ВСЕГДА публикует `notificationShown = true` (`LinkDownloadWorker.kt:139`; других `resultBus.publish` в дереве нет).
- `MainActivity.kt:299`: `if (pending.notificationShown && (isSuccess || isAuthGated)) return@collectOnLifecycle` - для успешных результатов (`Saved` / `FellBackToDownloads` / `BatchCompleted`) `present()` не вызывается вовсе.
- Следствие: ветка авто-открытия в `LinkAutoDownloadResultPresenter.present()` (`:62-90`) и обе пробы `Timber.d("S0980:` (`:63`, `:78`) - недостижимый мёртвый код. Именно поэтому device-тест S0980 дал "0 проб возможно".

Корень: подавление S0202 в `MainActivity` задумано, чтобы убрать ДУБЛИРУЮЩИЙ тост, но оно рубит `present()` целиком, вместе с авто-открытием. S0980 писался в расчёте, что `present()` выполняется, не учтя это подавление.

Связанное: S0980 (writer-фикс, чинит канал тапа), S0257 (content-intent по тапу = ручной override), S0202 (подавление дублей).

**Вложения:** нет.

---

## 1. Проблема / симптом

Foreground: скачивание завершено + `linkAutoDownloadOpenInPlayer` ВКЛ -> ожидается авто-открытие в плеере -> фактически ничего (только нотификация; открытие лишь по ручному тапу). Ветка авто-открытия и S0980-пробы недостижимы из-за подавления `MainActivity.kt:299`.

---

## 2. Корневая причина

Подавление S0202 в `MainActivity` collectOnLifecycle-коллекторе для `success`-результатов делает ранний `return@collectOnLifecycle` ДО вызова `present()`. Задумано оно было только против дублирующего тоста (воркер уже показал нотификацию результата), но фактически рубит весь `present()`, включая `launchPlayer()` авто-открытия. Так как воркер всегда публикует `notificationShown = true`, ветка авто-открытия для `Saved` / `FellBackToDownloads` / `BatchCompleted` недостижима навсегда.

Дополнительно (латентный дефект, всплывает при варианте A): `ShareDownloadResultBus` имеет `replay = 1`, а `clearReplayCache()` не вызывается ни одним consumer'ом. Пока `present()` для success подавлялся, это было безвредно (коллектор просто рано выходил). Как только авто-открытие оживает, тот же терминальный результат будет переигрываться из replay-кэша при каждом повторном входе в `MainActivity` (возврат из фона, recreation) -> повторное «выдёргивание» пользователя в плеер поверх текущего экрана. Consume-once обязателен.

---

## 3. Исправление

**Owner decision (разрешено из контракта настройки, вариант A).** Развилка §3.3 не требует owner-вопроса: контракт самой настройки авторитетен. `link_autodownload_open_in_player_label` = "Open downloaded file in player", summary = *"After a successful download the file opens **automatically** in the built-in player. If disabled, the result is shown as a toast."*, дефолт `linkAutoDownloadOpenInPlayer = true`. Настройка прямо обещает авто-открытие; вариант B (только по тапу) сделал бы отгруженную UI-копию ложной и потребовал бы удаления рабочей настройки. Берём **вариант A**: в foreground при ВКЛ настройке успешная загрузка авто-открывается; подавляется только дублирующий тост.

Изменения:

1. `LinkAutoDownloadResultPresenter.present()` - добавить параметр `notificationShown: Boolean = false`. В трёх success-ветках (`Saved`, `FellBackToDownloads`, `BatchCompleted` при `failureCount == 0`) `launchPlayer()` вызывается как раньше (при `openInPlayer && uri != null`), а тост-fallback показывается только при `!notificationShown` (иначе дубль с нотификацией воркера). `showBatchSummary` (partial batch, `failureCount > 0`) остаётся безусловным - это диалог с деталями отказов, не дублирующий тост.
2. `MainActivity` collector - убрать `isSuccess` из условия подавления (оставить подавление только `SocialPreviewOnly` = `isAuthGated`, чей in-Activity диалог избыточен рядом с нотификационным "Sign in"). Передавать `notificationShown = pending.notificationShown` в `present()`.
3. `MainActivity` collector - вызывать `shareResultBus.clearReplayCache()` после обработки на ОБОИХ путях (подавлённый auth-gated и обычный present), чтобы `replay = 1` не переигрывал тот же результат при повторном входе/recreation (consume-once).

Взаимодействие: S0980 (BlockNeedUserTest) ждал этого фикса - его presenter-ветка и пробы `S0980:` (`:63`, `:78`) становятся достижимы в foreground. Статус S0980 не трогаем (его device-тест отдельно). S0257 (ручной override по тапу) не затронут - тап-канал идёт через content-intent, отдельно от presenter'а.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0980 (writer-фикс open-in-player), S0257 (notification content-intent), S0202 (share-result suppression)
- **Owner decision:** РАЗРЕШЕНО из контракта настройки -> вариант A (foreground авто-открытие при ВКЛ `linkAutoDownloadOpenInPlayer`). Owner может ветировать в пользу B; тогда переоткрыть тикет.
- **Sensitive scope (UI):** меняется момент авто-открытия плеера в foreground. Видимая строка: `link_autodownload_open_in_player_summary` уже описывает это поведение, изменений строк не требуется.

---

## 4. Проверка

Device-тест (BlockNeedUserTest):
1. Настройки -> включить авто-загрузку ссылок + `linkAutoDownloadOpenInPlayer` ВКЛ; выбрать resource-назначение.
2. Приложение на переднем плане (MainActivity). Расшарить в приложение медиа-ссылку (напр. Instagram `/p/`), вернуться в приложение.
3. Ожидается: по завершении загрузки файл авто-открывается в плеере; проба `S0981: fg-present notif=true kind=Saved` (или `FellBackToDownloads`/`BatchCompleted`) в logcat.
4. Свернуть приложение и вернуться в MainActivity ещё раз: плеер НЕ должен переоткрыться повторно (consume-once, replay-кэш очищен).
5. При настройке ВЫКЛ: авто-открытия нет, показывается один тост-результат без дубля.

Статический контроль:
- `present()` вызывается для success (grep: `isSuccess` убран из подавления в `MainActivity`).
- `clearReplayCache()` вызывается в collector'е.
- Компиляция standard debug PASS.

---

## 5. Device-test finding (раунд 2) - foreground-snap + дефолт настройки

**Захвачено:** 2026-07-12, во время device-теста раунда 1 (galochka работает, `present()` достижим).

Owner-репорт: плеер действительно авто-открывается (проба `S0981: fg-present` в logcat подтверждена), но новая Activity не visually "выпрыгивает" поверх текущего экрана - открывается где-то в фоне/стеке задач, пользователь её не видит без ручного переключения.

Root cause (проверено): `LinkAutoDownloadResultPresenter.launchPlayer()` вызывал `host.startActivity()` без всякой гарантии foreground-snap, из коллектора `collectOnLifecycle(shareResultBus.pending)` (`MainActivity.kt:301`), который активируется на `Lifecycle.State.STARTED` (не `RESUMED`). `replay = 1` означает: если загрузка завершилась, пока приложение было в фоне, а пользователь затем возвращается в `MainActivity`, кэшированный `Pending` переигрывается ровно в момент `onStart` - до того как Activity стала по-настоящему интерактивной (`RESUMED`). Запуск новой Activity в этот момент может оставить её в back stack без видимого перехода. Дополнительно - канал по тапу нотификации (`LinkDownloadWorker`, `S0257`) уже использует `FLAG_ACTIVITY_NEW_TASK` для того же таргета; in-process путь этого флага не имел (асимметрия между двумя путями к одному и тому же экрану).

Исправление (`LinkAutoDownloadResultPresenter.launchPlayer()`):
1. `launchPlayer()` стал `suspend`; сам `startActivity()` обёрнут в `host.lifecycle.withResumed { }` (androidx.lifecycle 2.7.0) - гарантированно ждёт настоящего `RESUMED` перед запуском. Отмена коллектора (`repeatOnLifecycle` падает ниже `STARTED`) естественно отменяет и эту suspend-точку - утечки/зависания нет.
2. Intent теперь несёт `FLAG_ACTIVITY_NEW_TASK` вдобавок к `FLAG_GRANT_READ_URI_PERMISSION` - симметрично с notification-tap путём. Диспетчер (`StandalonePlayerDispatcherActivity`) и специализированные `*StandaloneActivity` хосты используют тот же task affinity, что и `MainActivity` (кроме `PhotoVideoStandaloneActivity`, чей `taskAffinity="${applicationId}.player"` инертен без `NEW_TASK` где-либо в цепочке до этого фикса) - флаг здесь гарантирует "поднять существующую задачу наверх", а не создаёт отдельную задачу.

Отдельно, owner-решение: `linkAutoDownloadOpenInPlayer` дефолт меняется с `true` на `false` (было решено как fail-safe: пока auto-open UX не отполирован, лучше явный opt-in, чем сюрприз-запуск плеера). Изменения:
1. `AppSettings.kt` / `LinkSettingsStore.kt` - код-дефолт `false`.
2. Новая run-once миграция `data/migration/S0981OpenInPlayerDefaultOff.kt` (паттерн `S0386UpgradeReconciliation`) форсит уже установленным приложениям `linkAutoDownloadOpenInPlayer = false` один раз - без этого существующие инсталляции уже имеют `true` явно сохранённым в DataStore (полный snapshot пишется при любом save настроек), и смена кодового дефолта их не затронула бы. Как и в S0386-прецеденте, миграция не различает "пользователь никогда не трогал" от "пользователь осознанно оставил ВКЛ" - сбрасывает безусловно (owner принял этот trade-off явно).
3. Подключена в `FastMediaSorterApp.onCreate()` за firstFrameSignal, идемпотентна (sentinel `s0981_migration`).

Проба: `Timber.d("S0981: default-migration check wasOn=...")` в `S0981OpenInPlayerDefaultOff.runIfNeeded()`.

Device-тест (раунд 2, добавляется к раунду 1 из §4):
1. Свежая установка (или после сброса данных приложения): открыть Настройки -> `linkAutoDownloadOpenInPlayer` должен быть ВЫКЛ по умолчанию.
2. На инсталляции, где ранее галочка стояла ВКЛ (до этого фикса): после первого запуска новой сборки - галочка должна стать ВЫКЛ автоматически (проба `S0981: default-migration` в logcat, `wasOn=true` -> лог "forced OFF").
3. Включить галочку вручную, повторить сценарий раунда 1 (foreground share -> download complete): проверить, что плеер визуально появляется поверх текущего экрана СРАЗУ, без необходимости вручную переключаться через recents.
4. Повторить с намеренной задержкой - свернуть приложение в момент завершения загрузки, вернуться в MainActivity сразу после: плеер должен появиться поверх экрана (не потеряться в фоне), либо consume-once не даст повторного срабатывания при следующем возврате.
