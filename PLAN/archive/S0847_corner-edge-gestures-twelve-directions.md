# Draft: S0847 - Corner edge gestures with up to twelve directions

**Ticket:** S0847
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 3 - Moderate
**Source:** User request 2026-07-01 (`/spec-draft`)

> Draft inbox - raw capture. Not yet researched/approved. Style gate exempt.

## 0. Captured request

**Captured:** 2026-07-01

**Text:**

сейас реализованы "жесты с левого краю". реализовать Жесты слева сверху, слева снизу, справа сверху и справа снизу. Итого до 12 жестов

**Attachments:** none.

## 1. Problem

Сейчас gesture subsystem уже умеет жесты только от левого края, с тремя направлениями распознавания. По запросу владельца этого больше недостаточно: нужно расширить модель до четырёх стартовых зон по углам/краям экрана, чтобы пользователь мог назначать больше независимых быстрых действий и довести систему до набора до 12 жестов.

## 2. Direction (rough)

- Keep the existing left-edge gesture concept as the baseline, but expand it.
- New start zones requested by the owner:
  - left-top
  - left-bottom
  - right-top
  - right-bottom
- Product goal: up to 12 independently configurable gestures in total.
- The request is about expanding the gesture-address space, not about redefining the existing action catalog yet.
- Later implementation will need to decide how the current three-direction model maps into each new zone, but the owner-level request is clear: four corner/edge anchors, total capacity up to 12 gestures.

## 3. Open points

1. Confirm whether the existing left-edge full-height strip remains in addition to the four new corner zones, or whether it is replaced by them.
2. Define the exact three gesture directions per corner zone so the total really equals 12 and remains learnable/non-conflicting.
3. Check whether the owner expects the same action picker and action catalog for every new gesture slot, or whether some corner zones are reserved for app/panel-only launch actions.

## 4. Current architecture (researched 2026-07-10)

- Direction model: `ScreenshotGestureDirection { DOWN, RIGHT, UP }` - exactly three directions, all anchored to a single left-edge strip.
- Dispatch: overlay manager -> `ScreenshotGestureActionDispatcher`, which maps each direction to `AppSettings.screenshotGestureAction{Down,Right,Up}` (three independent action slots).
- Action catalog: `ScreenshotGestureAction` (shared enum); defaults seeded by `SeedDefaultGestureBindingsUseCase`.
- Settings UI: `OperationsGesturesManager` (Operations settings) binds the three slots to action pickers.
- The edge strip is the opt-in `specialUse` FGS edge-gesture family (S0672); today one zone (left edge) x 3 directions = 3 gestures.

So the current address space is **1 zone x 3 directions**. The owner asks for **4 corner/edge zones x 3 directions = 12**.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0672 (edge-gesture specialUse FGS family - origin of the current left-edge strip); S0680, S0788-S0797 (active adjacent gesture family); archived context S0425/S0622/S0623.
- **Zone geometry / directions / catalog:** resolved 2026-07-11 (see §6 - REVISED zone geometry: 4 edge bands, reuse DOWN/RIGHT/UP, shared catalog).

## 6. Owner decisions required (BlockQuestions)

These are product/UX decisions, not derivable from code - they determine the whole data model, settings UI, and detection geometry, so implementation cannot start without them:

1. **Left-edge strip: keep or replace?** "Итого до 12" implies 4 zones x 3 = 12. But today's left edge is a full-height strip, not a corner. Options:
   - (a) Replace the left-edge strip with 4 corner zones (total = 12).
   - (b) Keep the left-edge strip AND add 4 corners (total = 3 + 12 = 15, not 12).
   - (c) Reconceive the left edge as two of the corner zones (left-top / left-bottom) and add right-top / right-bottom (total = 12, but left-* semantics change).
   - **RESOLVED (2026-07-11): (a)** - replace the left-edge strip with 4 corner zones = exactly 12. The existing 3 left-edge action bindings migrate into corner slots.
2. **Exact 3 directions per corner zone** so the total really equals 12 and stays learnable / non-conflicting (e.g. for left-top: DOWN / RIGHT / DIAGONAL? each corner needs a distinct, unambiguous triple given its screen position).
   - **RESOLVED (2026-07-11): reuse the existing `DOWN / RIGHT / UP` triple for every corner zone.** No new direction enum values or diagonal detection - the current `ScreenshotGestureDirection { DOWN, RIGHT, UP }` applies identically in all 4 zones. (Tactical note: from a top or right corner, UP/RIGHT point toward the screen edge; detection geometry must anchor the recognizer to the corner without requiring off-screen travel.)
3. **Action catalog per slot:** same `ScreenshotGestureAction` picker for all 12 slots, or are some corner zones reserved for app-panel / launch-only actions?
   - **RESOLVED (2026-07-11): single shared catalog** - every one of the 12 slots uses the full `ScreenshotGestureAction` picker. No launch-only / panel-only reserved zones.

