# Спецификация (fix): S0735 - Единый sealed UiState вместо split state/loading/error

**Ticket:** S0735
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-26
**Tier:** 2 - Bugfix
**Roadmap entry:** Ad-hoc - находки аудита S0718 (Layer 1, P2/P3 loose-state)
**Umbrella:** S0714

> **Scope:** Архитектурный рефактор моделирования состояния экрана. Найдено статически (S0718). Шире остальных - затрагивает BaseViewModel и подписчиков.

---

## 0. Источник

Две находки аудита S0718 (`PLAN/S0718_code-readability-audit/AUDIT_FINDINGS.md`, #4 + #8) с общим корнем: `BaseViewModel` разбивает состояние на три независимых потока.

## 1. Проблема

`BaseViewModel` (`core/ui/BaseViewModel.kt:33,36`) экспонирует три независимых observable - подклассовый `state`, `loading`, `error` - без объединяющего sealed `UiState`. Следствия:

- **Illegal combos представимы:** `setError`/`setLoading`/`clearError` независимы → `loading=true` при `error!=null`, empty-while-loading достижимы.
- **Дублированная ad-hoc реконсиляция:** `MainActivity` (#8, :950/:990) пересобирает content/empty/error в ДВУХ коллекторах, каждый снимает `.value` другого потока; третий коллектор (:970) гонит progressBar из `loading` без учёта error/empty. Конкретный симптом: на холодном старте empty-плейсхолдер и спиннер рендерятся вместе.

## 2. Решение

- Ввести `sealed UiState<Content> { Loading; Content(data); Empty; Error(msg) }` в `BaseViewModel`.
- Каждый экран рендерит один observable; illegal combos непредставимы.
- В `MainActivity` заменить три коллектора одним на `combine(state, loading, error)` → один `ListUiState`; убрать дублированный блок видимости.

Объём: затрагивает `BaseViewModel` и всех подписчиков - делать аккуратно, по экранам; начать с `MainActivity` (где симптом виден), затем мигрировать остальные.

## 3. Критерии приёмки

- [x] Состояние экрана - один sealed `UiState`; нет независимых loading/error, дающих illegal combos.
- [x] `MainActivity` рендерит из одного производного состояния; дублированный блок видимости удалён; нет одновременного empty+спиннера на старте.
- [x] Подписчики мигрированы (или явно отложены отдельным шагом); сборки зелёные; поведение экранов сохранено.

## 4. Связанные тикеты

- S0718 (аудит-источник), S0714 (зонтик).

## 5. Состояние реализации (2026-06-28)

Реализация уже присутствует в рабочем дереве (`compileStandardDebugKotlin` UP-TO-DATE, `.\a.ps1 fk` зелёный, кода не потребовалось).

Что сделано:

- `UiState<T>` (Loading/Empty/Error/Content) + `createUiState { isEmpty }` в `BaseViewModel` - READ-модель производится из `combine(state, loading, error)`. `setLoading`/`setError` остаются WRITE-API; illegal combos непредставимы в рендеримом `UiState`.
- `MainActivity` рендерит видимость progressBar/list/empty/error из одного коллектора `resourceListUiState` (`MainActivity.kt:969`); взаимоисключающий `when`, дублированный блок убран; коллектор `state` (`:929`) делает только привязку данных адаптера, коллектор `:987` - отдельный navigation-overlay (S0708).
- `BrowseActivity` мигрирован через `fileListUiState` (`BrowseObserverManager.kt:138`).

Явно отложено (не список-экраны - empty/error-плейсхолдеров нет, используется только `loading` как progress-overlay; миграция на sealed `UiState` была бы неверной):

- `AddResourceViewModel` / `AddResourceActivity:381` - progress во время scan/add.
- `PlayerViewModel` / `PlayerObserverManager:53` - buffering-spinner.
- `WelcomeViewModel` - loading/error не используются.

Остаётся: визуальная проверка холодного старта (нет empty+спиннера) на устройстве - структурно гарантировано взаимоисключающим `when` над единым `UiState`, опциональный device-smoke.
