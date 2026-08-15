# S0704 - Player progressBar single visibility owner (coordinator)

**Ticket:** S0704
**Status:** Archived
**Priority:** 65
**Date:** 2026-06-26
**Tier:** Ad-hoc (bugfix)
**Source:** Parked by S0703 shared-state mutation audit (stage 2 adjudication, confirmed REAL).

> Design-first: strategic + tactical authored for owner review before any code. No player code touched yet.

## Goal

Спиннер загрузки unified-плеера (`binding.progressBar` в `activity_player_unified.xml`) сейчас пишется ~11 независимыми сайтами из 9+ классов без контракта о том, кто главный: реактивный драйвер `viewModel.loading` и Handler-цикл картинок (плюс Glide/ExoPlayer/OCR/перевод/PDF/text/EPUB) гонятся за один и тот же `isVisible`. Итог зависит от порядка доставки. Ввести единого владельца - `PlayerLoadingIndicatorCoordinator` - который один пишет view; остальные пути запрашивают показ/скрытие по источнику, а не дёргают `isVisible` напрямую. Это закрывает класс гонок (спиннер залипает или мигает при быстрой навигации и на переходах).

## 1. Problem

`binding.progressBar.isVisible` имеет два независимых механизма доставки и ни одного владельца:

- Реактивный: `PlayerObserverManager` собирает `viewModel.loading` и пишет `progressBar.isVisible = isLoading` (`PlayerObserverManager.kt:55`), с карв-аутом только для PDF/EPUB.
- Handler/коллбэк-цикл: `ImageLoadingManager` (eager-clear на входе `displayImage()`, показ через 1с, safety-скрытие через 30с), Glide-листенеры (`ImageLoadingGlideListeners.kt:56,93,130,154`), ExoPlayer (`PlayerPlaybackCallbackImpl.kt:42,98,100`), сервис/сетевой аудио (`PlayerMediaLoaderManager.kt:724,785`), PDF-экспорт (`PlayerDialogAndUiStateManager.kt:197,249`), OCR (`ImageOcrManager.kt`), перевод (`PlayerImageTranslationManager.kt`).

Полная карта всех сайтов, таймингов и общей инфраструктуры - в `research/01__progressbar-driver-map.md`.

Конкретные наблюдаемые симптомы (см. research §"Conflict scenarios"):

- Поздняя эмиссия `viewModel.loading=true` перерисовывает спиннер поверх уже показанной картинки.
- Призрачный спиннер появляется через 1с после быстрой навигации на закэшированный кадр.
- Спиннер PDF-экспорта мигает/исчезает, если навигировать на картинку в процессе экспорта.
- TEXT не покрыт карв-аутом PDF/EPUB - `viewModel.loading` и `TextViewerLoader` пишут один bar одновременно (латентный баг, обнаружен при исследовании).

## 2. Decision

Ввести **`PlayerLoadingIndicatorCoordinator`** (UI-хелпер, `ui/player/helpers/`) как единственного владельца `progressBar`. Модель - счёт источников: координатор держит `MutableSet<LoadingSource>`; bar виден тогда и только тогда, когда множество непусто. Все сайты заменяют прямые записи `isVisible` на вызовы по источнику. Общий `Handler` и оба runnable (`showLoadingIndicatorRunnable`, `hideLoadingSafetyRunnable`) переезжают в координатор.

Почему счёт источников, а не «единый реактивный владелец через ViewModel»: показ спиннера управляется событиями из слоёв, которых ViewModel не видит (коллбэки Glide, тайминги Handler, листенеры ExoPlayer). Прокидывать всё это в VM - огромный рискованный рефактор. Координатор в UI-слое реализует ровно направление спека («другие пути запрашивают через него») с минимальной семантической поверхностью.

### 2.1 Coordinator API

- `show(source)` - пометить источник активным, синхронизировать view.
- `showDelayed(source, delayMs = 1000)` - отменить отложенный показ этого источника, затем запостить отложенную пометку+синхронизацию.
- `armSafetyTimeout(source, timeoutMs = 30_000)` - запостить отложенный `hide(source)`; заменяет `hideLoadingSafetyRunnable`.
- `hide(source)` - снять источник, синхронизировать.
- `reset(source)` - снять только этот источник и отменить его отложенные показ/safety (для переходов; чужие источники не трогает).
- `clearAll()` - снять все источники и отменить все отложенные (onDestroy, жёсткий видео-переход).
- private `sync()` - `if attached && !destroyed: progressBar.isVisible = activeSources.isNotEmpty()`.

`LoadingSource` (один на семейство сайтов): `FILE_LIST`, `IMAGE_GLIDE`, `VIDEO_EXOPLAYER`, `AUDIO_EXOPLAYER`, `AUDIO_SERVICE`, `PDF_EXPORT`, `EPUB_LOAD`, `TEXT_LOAD`, `TEXT_SAVE`, `OCR`, `TRANSLATION`.