### Quiz decisions (2026-07-11)
- Left-edge strip: keep or replace? → **Replace with 4 corner zones (=12)** (clean "up to 12" math; existing 3 left bindings migrate into corners).
- Exact 3 directions per corner zone? → **Reuse the same DOWN/RIGHT/UP triple in every zone** (owner chose enum reuse over per-corner diagonals; recognizer geometry to be resolved in /spec-tech so corner-anchored swipes need no off-screen travel).
- Action catalog per slot? → **Single shared `ScreenshotGestureAction` catalog for all 12 slots** (no reserved launch-only zones).

### REVISED zone geometry (2026-07-11, owner direct Q - SUPERSEDES the "corner" framing above)

Владелец уточнил: не углы, а **4 краевые полосы** (edge bands), заданные диапазоном высоты вдоль левого и правого края:

- Зона 1: **левый край, 10%..40% высоты экрана**.
- Зона 2: **левый край, 60%..90% высоты**.
- Зона 3: **правый край, 10%..40% высоты**.
- Зона 4: **правый край, 60%..90% высоты**.

- Каждая из 4 зон **независимо включается/выключается в настройках** (4 тумблера).
- Средняя треть краёв (40%..60%) намеренно не задействована (промежуток между полосами).
- Старая полноразмерная левая полоса **заменяется** двумя левыми полосами (10-40 / 60-90).
- Направления: остаётся решение quiz - **reuse DOWN/RIGHT/UP** на зону, но геометрия распознавания зеркалится для правого края (внутрь экрана - LEFT вместо RIGHT); точная привязка распознавателя к краевой полосе без ухода за экран - в /spec-tech.
- Каталог действий: остаётся **единый общий** `ScreenshotGestureAction` для всех слотов.
- Итог: 4 зоны x 3 направления = **до 12** жестов, каждая зона отключаема.

Статус после решения: Approved (все продуктовые решения приняты; детект-геометрия и модель настроек - в /spec-tech).

## Last Audit

**Date:** 2026-07-11
**Status:** BlockNeedUserTest (real-device gesture-geometry verification pending)
**Method:** from-code + build (standard + noLegal) + emulator smoke test (emulator-5556, Android 17)

Implemented - 4-band 12-gesture model:
- Domain: `ScreenshotGestureZone {LEFT_TOP, LEFT_BOTTOM, RIGHT_TOP, RIGHT_BOTTOM}`; `AppSettings` gains 4 zone-enable toggles + 12 action slots (replacing the legacy 3) plus `screenshotGestureAction(zone, dir)` / `screenshotGestureZoneEnabled(zone)` resolvers.
- Persistence: `ScreenshotSettingsStore` 16 new DataStore keys; LEFT_TOP reads fall back to the 3 legacy keys (no data loss, no Room bump); `SettingsRepositoryImpl` mapping updated.
- Detection: `ScreenGestureOverlayManager` rewritten from one left strip to four band views (2 left, 2 right at 10-40% / 60-90% of safe height); inward-drag classification mirrors the right edge (negated dx); zone-aware `onGestureMatched(zone, direction)`.
- Wiring: zone threaded overlay -> host -> consent -> capture service -> dispatcher (`EXTRA_GESTURE_ZONE`, default LEFT_TOP); noLegal accessibility path + quick-settings edge-tile updated; `ScreenshotGestureActionDispatcher.actionFor(zone, direction)`.
- Settings UI: 4 zone-enable toggles + 12 collapsible pickers (portrait + landscape); `OperationsGesturesManager` data-driven bind/render; the live overlay rebuilds on any zone/strip change; strings EN/RU/UK.
- Seed: LEFT_TOP seeded from the legacy triple; the other three bands are opt-in.
- Docs: settings-manifest + 16 annotations + `SETTINGS_REFERENCE*` regenerated; `ALL_FEATURES` S0847 record; class catalog synced.

Build/gates: standard `fc` + noLegal `fkn` BUILD SUCCESSFUL; settings-doc-sync 5/5 PASS; static gates (neuroslop / ticket-log / listener / pm / flavor / orientation) PASS; detekt-clean for all touched files.

Device (emulator-5556, Android 17): clean install (v2.60.7110.431-DEBUG) + launch -> MainActivity resumed, no FATAL/crash. SettingsActivity + the Operations tab inflate `fragment_settings_destinations` with the 20 new gesture view IDs (4 zone toggles + 12 pickers + 4 containers) with no InflateException -> binding + `OperationsGesturesManager.setup()` validated at runtime. The domain/persistence/seed integration (16 new `AppSettings` fields, legacy-key LEFT_TOP migration, `SeedDefaultGestureBindingsUseCase`) runs on first launch without crashing. The edge-strip overlay swipe geometry itself was not exercised (AVD collapse-toggle touch-wedge + it is a real-device check).

Real-device gate: precise per-band touch geometry (right-edge mirroring, 10-40% / 60-90% band bounds, middle-gap non-trigger, per-zone enable) needs on-device verification per the Status note. An emulator confirms UI render + no-crash only; the edge-strip overlay swipe geometry is a real-device manual check.

## Related

- Archived family context: S0425, S0622, S0623. Active adjacent family: S0680, S0788-S0797.
