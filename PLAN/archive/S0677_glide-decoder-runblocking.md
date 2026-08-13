# Спецификация: S0677 - Убрать runBlocking из decode-потока Glide (NetworkVideoFrameDecoder)

**Ticket:** S0677
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-25
**Tier:** 2 - Minor (ad-hoc)

<!-- auto-approved by /spec-all - 2026-06-25 -->

> **Scope:** COMPACT (Simple path). Поведение-сохраняющий рефактор: убрать `runBlocking` с потока пула Glide.

---

## Цель

`NetworkVideoFrameDecoder` (Glide `ResourceDecoder`, исполняется на потоке пула Glide) оборачивает работу с `ThumbnailCacheRepository` в `runBlocking` (4 места: cache-lookup, save, failed-check, dark-evict). Тела этих repo-методов синхронны по сути - `suspend` только из-за suspend-DAO Room, который хопает на собственный executor. `runBlocking` блокирует поток Glide и добавляет хоп, что под конкурентной прокруткой сериализует загрузку превью.

Решение: добавить синхронные (non-suspend) twins DAO-методов и blocking-методы репозитория; декодер зовёт их напрямую на своём потоке (Room допускает синхронные запросы на не-main потоке; SQLite разруливает конкурентные чтения). Поведение идентично, схема БД не меняется.

**Non-goals:**

- Большой рефактор пайплайна Glide (перенос загрузки в async `DataFetcher`) - вне объёма.
- Изменение логики извлечения кадра, дедупликации, классификации transient/permanent.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** parked из research S0675; пересечений-блокеров нет
- **Data scope:** добавляются только синхронные методы DAO/репозитория поверх существующих запросов; нет изменения `@Entity`, нет миграции Room, версия БД не меняется
- **Flavor scope:** все flavor (сетевые превью SMB/SFTP/FTP в `src/main`)

---

## Фазы

### Phase 01 - Синхронные DAO + repo методы

- [ ] `ThumbnailCacheDao`: добавить non-suspend twins тех же запросов: `getThumbnailBlocking`, `updateAccessTimeBlocking`, `insertThumbnailBlocking`, `deleteThumbnailBlocking`.
- [ ] `ThumbnailCacheRepository` (interface) + `Impl`: добавить `getCachedThumbnailBlocking(path): File?`, `saveThumbnailBlocking(path, file)`, `deleteThumbnailBlocking(path)` - тела повторяют существующие suspend-методы, но через blocking-DAO.
- **Verification:** `.\a.ps1 fk` компилируется (Room генерирует синхронные impl).

### Phase 02 - Замена runBlocking в декодере

- [ ] В `NetworkVideoFrameDecoder` заменить 4 `runBlocking { thumbnailCacheRepository.X() }` на blocking-методы: cache-lookup (`getCachedThumbnailBlocking`), save (`saveThumbnailBlocking`), failed-check (`getCachedThumbnailBlocking`), dark-evict (`deleteThumbnailBlocking`).
- [ ] Удалить неиспользуемый `import kotlinx.coroutines.runBlocking`.
- **Verification:** grep по файлу - нет `runBlocking`; `.\a.ps1 d` собирается.

---

## Критерии готовности

1. `NetworkVideoFrameDecoder` не вызывает `runBlocking` на потоке Glide (статически проверяемо grep).
2. Превью грузятся без runBlocking-сериализации под конкурентной прокруткой (по построению; полное подтверждение - профилирование на устройстве с сетевой папкой видео).

---

## Last Audit

**2026-06-25** - by `/spec-all` F5/S4 (audit). Verdict: **Verified**.

- Criterion 1 (no `runBlocking` on Glide thread): grep `NetworkVideoFrameDecoder.kt` -> 0 hits (PASS). Все 4 места заменены на blocking-методы репозитория; `import kotlinx.coroutines.runBlocking` удалён.
- Criterion 2 (без runBlocking-сериализации): выполнено по построению - устранён сам `runBlocking` и хоп на Room-executor; синхронные DAO-методы исполняются прямо на потоке Glide, SQLite разруливает конкурентные чтения. Изменение поведение-сохраняющее (те же DAO-операции, та же логика). Количественный замер прироста под конкурентной прокруткой требует профилирования на устройстве с реальной сетевой папкой видео (manual nicety, не блокер).
- Сборка: `.\a.ps1 cd` (clean) BUILD SUCCESSFUL - Room сгенерировал синхронные impl, схема БД не менялась. (Первый инкрементальный build дал phantom unresolved-ref после мульти-файловой правки; снят clean-сборкой.)
- Гейты: neuroslop / deprecated-pm-flags / fgs-notifications PASS, без регрессий.

---

## Связи

- Parked из research S0675.
