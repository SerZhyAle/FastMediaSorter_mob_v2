# Phase 06 - docs-and-closure

**Goal:** Regenerate settings docs (Rule 22), sync the class catalog, record the shipped capability, and route the device-verification gate.

**Depends on:** all prior phases.
**Source set:** docs + scripts (no runtime code).

---

## Steps

### [ ] 06.1 - Settings docs sync (Rule 22)

- The gesture settings changed (4 new toggles + 12 slots, replacing 3). Regenerate `docs/settings/settings-manifest.json` + `docs/SETTINGS_REFERENCE*.md` and update `docs/settings/settings-annotations.json` for the new keys. Run `scripts/quality/assert-settings-doc-sync.ps1` (via `post-change.ps1`).
- **Verification:** settings-doc-sync gate passes.

### [ ] 06.2 - Catalog sync

- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (new `ScreenshotGestureZone` class). Fill `role`+`status` via `set.ps1` if flagged.
- **Verification:** catalog sync OK; new class present.

### [ ] 06.3 - Capability inventory

- Record the shippable capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` (EN-only): "Up to 12 configurable edge-swipe gestures across 4 independently-toggleable screen-edge bands." Spec field `S0847`.
- **Verification:** `scripts/all_features/validate.ps1` passes; grep `S0847` in `docs/ALL_FEATURES.jsonl` returns the record.

### [ ] 06.4 - Device-verify gate

- With emulator-5556 attached and the `S0847:` probe in place (Phase 04.4), set status `BlockNeedUserTest` and auto-run `/spec-test-device S0847` -> `/spec-check S0847`. `/spec-check` converts evidence to Verified/Partial and removes the probe on transition out of BlockNeedUserTest.
- **Verification:** `## Last Audit` carries per-zone PASS/FAIL evidence; status advanced.

---

## Phase Done Criteria

- [ ] Settings docs regenerated; Rule 22 gate green.
- [ ] Catalog synced; `ScreenshotGestureZone` catalogued.
- [ ] ALL_FEATURES S0847 record present.
- [ ] Device gate routed (BlockNeedUserTest -> test-device -> check).
