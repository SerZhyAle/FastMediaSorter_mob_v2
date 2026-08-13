# Phase 05 - docs-catalog-cleanup

**Goal:** Documentation, capability inventory, catalog sync, debug-tag insertion, and the final build that validates code + tag in one pass before the device-test gate.

**Depends on:** all

---

## Steps

- [ ] **05.1 - FEATURES (showcase) + ALL_FEATURES (inventory).**
  - Add one sentence to `docs/FEATURES.md` + `_RU.md` + `_UK.md` in the photo-capture block (strategic §8).
  - Record the delivered capability via `scripts/all_features/add.ps1` (EN-only inventory record).
  - **Verification:** grep the new sentence in all three FEATURES files; `scripts/all_features/validate.ps1` exit 0.

- [ ] **05.2 - Catalog sync.**
  - `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.
  - **Verification:** script exit 0.

- [ ] **05.3 - Debug verification tag (BlockNeedUserTest gate).**
  - Insert exactly one `Timber.d("S0469: <entry-point description>")` at the clipboard-copy gate in `CameraCaptureSaver.save()` (the changed flow entry), logging the flag value - so the device tester can confirm the gate fired.
  - **Verification:** exactly one `S0469:` tag in `.kt`; no permanent log carries `S0469`.

- [ ] **05.4 - Final build + status.**
  - `.\a.ps1 dq` (standard debug) - validates code + tag in one pass.
  - Set status `BlockNeedUserTest` with a `-StatusNote` describing the device test (enable option, capture photo, paste into an `image/*` receiver, confirm assigned operation still runs).
  - **Verification:** build PASS; journal status `BlockNeedUserTest`.

---

## Phase Done Criteria

- Docs + inventory updated, catalog synced, single debug tag present, build green, ticket parked for on-device confirmation.