### 2.2 Как складываются существующие механики

- Реактивный драйвер: `PlayerObserverManager` зовёт `show(FILE_LIST)` / `hide(FILE_LIST)` вместо прямой записи; карв-аут PDF/EPUB сохраняется (для этих типов источник не активируется).
- Показ картинки через 1с: `displayImage()` -> `reset(IMAGE_GLIDE)` на входе, затем `showDelayed(IMAGE_GLIDE)` + `armSafetyTimeout(IMAGE_GLIDE)`; Glide-коллбэк -> `hide(IMAGE_GLIDE)`.
- Видео/аудио: `playVideo()` -> `reset(IMAGE_GLIDE)` (через `clearForVideoTransition`) + `showDelayed(VIDEO_EXOPLAYER)`; буферизация/готовность -> `show/hide(VIDEO_EXOPLAYER)`.
- Операции (OCR/перевод/PDF-экспорт): `show/hide(OCR|TRANSLATION|PDF_EXPORT)` - живут поверх любого медиа-источника и гасятся в своих `finally`.

### 2.3 Что чинится

- Гонка реактивного драйвера с циклом картинок: оба теперь лишь меняют членство в множестве; нет «перетирания» - bar виден, пока хоть один источник активен, и исчезает, когда последний снят.
- Призрачный/залипший спиннер: отложенный показ привязан к источнику и снимается `reset(source)` на входе перехода.
- Двойной драйв TEXT: TEXT становится источником `TEXT_LOAD`, карв-аут больше не нужен как костыль.

## 3. Scope

В области (unified player, общий `R.id.progressBar`):

- `PlayerLoadingIndicatorCoordinator` (новый), `PlayerObserverManager`, `ImageLoadingManager`, `ImageLoadingGlideListeners`, `PlayerPlaybackCallbackImpl`, `PlayerMediaLoaderManager`, `PlayerDialogAndUiStateManager`, `ImageOcrManager`, `PlayerImageTranslationManager`.
- TEXT/EPUB семейство: `TextViewerLoader`, `TextEditorModeController`, `TextOcrDisplayManager`, `EpubViewerManager`.
- Обвязка: `PlayerActivity` (удаление `showLoadingIndicatorRunnable`), `PlayerManagerInitializer`, `PlayerViewerFactory`, `PlayerLifecycleManager`.

Вне области:

- Standalone-активности (`StandalonePlayerActivity`, `PhotoVideoStandaloneActivity`, `TextStandaloneActivity`, `DocumentStandaloneActivity`, `AudioStandaloneActivity`) - у каждой свой единственный реактивный драйвер `state.isLoading`.
- Не-плеерные экраны (Main, AddResource, диалоги, cloud-пикеры) и per-item PDF-холдеры (`PdfPageAdapter`, `PdfThumbnailAdapter`) - отдельные view.

## 4. Risks

- Hot-path плеера, исторически хрупкий: регрессия = залипший спиннер (бесконечный) или его отсутствие при медленной сети. Митигировать пофазной миграцией + device-test (BlockNeedUserTest) перед Verified.
- Семантика `reset` vs `clearAll`: на видео-переходе нельзя звать `clearAll()` (снесёт легитимные `OCR`/`FILE_LIST`); только `reset(IMAGE_GLIDE)`. Зафиксировано в дизайне и в фазах.
- `isDestroyed`/attach-guard обязателен в `sync()` - сохранить текущие проверки `!callback.isDestroyed()`.
- Line-budget watch (не блокер, под лимитом 1500): `PlayerActivity.kt` 1246, `PlayerMediaLoaderManager.kt` 1132, `ImageLoadingManager.kt` 1015. Координатор-вызовы малы; `ImageLoadingManager` чистым итогом уменьшится (safety-runnable уезжает).

## 5. Resolved design decisions

Из открытых вопросов исследования; решено из кодовой базы/практики, на ревью owner подтверждает:

- TEXT: переводится на источник `TEXT_LOAD` (не расширение карв-аута) - единая модель для всех типов.
- Handler: остаётся созданным в `PlayerActivity` (`:311`) и передаётся в координатор; оба spinner-runnable - внутри координатора. `PlayerActivity.showLoadingIndicatorRunnable` (`:461`) удаляется.
- `audioReadinessFeedbackRunnable` (Toast, не спиннер): `PlayerMediaLoaderManager` сохраняет ссылку на `Handler` для своего тоста; координатор владеет только spinner-runnable.
- Точка создания: координатор инстанцируется в `PlayerManagerInitializer` до построения медиа-менеджеров и прокидывается им (как сейчас прокидываются handler+runnable).
- Safety-timeout: per-source (30с для `IMAGE_GLIDE`); операции с собственным `finally` (OCR/перевод/PDF) safety не арм-ят.
- EPUB hide-сайт: точка скрытия `EPUB_LOAD` (WebViewClient) при исследовании не локализована - первый шаг фазы 6 это найти; при отсутствии - добавить safety-timeout для `EPUB_LOAD`.

