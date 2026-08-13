# Phase 04 - Dialog UI (type picker)

**Strategic spec:** [`../S1145_stream-edit-parameters-dialog.md`](../S1145_stream-edit-parameters-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-07-22
**Completed:** 2026-07-22

---

## Objective

Add a media-kind picker (Auto / Audio / Video) to the shared add/edit dialog, shown only in edit mode, pre-selected from the channel's current kind, and forward the chosen override to `viewModel.onEdit`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (type-label + option strings exist).
- [ ] Phase 03 is ✅ Done (`onEdit(source, url, title, override)` and `resolveEditKindOption(source)` exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_add_stream.xml` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 870 |

> Landscape: `res/layout-land/dialog_add_stream.xml` does not exist (dialog uses a single layout) - no landscape counterpart to edit.

---

## Steps

### Step 04.1 - Add the media-kind toggle group to the dialog layout

**Files:** `app_v2/src/main/res/layout/dialog_add_stream.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `xmlns:app="http://schemas.android.com/apk/res-auto"` to the root `LinearLayout`. After `tilTitle`, add a vertical container `LinearLayout` `@+id/mediaKindContainer` with `android:visibility="gone"` (edit-only; the add/import dialog leaves it hidden) and `android:layout_marginTop="12dp"`, holding:
> - a `TextView` `@+id/tvMediaKindLabel`, `android:text="@string/streams_edit_type_label"`, `android:textAppearance="?attr/textAppearanceLabelLarge"`.
> - a `com.google.android.material.button.MaterialButtonToggleGroup` `@+id/toggleMediaKind` (`app:singleSelection="true"`, `app:selectionRequired="true"`, `layout_marginTop="4dp"`) with three `com.google.android.material.button.MaterialButton` children `@+id/btnKindAuto`, `@+id/btnKindAudio`, `@+id/btnKindVideo`, each `style="?attr/materialButtonOutlinedStyle"`, `layout_width="0dp"`, `layout_weight="1"`, texts `@string/streams_edit_type_auto` / `_audio` / `_video`.
> No hardcoded colors (Rule 19) - rely on the Material outlined style / theme attrs. The text labels make the options distinguishable without color (accessibility, strategic §3.2).

**Verification:**

- `Grep` - `@+id/toggleMediaKind` and `@+id/mediaKindContainer` in `dialog_add_stream.xml`.
- `Grep` - `@+id/btnKindAuto`, `@+id/btnKindAudio`, `@+id/btnKindVideo` all present.
- `Grep` - `android:visibility="gone"` on the container.
- `Grep` - no `="#` hex color literal added to the file.

**Status:** `[x]` done

---

### Step 04.2 - Wire the picker in showEditDialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `showEditDialog(source)`: after pre-filling url/title, set `dialogBinding.mediaKindContainer.isVisible = true` and pre-select the toggle from `viewModel.resolveEditKindOption(source)` - `"AUDIO"` -> `dialogBinding.toggleMediaKind.check(R.id.btnKindAudio)`, `"VIDEO"` -> `check(R.id.btnKindVideo)`, else `check(R.id.btnKindAuto)`. In the positive-button lambda, compute the override from `dialogBinding.toggleMediaKind.checkedButtonId` (`R.id.btnKindAudio` -> `"AUDIO"`, `R.id.btnKindVideo` -> `"VIDEO"`, else `null` for Auto) and call `viewModel.onEdit(source, url, title, override)` with the existing url/title reads. Do not touch `showSourceDialog` - the add/import dialog keeps `mediaKindContainer` hidden by default.

**Verification:**

- `Grep` - `mediaKindContainer.isVisible = true` in `StreamsActivity.kt`.
- `Grep` - `resolveEditKindOption(source)` referenced in `showEditDialog`.
- `Grep` - `toggleMediaKind.checkedButtonId` referenced.
- `Grep` - `viewModel.onEdit(source,` now passes a fourth argument (override).
- `Grep -n "Log\.d\("` in the file returns zero hits.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit - no unresolved P0/P1 (Layer 1: Activity stays presentation-only, the classification decision lives in the ViewModel; no lifecycle/listener change).

---

## Handoff Notes to Next Phase

Edit dialog now shows and persists the stream type and never crashes on a duplicate URL. Phase 05 records the capability change and regenerates the catalog.

---

## Rollback Plan

Revert both edits - remove the toggle block from the layout and the pre-select/override wiring from `showEditDialog`; `onEdit` still accepts the override (harmless, defaults to auto).
