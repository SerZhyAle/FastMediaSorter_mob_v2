# Phase 04 - Unit test for the settings-to-wallpaper mapping

**Strategic spec:** [`../S1434_launcher-static-striped-wallpaper.md`](../S1434_launcher-static-striped-wallpaper.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 0 / 1
**Started:** -
**Completed:** -

---

## Objective

Cover `LauncherWallpaper.fromMode` with a unit test that pins every token, the unknown-token fallback and the missing-image fallback.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/launcher/LauncherWallpaperFromModeTest.kt` | New | ≤ 90 |

> **Flavor placement.** The test targets `src/main` domain code and lives in the shared `src/test` source set, so it compiles under every flavor's unit-test variant and asserts nothing that is flavor-specific.

---

## Steps

### Step 04.1 - Write `LauncherWallpaperFromModeTest`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/launcher/LauncherWallpaperFromModeTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a JUnit test class with one assertion per case: each of the four tokens in `AppSettings.LAUNCHER_WALLPAPER_MODES` maps to its variant; an unrecognised token maps to `Branded`; the image token with a blank path maps to `Branded`; the image token with a non-blank path and `imageExists = false` maps to `Branded`; the image token with a non-blank path and `imageExists = true` maps to `Image` carrying that path. Add one assertion that `LAUNCHER_WALLPAPER_MODES` has four entries and that every entry maps to a distinct variant, so a future token added to the list without a mapper branch fails here instead of silently rendering the branded animation.

**Why:**

Strategic §7 records that no class on this path is covered by tests and that a regression would therefore pass unnoticed, and its stated mitigation is exactly a unit test of the "settings string -> wallpaper variant" mapping.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/launcher/LauncherWallpaperFromModeTest.kt` exists.
- `Grep` - `LauncherWallpaper.fromMode(` matches at least seven times in that file.
- `pwsh -NoProfile -File ./a.ps1 fu` reports this class green; a pre-existing failure elsewhere is recorded, not fixed here.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

The mode list and the mapper are now pinned together by a test, so the final phase can regenerate the settings documentation without re-verifying the mapping by hand.

---

## Rollback Plan

Revert phase commit(s) - test-only change, no production code touched.