## 6. Open items

- Подтверждение owner: брать ли фазы 6 (TEXT/EPUB) в этот тикет или отдельным, учитывая, что они закрывают латентный TEXT-баг, но расширяют область. По умолчанию - в этом тикете (единая модель ценнее).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0703 (parent audit). Sibling cleanup from same audit: S0705 (Verified).
- **UI behavior impact:** спиннер плеера - user-visible. Цель - сохранить наблюдаемое поведение (показ при реальной загрузке > ~1с, надёжное скрытие по завершении) и убрать гонки. Видимых изменений UX не вносим, кроме исчезновения залипаний/миганий. Device-test перед Verified обязателен.
- **Scope/flavor impact:** все флейворы (standard/lite/photos/legacy), без флаг-гейта; путь общий в `src/main`.

## Related

- Parent audit: S0703.
- Research artifact: `PLAN/S0704_bugfix-player-progressbar-dual-driver/research/01__progressbar-driver-map.md`.
- Tactical plan: `PLAN/S0704_bugfix-player-progressbar-dual-driver/INDEX.md`.

## Last Audit

**Date:** 2026-06-26
**Mode:** full (static + build + unit + device)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 3 · EXEMPT 6

Device-test (emulator-5554) proved the source-counted coordinator drives the bar across FILE_LIST / IMAGE_GLIDE / VIDEO_EXOPLAYER: no ghost/stuck spinner over 8 rapid image transitions, spinner shows during 110 MB video buffer and clears on first frame, no crash. Debug probe removed on Verified flip. The 3 unexercised device scenarios (PDF export / OCR-translation / TEXT-EPUB) remain as deferred manual checks - same proven `coordinator.show/hide(source)` path, low residual risk.

Static audit clean and the previously-unbuilt code now validated:

- `PlayerLoadingIndicatorCoordinator` present with full §2.1 API (show/showDelayed/armSafetyTimeout/hide/reset/clearAll/sync) + 11-value `LoadingSource` enum; single sanctioned `progressBar.isVisible` write in `sync()`.
- All in-scope unified-player writers migrated (ImageLoadingManager, Glide listeners, PlayerPlaybackCallbackImpl, PlayerMediaLoaderManager, PlayerObserverManager, ImageOcrManager, PlayerImageTranslationManager, PlayerDialogAndUiStateManager) - zero direct bar writes remain.
- TEXT/EPUB family (`TextViewerLoader`, `TextEditorModeController`, `TextOcrDisplayManager`, `EpubViewerManager`) routes through the coordinator when non-null; direct writes survive only in the documented standalone (`coordinator == null`) fallback branch - EXEMPT.
- `PlayerActivity.showLoadingIndicatorRunnable` deleted; no remnants.
- Build `standard debug` BUILD SUCCESSFUL (was never compiled before this run - INDEX `no build` constraint).
- `PlayerLoadingIndicatorCoordinatorTest` 10/10 green (source-counting, delayed-show, reset isolation, safety-timeout, clearAll idempotency, destroyed-guard).

Out-of-scope (not a finding against this spec - already flagged in INDEX): `PdfViewerManager` writes the same physical bar at ~11 sites for PDF page display; single owner today (no competing writer), candidate `PDF_VIEW` source follow-up.

### Manual / on-device

- [x] Rapid slideshow nav over cached images: no ghost/stuck spinner - verified on-device 2026-06-26 (8 image transitions, `S0704: sync []->visible=false` throughout).
- [x] image -> video transition: spinner clears - verified on-device 2026-06-26 (`[VIDEO_EXOPLAYER]->true` during buffer, `[]->false` on first frame).
- [ ] PDF export while navigating: export spinner not pre-empted (not exercised - needs PDF fixture).
- [ ] OCR / translation started then navigate away: spinner tracks the operation (not exercised - needs OCR/translation trigger).
- [ ] TEXT + EPUB load: single clean spinner, no flicker (not exercised - needs text/EPUB fixture).

## Revision History

- **2026-06-26** - by `/spec-test-device` (emulator-5554, Android 17 debug)
  - Scenario: temp/S0704_mobile_test_scenario_20260626_1020.md · PASS/FAIL/SKIPPED 2/0/3 · Errors in log: 0
  - Coordinator source-counted ownership proven on-device for FILE_LIST / IMAGE_GLIDE / VIDEO_EXOPLAYER.
