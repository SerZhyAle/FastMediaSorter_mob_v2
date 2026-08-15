# S0593 - Stream row play-status bullet (green/yellow/red by local play outcome)

**Ticket:** S0593
**Status:** Archived
**Priority:** 55
**Date:** 2026-06-21
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-21 (развитие S0589/S0570)
**Complexity:** Full (Room migration + app code + UI), compact single-file spec.

<!-- auto-approved by /spec-all - 2026-06-21 -->

> **Scope:** First cut of S0593. Local-only per-stream play outcome -> colored status bullet in the Streams list row. No CSV `last_online`, no "verified only" filter, no server telemetry (deferred within S0593).

---

## Goal (RU)

В строке списка трансляций показываем цветной шарик статуса по ЛОКАЛЬНОМУ факту последней попытки воспроизведения на этом устройстве: 🟢 проигралось OK, 🔴 последняя попытка не удалась, 🟡 ещё не пробовали.. Исход хранится в Room (`stream_sources`), пишется из существующих success/error-хуков плеера (видео/RTSP и inline-аудио), отображается в уже существующем per-row binding (рядом с `ivKind`).. Статус НЕ только цветом - разные формы иконки + `contentDescription` для TalkBack..

**Захвачено (verbatim):** «давай помечать желтым/зеленым/красным шариком строки стримов в списке» (2026-06-21); семантика green из «ставить зелёным как проверено, когда стрим пошёл».

**Owner decision (2026-06-21):** 1-й cut = шарик по локальному факту play (не каталожный/пробный сигнал).

**Non-goals (в этой итерации):**

- CSV `last_online` + стампинг скриптом; фильтр "только проверенные"; дата проверки в строке; каталожный пробный статус для непроигранных строк.
- 3-сек guard перед "OK" (записываем OK на реальном playing; робастность порога - будущая итерация).
- lite/photos: Streams скрыт (`SUPPORT_STREAMS=false`) - изменений нет; таблица в общей БД безвредна.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0589, S0570, S0565, S0579, S0588, S0590
- **UI scope:** один новый `ImageView` статуса в `item_stream_source.xml` (нет layout-land для item трансляции - один layout на обе ориентации); 3 формы-иконки + tint + contentDescription; новые строки EN/RU/UK.
- **Data-compat:** Room 34 -> 35, две nullable-колонки через `ALTER TABLE` (без деструктива на всех minSdk).

---

## Phases

### Phase 01 - Schema: outcome columns + Room 34->35 migration

Steps:
1. `StreamSourceEntity.kt`: add `val lastPlayOutcome: String? = null` and `val lastPlayOutcomeAt: Long? = null` after `lastPlayedAt`.
2. New file `Migration34To35.kt` (top-level `val MIGRATION_34_35 = object : Migration(34, 35)`), two `ALTER TABLE stream_sources ADD COLUMN lastPlayOutcome TEXT` / `ADD COLUMN lastPlayOutcomeAt INTEGER` (follow `Migration33To34.kt`).
3. `AppDatabase.kt:31`: bump `version = 34` -> `35`.
4. `DatabaseModule.kt` (~L103): register `MIGRATION_34_35` in `.addMigrations(...)` after `MIGRATION_33_34`.

Verification:
- `.\a.ps1 fk` compiles. expected: success.
- Grep: `version = 35` in AppDatabase; `MIGRATION_34_35` referenced in DatabaseModule. expected: present.

### Phase 02 - DAO + Repository + UseCase write path

Steps:
1. `StreamSourceDao.kt`: add `@Query("UPDATE stream_sources SET lastPlayOutcome = :outcome, lastPlayOutcomeAt = :atMillis WHERE id = :id") suspend fun markPlayOutcome(id: String, outcome: String, atMillis: Long)`.
2. `StreamSourceRepository.kt`: add `suspend fun recordPlayOutcome(id: String, outcome: String)` delegating to DAO with `System.currentTimeMillis()`.
3. New `RecordStreamPlayOutcomeUseCase.kt` (`domain/usecase/streams/`, `@Inject constructor(private val repository: StreamSourceRepository)`), `suspend operator fun invoke(id: String, ok: Boolean)` -> `repository.recordPlayOutcome(id, if (ok) "OK" else "FAIL")`. Define the outcome constants ("OK"/"FAIL") in one place.

