# Phase 01 - Documentation-scope surface catalog

**Strategic spec:** [`../S1313_settings-manifest-blind-to-dialog-settings.md`](../S1313_settings-manifest-blind-to-dialog-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05
**Steps done:** 2 / 2
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Introduce `SettingsDocScopeCatalog` - the list of layouts that host user-facing settings outside `fragment_settings_*`, each with the reference section and settings tab it documents under. No exporter, renderer, or search behaviour changes yet.

---

## Prerequisites

- [ ] INDEX.md "Owner scope call" blocker is resolved; the surface list below matches the owner's decision.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsDocScopeCatalog.kt` | New | ≤ 90 |

> No layout files are edited in this phase, so the landscape-parity rule does not apply.

---

## Steps

### Step 01.1 - Add the `DocScopeSurface` type and catalog object

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsDocScopeCatalog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `SettingsDocScopeCatalog.kt` in the `ui.settings.search` package. Declare `data class DocScopeSurface(val layoutResId: Int, val sectionId: String, val destination: SettingsSearchDestination, val hostKey: String)` where `hostKey` is the manifest key of the row or button in a settings screen that opens this surface. Declare `object SettingsDocScopeCatalog` with `val surfaces: List<DocScopeSurface>` holding one entry per approved layout, and `fun sectionFor(layoutResId: Int): DocScopeSurface?` backed by a `Map<Int, DocScopeSurface>` built from `surfaces`. Do not touch `SettingsSearchLayoutCatalog` or `SettingsSearchTabMapping` - the in-app search index must not change.

**Surface table actually encoded** (`layoutResId` / `sectionId` / `destination` / `hostKey`) - corrected
against live code, see "Deviation from plan" below:

- `R.layout.dialog_launcher_settings` / `launcher` / `GENERAL` / `rowLauncherSettings`
- `R.layout.dialog_edge_gesture_config` / `gestures` / `OPERATIONS` / `btnOpenEdgeGestureConfig`
- `R.layout.dialog_default_apps` / `defaultApps` / `OPERATIONS` / `btnOpenDefaultAppsDialog`
- `R.layout.dialog_camera_settings` / `camera` / `OPERATIONS` / `""`
- `R.layout.dialog_camera_ocr_settings` / `camera` / `OPERATIONS` / `""`
- `R.layout.dialog_translation_settings` / `translation` / `MEDIA` / `""`

**Deviation from plan.** The original table (see git-free history is not authoritative; this note is the
record) proposed 9 surfaces and used `btnSelectCameraPhotosDest` as the hostKey for both camera dialogs.
Verification against live code found: (1) `btnSelectCameraPhotosDest` is the "select camera-photos
destination folder" picker in `fragment_settings_destinations.xml`, unrelated to opening either camera
dialog - no settings-screen control opens `dialog_camera_settings`/`dialog_camera_ocr_settings` at all
(both open only from the camera-capture/OCR flow), so both now carry an empty `hostKey`; (2)
`dialog_player_settings`, `dialog_playback_control`, `dialog_slideshow_settings` were dropped entirely -
Phase 02's regeneration showed they produce mostly non-setting noise (playback transport controls) or
incomplete coverage (a `Slider` widget kind `LayoutSettingsSearchSource.kindFromTag` does not recognize).
Full reasoning: strategic spec §3 "Corrections 1/2".

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsDocScopeCatalog.kt` exists.
- `Grep` - `object SettingsDocScopeCatalog` matches exactly once.
- `Grep` - `data class DocScopeSurface` matches exactly once.
- `Grep` - `fun sectionFor(layoutResId: Int): DocScopeSurface?` matches exactly once.
- `Grep` - `R\.layout\.dialog_` returns 6 hits in that file (9 planned, corrected to 6 - see "Deviation from plan" above).
- `Grep` - `SettingsSearchLayoutCatalog` returns 3 hits, all inside the class KDoc explaining WHY this
  catalog is disjoint from it (S1035 rationale) - zero hits in executable code (no import, no reference
  in `surfaces`/`sectionFor`). The plan's literal "zero hits" predicate did not anticipate a KDoc
  cross-reference; the actual invariant (no code coupling) holds.

**Status:** `[x] done`

---

### Step 01.2 - Document the `hostKey` contract and assert it holds

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsDocScopeCatalog.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a KDoc line on `hostKey` stating the contract: a non-empty value must name a view id already present in `docs/settings/settings-manifest.json`, and an empty value means the surface has no settings-screen entry point and is reachable only from the player or media UI. Then verify the contract holds for the table written in step 01.1 by grepping the committed manifest for every non-empty `hostKey`. A non-empty key that is absent from the manifest is a bug in the table, not in the manifest - fix the table.

**Verification:**

- `Grep` - for each non-empty `hostKey` literal in `SettingsDocScopeCatalog.kt`, the same literal matches in `docs/settings/settings-manifest.json`. Verified: `rowLauncherSettings`, `btnOpenEdgeGestureConfig`, `btnOpenDefaultAppsDialog` all present.
- Value equality - exactly 3 surfaces carry a non-empty `hostKey` and exactly 3 carry `""` (5/4 planned, corrected to 3/3 - see Step 01.1 "Deviation from plan").
- `Grep` - the `hostKey` KDoc stating the empty-string meaning matches once in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exits 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `Grep -n "Log\.d\("` returns zero hits in the new file.
- [x] Dev log entry added for the new file via `.\scripts\add_to_dev_log.ps1` (batched with the ticket's Phase 06 closure entry).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (pure static data object, no lifecycle/DI/Room/coroutine surface).

---

## Handoff Notes to Next Phase

`SettingsDocScopeCatalog.surfaces` is the single source of truth for documentation-only settings surfaces. `SettingsSearchLayoutCatalog` remains the source of truth for the navigable in-app index; the two lists are disjoint and Phase 05 gates that they stay disjoint and jointly exhaustive.

---

## Rollback Plan

Delete the new file - nothing consumes it until Phase 02. No data migration or user-facing surface changed.
