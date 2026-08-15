# Phase 01 - Upload-target rows to button pattern

**Strategic spec:** [`../S0648_settings-upload-target-rows-unification.md`](../S0648_settings-upload-target-rows-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** S0644 (etalon resolved; owner picked button pattern for these rows)
**Steps done:** 3 / 3
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Migrate the two upload-target rows ("Resource for downloads" = `row_link_autodownload_resource`; "Upload screenshots to.." = `rowScreenshotDestination`) from `SettingsSelectionRow` to the camera-folder selector pattern (vertical: title TextView + horizontal [value TextView weight=1, ellipsized] + outlined "Select" `MaterialButton`), in portrait and landscape, preserving the existing destination-picker behavior and value refresh.

---

## Files Touched

| File | New / Modified |
|------|:--------------:|
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsGesturesManager.kt` | Modified |
| `docs/settings/settings-manifest.json` + `settings-annotations.json` + `SETTINGS_REFERENCE*.md` | Regenerated |

---

## Steps

### Step 01.1 - Replace both rows with the button pattern (portrait + landscape)

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Both rows replaced in portrait + landscape with the camera-folder reference pattern. New ids: `layoutLinkAutodownloadResourceSelector` / `tvLinkAutodownloadResource` / `btnSelectLinkAutodownloadResource`; `layoutScreenshotDestinationSelector` / `tvScreenshotDestination` / `btnSelectScreenshotDestination`. Landscape ROW 1 was un-nested from a weighted horizontal slot into a full-width block (open-in-player row stacked below), required to apply the full-width reference pattern. Zero remaining old-id references.

### Step 01.2 - Rewire picker + value refresh + enabled state

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - `setOnRowClickListener` -> `btnSelect*.setOnClickListener` (picker unchanged); `setValue(it)` -> `tv*.text = it`; autodownload enabled state applied to button + value text. Debug tags `Timber.d("S0648: ..")` added at each button click handler.

### Step 01.3 - Build + settings-doc sync

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - BUILD SUCCESSFUL (APK v2.60.6211.547-DEBUG). Neuroslop delta 0. Manifest regenerated (old row keys -> `btnSelectLinkAutodownloadResource`/`btnSelectScreenshotDestination`), annotations renamed, reference re-rendered, `assert-settings-doc-sync.ps1` exits 0.

---

## Phase Done Criteria

- [x] Steps `[x] done`.
- [x] Project compiles.
- [x] Both portrait + landscape edited.
- [x] Settings doc-sync green.

---

## Rollback Plan

Revert the four file edits + regenerated docs: rows return to `SettingsSelectionRow`.