Verification:
- `.\a.ps1 fk` compiles. expected: success.

### Phase 03 - Record outcome from player hooks (4 seams)

Steps:
1. Video FAIL: `PlayerViewModel.onStreamPlaybackFailed` (~L249) - after resolving `source` via `GetStreamSourceByUrlUseCase`, call `recordStreamPlayOutcomeUseCase(source.id, ok = false)` in `viewModelScope`. Inject the UseCase into `PlayerViewModel`.
2. Video OK: add `PlayerViewModel.recordStreamPlayOk(path)` that resolves url->id and records `ok = true`; call it from `PlayerPlaybackCallbackImpl.onPlaybackReady` when `isStreamUrl(currentPath)`.
3. Inline audio FAIL: `StreamInlineAudioManager.onError` already has `currentSource` - route its id to the UseCase via `StreamsViewModel` (add `StreamsViewModel.recordStreamOutcome(id, ok)`; Activity forwards the existing `onError` callback).
4. Inline audio OK: add `onSuccess: (StreamSourceEntity) -> Unit = {}` to `StreamInlineAudioManager`, fired from `playerListener.onIsPlayingChanged(true)` once per source; Activity forwards to `StreamsViewModel.recordStreamOutcome(source.id, ok = true)`.

Verification:
- `.\a.ps1 fk` compiles. expected: success.
- Grep: `recordStreamPlayOutcomeUseCase` referenced in PlayerViewModel; `recordStreamOutcome` in StreamsViewModel. expected: present.

### Phase 04 - UI: status bullet in the row

Steps:
1. Three vector drawables (distinct shapes, not color-only): `ic_stream_status_unknown` (hollow circle), `ic_stream_status_ok` (circle + check), `ic_stream_status_failed` (circle + exclamation).
2. Three semantic colors in `colors.xml`: `stream_status_ok` (green), `stream_status_failed` (red), `stream_status_unknown` (amber) - referenced via code tint, not hardcoded hex in layout.
3. `item_stream_source.xml`: add `ImageView` `ivPlayStatus` (~14dp) leading `ivKind`; `contentDescription` set in code.
4. `StreamSourceAdapter.VH.bind`: add `bindPlayStatus(source.lastPlayOutcome)` - pick drawable + tint + contentDescription by state (null = unknown/🟡, "OK" = 🟢, "FAIL" = 🔴) following the `bindPinState` tint pattern.
5. Strings EN/RU/UK: `stream_status_ok` / `stream_status_failed` / `stream_status_unknown` (contentDescription text) via `set-android-string.ps1 -Action add`.

Verification:
- `.\a.ps1 fc` (code + resources) compiles. expected: success.
- `check_strings_localized.ps1 -KeyPrefix "stream_status_"` exit 0. expected: pass.
- Neuroslop gate (no hardcoded hex in layout). expected: pass.

### Phase 05 - Device-test gate + closure

Steps:
1. Insert `Timber.d("S0593: <flow>")` probes at the 4 record seams + the bind branch (entry points of changed flows).
2. `.\a.ps1 fc` (validates code + tags). Set status `BlockNeedUserTest` with note.
3. Dev log + `catalog_sync.ps1 -Module app_v2` + `ALL_FEATURES` record (user-visible capability).

Verification:
- Build passes with tags. expected: success.
- One `Timber.d("S0593:` per changed-flow entry. expected: present.

---

## Acceptance criteria

1. Запуск трансляции, которая успешно играет -> её строка получает 🟢 (после возврата в список).
2. Запуск трансляции, что падает (cert/401/DNS/404) -> строка получает 🔴.
3. Никогда не запускавшаяся трансляция -> 🟡.
4. Статус переживает холодный старт (Room), переживает re-pin/re-order.
5. Статус различим не только цветом (форма иконки + TalkBack contentDescription).
6. Обычные не-stream экраны/файлы не затронуты; lite/photos без изменений.
