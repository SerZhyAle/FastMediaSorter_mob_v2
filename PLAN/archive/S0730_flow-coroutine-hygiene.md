# Спецификация (fix): S0730 - Гигиена Flow и структуры корутин (S0716)

**Ticket:** S0730
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-26
**Tier:** 2 - Bugfix
**Roadmap entry:** Ad-hoc - находки аудита S0716 (Layer 2, P2/P3 efficiency)
**Umbrella:** S0714

> **Scope:** Три правки лишней работы корутин/Flow (не корректность, а эффективность). Найдено статически (S0716).

---

## 0. Источник

Три находки аудита S0716 (`PLAN/S0716_concurrency-correctness-audit/AUDIT_FINDINGS.md`): гигиена Flow + структура корутин. Лишняя работа, не баги корректности.

## 1. Находки и правки

1. **P2 - `ui/browse/managers/BrowseObserverManager.kt:61`.** Четыре независимых `collectOnLifecycle(getSettings())` подписки; `getSettings()` - холодный, без `stateIn`/`shareIn`/`flowOn`, пересобирает ~150-полевой `AppSettings` (incl. side-effect записи в `glidePrefs`) на Main по 4 раза на каждую запись настроек. Sibling-паттерн (PlayerViewModel:669, StreamsViewModel:75) - один `stateIn`-обёрнутый settings-StateFlow. **Fix:** один разделяемый settings-StateFlow на BrowseViewModel (`getSettings().flowOn(Default).stateIn(viewModelScope, Eagerly, ..)`), 4 обзёрвера мапят поля через `distinctUntilChanged { it.field }`.
2. **P3 - `domain/usecase/ResourceEditorUseCase.kt:62` `scope`.** `CoroutineScope(SupervisorJob()+ioDispatcher)` без `@Scope` (fresh per VM-инъекция), никогда не отменяется; `launchPostSaveVerification` оставляет in-flight сетевую корутину после смерти владельца. Ограничено (короткоживущая, StateFlow без коллекторов). **Fix:** инжектировать lifecycle-scoped scope или гонять верификацию на `viewModelScope`.
3. **P3 - `data/transfer/strategy/CloudOperationStrategy.kt:289`.** `progressScope.launch { onProgress(..) }` raw на каждый 64KB-тик (GoogleDriveRestClient шлёт без троттлинга) → тысячи корутин на большой передаче (~16k на 1GB), флуд прогресс-бара/jank. Sibling `CloudFileOperationHandler` использует троттл `adaptCloudProgress` (100KB + AtomicLong CAS). **Fix:** маршрутизировать оба progress-колбэка через существующий `adaptCloudProgress(..)`.

## 2. Критерии приёмки

- [x] BrowseViewModel читает настройки через один разделяемый StateFlow; пересборка AppSettings не множится по обзёрверам.
- [x] Верификация ResourceEditor отменяется при закрытии экрана.
- [x] Прогресс облачной передачи троттлится; нет тысяч корутин на тик.
- [x] `.\a.ps1 fc` зелёный (compile зелёный после kapt-recovery; detekt зелёный после ре-фриза baseline).

## 3. Связанные тикеты

- S0716 (аудит-источник), S0714 (зонтик).

## 4. Реализация

- `BrowseViewModel`: добавлен `val settings: StateFlow<AppSettings> = getSettings().flowOn(Default).stateIn(viewModelScope, Eagerly, AppSettings())`.
- `BrowseObserverManager`: четыре обзёрвера читают `viewModel.settings`; однополёвые - через `map{}.distinctUntilChanged()`; `observeSettings` (мультиполе) - на весь StateFlow (StateFlow дедупит по equals). Убран неиспользуемый параметр `settingsRepository` (и из `BrowseManagerInitializer`).
- `ResourceEditorUseCase`: удалён никогда не отменяемый `CoroutineScope(SupervisorJob()+io)`; `save(formData, verificationScope)` запускает пост-сейв-верификацию через `verificationScope.launch(ioDispatcher)`; вызывающий `ResourceFormViewModel` передаёт `viewModelScope`.
- `CloudOperationStrategy`: оба progress-колбэка (download + upload) идут через `adaptCloudProgress(progressCallback, progressScope)` (100KB + AtomicLong CAS) вместо raw `launch` на тик.
- Detekt baseline (`config/detekt/baseline-app_v2.xml`, `baseline-wear.xml`) ре-фрожен: правки сдвинули строки/сигнатуры существующих baselined-находок (ImportOrdering, LongParameterList ctor).
