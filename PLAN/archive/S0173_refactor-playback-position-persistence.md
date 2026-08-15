# Стратегическая спецификация: S0173 — Рефактор: единая инфраструктура сохранения/восстановления позиции

**Ticket:** S0173
**Status:** Verified
**Priority:** 30
**Date:** 2026-05-12
**Tier:** 3 — Tech Debt
**Roadmap entry:** Следствие S0172 — выявленное дублирование при реализации баг-фикса

> **Scope:** STRATEGIC. Цели, ограничения, ADR. Без имён методов, путей, миграций Room, модулей Hilt.

---

## 1. Проблема

S0172 добавил сохранение/восстановление позиции для сервисного пути (SFTP/SMB/FTP через `AudioPlaybackService`). В результате в кодовой базе появилось дублирование:

**Save loop:** одинаковая логика Handler-Runnable с 15-секундным интервалом существует в двух местах: в расширениях `VideoPlayerManager` (`PlaybackPositionHelper`) и в `AudioPlaybackService`. Каждый экземпляр независимо управляет scheduling, skip-if-unchanged и IO-диспетчером.

**Restore + seek:** паттерн «прочитать позицию из репозитория → seekTo → показать toast» повторяется в `VideoPlayerManager` и в `PlayerMediaLoaderManager` (SFTP-ветка). Форматирование времени (`formatTimeMs`) тоже продублировано.

**Незакрытая ветка:** облачный путь (Google Drive / OneDrive / Dropbox) в `playAudioViaService()` не имеет ни save, ни restore позиции — в отличие от SMB/SFTP/FTP, накрытых S0172.

---

## 2. Цели

1. Выделить логику периодического сохранения позиции в standalone utility — единый класс, не зависящий от `VideoPlayerManager` или `AudioPlaybackService`.
2. Создать единую suspend-функцию restore-and-play, пригодную для вызова из любого контекста: Activity-scope и Service.
3. Перевести `VideoPlayerManager`, `AudioPlaybackService` и `PlayerMediaLoaderManager` на новые утилиты.
4. Накрыть облачную ветку (`ResourceType.CLOUD`) в `playAudioViaService()` — restore и save позиции.
5. После рефактора поведение для пользователя не меняется. Никаких новых UI-элементов.

**Non-goals:**
- Изменение интервала сохранения (15 с остаётся).
- Добавление позиционирования для форматов, которые его сейчас не поддерживают.
- Изменение `PlaybackPositionRepository` или схемы Room.

---

## 3. Архитектурные решения (ADR)

### ADR-1: `PositionSaveLoop` как standalone utility, не extension

Extension-функции на `VideoPlayerManager` (`PlaybackPositionHelper`) хороши для конкретного случая, но не переиспользуются сервисом без рефактора. Новый utility-класс принимает зависимости через конструктор (`interval`, `getPositionMs: () -> Long`, `getDurationMs: () -> Long`, `getPath: () -> String?`, `onSave: suspend (path, pos, dur) -> Unit`) и не знает о конкретных player-типах.

### ADR-2: `PlaybackPositionRestorer` как suspend utility, не inline-код

Функция принимает `path: String`, `player: Player`, `repository: PlaybackPositionRepository`, `context: Context`, `stringResId: Int` и возвращает `Long` (restored position, 0 если нет). Toast показывается внутри. Вызывающий код делает только `seekTo(restoredPos)` если результат > 0.

### ADR-3: Ключ для облака — оригинальный cloud URL

Аналогично ADR-2 из S0172 (SFTP: оригинальный путь, не кэш). Для облака ключом будет оригинальный путь из `MediaFile` (как он хранится в БД), не `file://` URI кэша.

### ADR-4: `PlaybackPositionHelper.kt` остаётся, делегирует к utility

Не удаляем файл расширений — он используется в `VideoPlayerManager`. Вместо этого его реализация делегирует к `PositionSaveLoop`. Публичный API расширений не меняется → `VideoPlayerManager` не требует правок.

---

## 4. Затронутые компоненты

| Компонент | Изменение |
|-----------|-----------|
| `PlaybackPositionHelper.kt` | Реализация `startPositionSaving()` делегирует к `PositionSaveLoop` |
| `AudioPlaybackService.kt` | Приватные методы save-loop заменяются на `PositionSaveLoop`; restore через `PlaybackPositionRestorer` |
| `PlayerMediaLoaderManager.kt` | SFTP-ветка: inline restore заменяется на `PlaybackPositionRestorer`; облачная ветка: добавляются save + restore |
| `PositionSaveLoop.kt` (новый) | Standalone utility в `ui/player/helpers/` |
| `PlaybackPositionRestorer.kt` (новый) | Standalone utility в `ui/player/helpers/` |

---

## 5. Риски

**Низкий.** Это pure refactor — поведение не меняется. Обе утилиты покрываются unit-тестами перед переключением. Облачная ветка — единственное новое поведение.

**Регрессия:** если `PositionSaveLoop` получает неправильный `getPath()` при смене трека — позиция пишется не туда. Нужен unit-тест на смену пути.

---

## 6. Открытые вопросы

Нет.

**Tactical plan:** `PLAN/S0173_refactor-playback-position-persistence/INDEX.md`
**Implemented date:** 2026-05-12

## Last Audit

**Date:** 2026-05-12
**Mode:** full
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 21 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Manual / on-device

- [ ] SFTP audio resume: open an SFTP file mid-play, kill app, reopen — toast appears, playback resumes from saved position.
- [ ] Cloud audio resume (Google Drive / OneDrive / Dropbox): same flow as SFTP.
- [ ] Video position auto-save still works after PlaybackPositionHelper refactor.
- [ ] No regression in audio background playback lifecycle (AudioPlaybackService serviceScope cancel on destroy).
