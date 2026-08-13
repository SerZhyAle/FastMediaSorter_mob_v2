# Стратегическая спецификация: S0091 — Bugfix: startup race в диалоге прогресса файловой операции

**Ticket:** S0091
**Status:** Verified
**Implemented date:** 2026-05-05
**Priority:** 95
**Date:** 2026-05-05
**Tier:** 1 — Quick Win
**Roadmap entry:** Ad-hoc — field logs 2026-05-05, Standard release blocker
**Tactical spec:** [`PLAN/S0091_bugfix-file-op-progress-startup-race/INDEX.md`](S0091_bugfix-file-op-progress-startup-race/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Копирование и перемещение файлов падает до начала передачи данных, если progress-событие приходит раньше, чем delayed progress dialog успевает пройти `show()` и inflate своего layout. В результате пользователь получает ложный `Copy failed`, временный `.temp_copy` файл откатывается, а вся операция отменяется не из-за transfer-layer, а из-за UI-race.

Логи 2026-05-05 подтверждают стабильное воспроизведение: `Progress received: Starting(..)` → `FileOperationProgressDialog: Starting` → `UninitializedPropertyAccessException: lateinit property tvOverallPercent has not been initialized`.

---

## 2. Цели

1. Copy/move operation больше не падает из-за раннего progress update.
2. Поведение delayed dialog сохраняется: короткие операции не обязаны показывать модальное окно.
3. Transfer/business logic не меняется.
4. Fix работает во всех flavor, где доступен общий file operation flow.

**Non-goals:**

- Редизайн диалога прогресса.
- Замена delayed-show механики на другой UX.
- Переработка `executeWithProgress()`.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Исправление должно быть минимальным и безопасным для Standard release.
2. Нельзя ломать уже существующую логику скрытия коротких операций.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard`, `lite`, `photos`, `legacy`, `vr`, `vrUnlicensed` — код общий.
- **API level:** без API-specific ветвлений.
- **Wear OS:** не затрагивается.
- **Производительность:** без дополнительных I/O и без частого UI invalidation.
- **Совместимость данных:** нет.
- **Локализация:** новые строки не требуются.

---

## 4. Контекст текущей архитектуры

File operation flow создаёт отдельный диалог прогресса и запускает delayed show. Progress-события приходят из asynchronous use-case независимо от жизненного цикла окна. Внутри диалога уже есть частичная защита для `Processing`-событий через отложенное применение latest state после `onStart`, но ветка `Starting` остаётся вне этого механизма и напрямую пишет в summary-View. Это делает компонент неустойчивым к ранним progress callback.

---

## 5. Предлагаемый подход

Убрать прямой доступ к summary-View до гарантированной инициализации layout. Fix должен остаться локальным в диалоге: либо через readiness guard, либо через распространение существующего deferred-apply pattern на `Starting`/terminal-состояния. Предпочтение — минимальное изменение с самым узким риском.

### 5.1 Основные столпы

**Dialog-local lifecycle safety.** Компонент сам обязан быть безопасным к progress callbacks до `onCreate()`.

### 5.2 Потоки данных и событий

Нет изменений в upstream/downstream flow. Меняется только момент, в который dialog пишет в свои View.

### 5.3 Точки расширяемости

Если позже delayed dialog будет заменён на non-modal indicator, это исправление не мешает дальнейшей эволюции.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Исправление окажется неполным и останется ещё один pre-init доступ к View | Низкая | Повторный crash | Точечный grep всех обращений к `lateinit`-View внутри диалога |
| Fix случайно изменит UX completed path | Низкая | Диалог не закроется на быстрых операциях | Узкая compile validation и ручная log-based проверка |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES*.md` — это исправление регрессии существующей copy/move functionality, а не новая возможность.

---

## 9. Архитектурные решения (ADR)

ADR нет — локальный bugfix в существующем UI-компоненте.

---

## 10. Связи с другими спеками

- `S0074` `copy-move-dialog-progress` — базовая реализация прогресс-диалога.
- `S0079` `bugfix-file-op-progress-dialog-landscape-npe` — предыдущий fix другой failure-mode в той же области.

---

## 11. Критерии готовности (strategic-level)

1. Логический путь `Starting` больше не приводит к `lateinit`-crash.
2. Copy/move flow не отменяется из-за диалога до начала передачи.
3. Сборка проходит без новых ошибок.
4. В touched Kotlin file нет новых lint/IDE diagnostics.

---

## 12. Ссылка на тактическую спецификацию

Тактический план: [`PLAN/S0091_bugfix-file-op-progress-startup-race/INDEX.md`](S0091_bugfix-file-op-progress-startup-race/INDEX.md)

## Last Audit

**Date:** 2026-05-06
**Mode:** full
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 7 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 2

### Manual / on-device

- [ ] On-device copy/move: confirm operation completes without spurious "Copy failed" when progress event arrives before dialog shows.
- [ ] Run `lintStandardDebug` / IDE diagnostics on `FileOperationProgressDialog.kt` — verify no new warnings.