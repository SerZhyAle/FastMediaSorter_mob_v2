# Phase 03 - dispatch-and-wiring

**Goal:** Thread the originating `ScreenshotGestureZone` alongside the existing `ScreenshotGestureDirection` from the overlay manager through the host/consent/capture chain into `ScreenshotGestureActionDispatcher`, so the dispatcher resolves the correct one of 12 slots.

**Depends on:** 01.
**Source sets:** `src/main` (dispatcher), `src/screenCapture` + `src/noLegal` (hosts/services), plus any consent-activity + capture-service intent plumbing they touch.

---

## Steps

### [ ] 03.1 - Zone-aware dispatcher

- In `ScreenshotGestureActionDispatcher.actionFor`, change the signature to `actionFor(zone: ScreenshotGestureZone, direction: ScreenshotGestureDirection)` and resolve via the `AppSettings.screenshotGestureAction(zone, direction)` helper from Phase 01.
- **Verification:** dispatcher compiles; every (zone, direction) resolves to its slot; no reference to the removed legacy fields.

### [ ] 03.2 - Overlay-manager callback carries the zone

- The `ScreenGestureOverlayManager` callback becomes `onGestureMatched: (zone: ScreenshotGestureZone, direction: ScreenshotGestureDirection) -> Unit` (full 4-band rewrite is Phase 04; this step only widens the contract + the single existing call site so the tree compiles).
- **Verification:** the manager's callback type includes the zone.

### [ ] 03.3 - Thread zone through the intent plumbing

- Grep the current path that carries `direction` from `OverlayHostService.launchConsentActivity(direction)` to `dispatcher.actionFor(direction)` (consent activity + capture service intent extras). Add a parallel `EXTRA_GESTURE_ZONE` string extra (`ScreenshotGestureZone.name`) next to the existing direction extra, read it back with `ScreenshotGestureZone.valueOf`, default `LEFT_TOP` when absent (back-compat with an in-flight intent).
- Update `launchConsentActivity` and every hop signature to pass `(zone, direction)`.
- **Verification:** grep shows the zone extra written at the overlay host and read at the dispatcher call site; no hop drops it.

### [ ] 03.4 - noLegal accessibility host

- Apply the same callback + intent-extra change to the noLegal accessibility overlay path (`src/noLegal/.../ScreenshotAccessibilityService.kt` and its `ScreenGestureOverlayManager` construction).
- **Verification:** noLegal source set references the zone-aware callback; `.\a.ps1 fkn` (noLegal Kotlin compile) passes.

### [ ] 03.5 - standardScreenCapture / controller impls

- Update `standardScreenCapture` + `noLegal` `ScreenGestureOverlayControllerImpl` and any DI (`ScreenGestureOverlayModule`) if the controller surface changed. If the controller only toggles show/hide (no direction/zone), no change beyond compile.
- **Verification:** `.\a.ps1 fk` and `.\a.ps1 fkn` both pass.

---

## Phase Done Criteria

- [ ] Dispatcher resolves 12 slots by (zone, direction).
- [ ] Zone extra threaded end-to-end (overlay -> host -> consent -> capture -> dispatcher), defaulting LEFT_TOP when absent.
- [ ] standard + noLegal Kotlin compile pass.
