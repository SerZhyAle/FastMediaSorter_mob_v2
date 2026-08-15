# Phase 02 - per-slot-payload

**Goal:** Introduce a generic per-slot string payload (12 slots), value-agnostic (URL here, app package in S1036). ADR-3 shared mechanism.

## Steps

- [ ] **2.1** `AppSettings`: add 12 per-slot payload fields (or a `Map<slotKey,String>` mirroring the existing action-field shape at :196-218/:385-424) with a `screenshotGesturePayload(zone,dir)` accessor. Default empty. Verify: accessor present; compiles.
- [ ] **2.2** `data/repository/settings/ScreenshotSettingsStore.kt`: persist the payload per slot via `stringPreferencesKey` (mirror the existing 12-slot action-key block :36-47/:131-142). Verify: read/write round-trips; add a unit test (default empty; save/load a value for one slot).
- [ ] **2.3** Keep the payload semantics value-agnostic (no URL/package assumptions in storage). Document the shared contract with S1036 in a KDoc on the accessor. Verify: KDoc references S1036; `a.ps1 fk` compiles.

## Done criteria
- Per-slot string payload persists for all 12 slots; unit test green; ready for URL (Phase 04) and S1036 app-package.
