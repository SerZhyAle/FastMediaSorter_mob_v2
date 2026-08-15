# Phase 04 - Docs, catalog, features

**Goal:** Reflect the now-Play-available capability in user docs, regenerate the class catalog for the relocated classes, and record the dev/functionality log entries.

**Depends on:** Phases 01-03.

---

## Steps

### 4.1 FEATURES (EN/RU/UK)

Add one sentence to `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` describing the capability now available in Play builds: edge-gesture screenshot of any screen, saved to a chosen resource or the device screenshots folder. Place it near other capture/screenshot wording. Note the consent dialog is expected on each capture (Play-safe path).

**Verification:** the new sentence is present in all three files; `pwsh -NoProfile -File scripts/check_strings_localized.ps1` not applicable (docs, not strings) - instead confirm EN/RU/UK parity by grepping each file for the added keyword.

### 4.2 Strings locale audit (only if 2.1 added a string)

If `screenshot_overlay_permission_rationale` (or any key) had to be added in Phase 02, run the audit.

**Verification:** `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "screenshot_overlay"` exits 0. If no string was added, mark this step `[skipped - no new string]`.

### 4.3 Catalog sync

Regenerate the class catalog for the relocated + new classes.

**Verification:** `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0; `dev/CATALOG/scripts/query.ps1 -ClassMatches "*ScreenGestureOverlayControllerImpl*"` lists the Play impl.

### 4.4 Dev log + functionality log

One `dev/CHANGELOG.md` entry per modified file via `scripts/add_to_dev_log.ps1`. One `dev/FUNCTIONALITY.log` entry (ADD: edge-gesture screenshot now in Play builds) via `scripts/add_to_functionality_log.ps1` (run last - it leaves a non-zero exit code).

**Verification:** `grep S0418 dev/CHANGELOG.md` shows entries; `grep S0418 dev/FUNCTIONALITY.log` shows one ADD line.

---

## Phase Done Criteria

- [ ] FEATURES EN/RU/UK updated.
- [ ] String audit green (or skipped - no new string).
- [ ] Catalog regenerated; Play impl listed.
- [ ] Dev + functionality logs written.
