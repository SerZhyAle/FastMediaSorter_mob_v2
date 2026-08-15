# Стратегическая спецификация: S0351 - Виджет воспроизведения (Audio Now Playing)

**Ticket:** S0351
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-04
**Tier:** 3 - Moderate (ad-hoc)
**Parent ticket:** S0348 (home-widget-icon-refresh) - выделено как суб-спецификация по решению владельца 2026-06-04.
**Tactical plan:** `PLAN/S0351_widget-audio-now-playing/INDEX.md`

> **Scope:** STRATEGIC. Audio now-playing widget for existing persistent audio playback service.

---

## 0. Approval Gate (owner input)

- **Origin:** выделено из S0348 §5.1 (пункт 2.2), §6.4 (Audio now-playing feasibility: Medium) и критерия §11.15.
- **Requested mode:** Provided by user - implementation via `/spec-all S0351`.
- **Goal / expected outcome:** Delegated by user - `/spec-all` auto-approval; implement a home-screen Audio Now Playing widget for existing persistent audio playback.
- **Local anchor:** Provided by user - `S0351`.
- **Scope boundaries / forbidden areas:** Delegated by user - `/spec-all` auto-approval; reuse `AudioPlaybackService` / Media3 session state, avoid Room migrations and new Hilt scopes, keep flavor isolation in manifests, do not redesign notification or full player UI.
- **Done / success signal:** Delegated by user - `/spec-all` auto-approval; widget provider/layout/info/manifest/strings exist, state updates from audio playback service, playback commands work, stale state is cleared after service stop.
- **Autonomy rule:** Delegated by user - `/spec-all` auto-approval; agent may decide reversible widget layout and empty-state behavior from existing widget patterns.
- **UI decisions / delegation:** Delegated by user - `/spec-all` auto-approval; `2x1` and `4x1` use one RemoteViews layout, empty state shows an inactive audio prompt, cover falls back to a music icon, unavailable flavors remove the receiver.
- **Related tickets:** S0348 (parent).

Auto-approved by `/spec-all` on 2026-06-04 because the remaining UI/research decisions are reversible and the implementation can reuse existing persistent audio playback primitives.

---

## 1. Проблема

Управление фоновым аудио сейчас живёт в notification и UI плеера. На home screen нет быстрого контрол-виджета с обложкой и кнопками.

---

## 2. Цели

1. Добавить control-виджет `2x1` / `4x1` для текущей audio session.
2. Отображать обложку трека/альбома (или стандартную иконку), название трека и имя артиста.
3. Кнопки: Play/Pause, Skip Forward/Backward, Add to Favorites.
4. Интеграция через MediaSession (Media3).

**Non-goals:**

- Не показывать полный playlist/queue внутри виджета в первой версии (S0348 §6.4 - defer playlist collection).
- Не дублировать notification controls.

---

## 3. Ограничения

- **Flavor:** только flavors с фоновым аудио (`ENABLE_PERSISTENT_AUDIO_PLAYBACK` / `SUPPORT_AUDIO`). Receiver вырезается там, где недоступно.
- **Update contract:** обновления по событиям MediaSession, не по `updatePeriodMillis` (минимум 30 мин неприемлем для live state, S0348 §6.1).
- **Stale state:** виджет не должен показывать неверный трек после смерти процесса; нужен надёжный snapshot из MediaSession.
- **Производительность:** обложка - из кэша, без тяжёлых bitmap-операций в provider.
- **Локализация:** EN/RU/UK; accessibility для всех кнопок (Rule 17).
- **Flavor isolation:** Rule 15.

### 3.3 Owner inputs (Approval gate)

- **Approval signal:** owner invoked `/spec-all S0351` on 2026-06-04 after changing `/spec-all` rules to allow auto-approval when no blocking ambiguity remains.
- **Autonomy:** agent may decide reversible implementation details with explicit assumptions and must stop only for unsafe or contradictory decisions.
- **Widget surface:** `2x1` and `4x1` use one resizable RemoteViews layout with compact metadata and playback controls.
- **Empty state:** placed widget shows an inactive audio prompt instead of stale track data.
- **Artwork policy:** use already available local/metadata artwork only; fall back to a generic music icon.
- **Favorites action:** enabled only when the service snapshot has enough stable identity to toggle safely.
- **Related tickets:** S0348, S0349, S0350.

---

## 4. Research decisions

1. **Источник стабильного now-playing snapshot вне UI плеера**
   - **Решение:** `AudioPlaybackService` owns the snapshot. It writes a lightweight persistent snapshot (title, artist, artwork URI, playing state, current media URI, resource context) when the Media3 player changes metadata/playback state and asks widgets to update.
   - **Почему:** service is the persistent playback owner; UI `PlayerActivity` may be absent, while widget provider must be able to render after process/service transitions.
   - **Статус:** Resolved.

2. **Политика загрузки/кэширования обложки для RemoteViews**
   - **Решение:** first version uses only already available `MediaMetadata.artworkUri` when it is suitable for `RemoteViews.setImageViewUri`; otherwise it shows `ic_music_note`. No provider-side decoding, network fetch, or heavy bitmap work.
   - **Почему:** provider work must stay lightweight and launcher-safe; existing service metadata already feeds notification / mini now-playing UI.
   - **Статус:** Resolved.

3. **Поведение, когда нет активной session**
   - **Решение:** render an inactive state with generic audio icon, localized “No audio playing” text, and disabled-looking controls. Clear the active snapshot when service is destroyed or playback ends/stops.
   - **Почему:** stale track titles after process death are worse than an empty widget; hiding an already placed widget is not possible.
   - **Статус:** Resolved.

4. **Favorites command boundary**
   - **Решение:** include a visible Favorite action only when enough snapshot identity exists to toggle a favorite safely; otherwise the action is disabled in the widget surface for this first version.
   - **Почему:** `FavoritesEntity` requires a stable URI/resourceId/display name. The service currently has reliable playback identity for local/service-backed items, but not every legacy single-file path carries full favorite context.
   - **Статус:** Resolved.

---

## 5. Критерии готовности

1. Виджет воспроизведения добавляется в размерах `2x1` / `4x1`.
2. Отображает обложку, название трека, артиста для текущего медиа.
3. Управляет состоянием плеера (Play/Pause, Skip) через existing Media3/service command path; Favorites toggles only when the snapshot has safe identity.
4. Не показывает stale-состояние после рестарта процесса.

---

## 6. Связи

- **S0348** - parent; picker registry, pinning, content-widget boundary.

---

## Last Audit

**Date:** 2026-06-04
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 83 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] Optional launcher smoke: place Audio Now Playing widget, start local audio, tap previous/play-pause/next/favorite-safe controls.
- [x] Build validation: standard debug, lite debug, and photos debug builds passed; merged manifests contain the receiver only for standard.
