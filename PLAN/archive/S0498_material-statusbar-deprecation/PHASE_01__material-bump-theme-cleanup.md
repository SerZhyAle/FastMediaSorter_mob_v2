# Phase 01 - Material Bump + Theme Cleanup

**Strategic spec:** [`../S0498_material-statusbar-deprecation.md`](../S0498_material-statusbar-deprecation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-06-18
**Completed:** 2026-06-18

---

## Objective

Bump Material Components to 1.14.0 (removes the deprecated `setStatusBarColor` call on API 35) and surgically strip the now-obsolete edge-to-edge override plus its false comments from the Android-15 theme overlays, retaining the system-window padding.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Material Components 1.14.0 resolvable from Google Maven (already a configured repo).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | 1 line (≥500 LOC file → backup first) |
| `app_v2/src/main/res/values-v35/themes.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/values-night-v35/themes.xml` | Modified | ≤ 30 |

> `app_v2/build.gradle.kts` exceeds 500 LOC: write a timestamped backup under `temp/` before editing (CLAUDE.md Rule 5). The two theme files are < 40 LOC each - no backup needed.
> Landscape parity: theme XML is orientation-independent; no `layout-land` counterpart involved.

---

## Steps

### Step 01.1 - Bump Material Components 1.13.0 → 1.14.0

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `app_v2/build.gradle.kts` to `temp/` with a timestamp first (file > 500 LOC). Then change the single dependency line `implementation("com.google.android.material:material:1.13.0")` to `:1.14.0`. Do not touch any other dependency. No version catalog exists - this is the only coordinate.

**Verification:**

- `Grep` - `com.google.android.material:material:1.14.0` matches exactly once in `app_v2/build.gradle.kts`.
- `Grep` - `com.google.android.material:material:1.13.0` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-18 - Verification 2/2 PASS. Files: app_v2/build.gradle.kts (material 1.13.0 → 1.14.0). post-change Config PASS (doc-pins drift 0). Dev log recorded. Backup: temp/build.gradle.kts.S0498-bak-20260618-030200.

---

### Step 01.2 - Strip obsolete edge-to-edge override in values-v35/themes.xml

**Files:** `app_v2/src/main/res/values-v35/themes.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the `Theme.App.BottomSheetDialog.EdgeToEdgeCompat` style, remove the now-deprecated `<item name="enableEdgeToEdge">true</item>` and the dead `<item name="android:navigationBarColor">@android:color/transparent</item>` (both are no-ops on API 35 with Material 1.14.0). Retain the three `padding*SystemWindowInsets` items so sheet content keeps its inset padding. Keep the `bottomSheetDialogTheme` reference and the empty `Theme.FastMediaSorter` / `Theme.FastMediaSorter.FullScreen` definitions untouched - they govern API-35 theme fallback and are out of scope. Replace the top comment block: drop the false claim that "Material 1.13.0+ rewrote BottomSheetDialog .. instead of setStatusBarColor"; state instead that Material 1.14.0 guards `setStatusBarColor` behind an SDK_INT < 35 check and handles bottom-sheet edge-to-edge natively, so only padding insets remain.

**Verification:**

- `Grep` - `<item name="enableEdgeToEdge"` returns zero hits in `values-v35/themes.xml` (item-level; the accurate comment legitimately names the term).
- `Grep` - `<item name="android:navigationBarColor"` returns zero hits in `values-v35/themes.xml`.
- `Grep` - `rewrote BottomSheetDialog` returns zero hits in `values-v35/themes.xml`.
- `Grep` - `paddingBottomSystemWindowInsets` still present in `values-v35/themes.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-18 - Verification 4/4 PASS (item-level greps; `<item name="enableEdgeToEdge"` and `<item name="android:navigationBarColor"` = 0, `rewrote BottomSheetDialog` = 0, `paddingBottomSystemWindowInsets` present). post-change Xml PASS (neuroslop baselines unchanged). Dev log recorded.

---

### Step 01.3 - Mirror the cleanup in values-night-v35/themes.xml

**Files:** `app_v2/src/main/res/values-night-v35/themes.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Apply the exact same edits as Step 01.2 to the night-mode Android-15 overlay: remove `enableEdgeToEdge` and `android:navigationBarColor`, retain the `padding*SystemWindowInsets` items, replace the false comment with the accurate one. This file must stay in lockstep with `values-v35/themes.xml` (night overrides outrank `values-v35` during resource resolution, so a portrait/day-only fix would leave dark mode stale).

**Verification:**

- `Grep` - `<item name="enableEdgeToEdge"` returns zero hits in `values-night-v35/themes.xml` (item-level; the accurate comment legitimately names the term).
- `Grep` - `<item name="android:navigationBarColor"` returns zero hits in `values-night-v35/themes.xml`.
- `Grep` - `rewrote BottomSheetDialog` returns zero hits in `values-night-v35/themes.xml`.
- `Grep` - `paddingBottomSystemWindowInsets` still present in `values-night-v35/themes.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-18 - Verification 4/4 PASS (item-level greps = 0; `paddingBottomSystemWindowInsets` present). Mirrors Step 01.2. post-change Xml PASS (neuroslop baselines unchanged). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - standard-debug assembled with Material 1.14.0 (`.\a.ps1 d` → BUILD SUCCESSFUL in 1m 52s, exit 0; log temp/S0498_build.log).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `app_v2/build.gradle.kts`, `values-v35/themes.xml`, `values-night-v35/themes.xml` via post-change.
- [x] No `dev/CATALOG` regeneration from the planned files; the finalization debug-tag in `SendToBottomSheet.kt` changes no public API.

---

## Handoff Notes to Next Phase

- The library bump is what clears the Play Console warning; the theme edits are hygiene (remove the deprecated attribute + correct the false comment), not the fix.
- Padding insets retained deliberately: if the device pass shows bottom-sheet content clipped by the navigation bar, the mitigation is to keep/adjust `padding*SystemWindowInsets` - never to re-add `enableEdgeToEdge` or a status/nav bar color (strategic §7).
- Device verification (strategic §6.2 / §11.2 / §11.3) happens after build: exercise the bottom sheets - share sheet, PDF thumbnail sheet, icon picker, now-playing, permission rationale, stream-offload offer, wear-sync settings - in portrait + landscape, light + dark on an Android 15 device.

---

## Rollback Plan

Revert the phase commit(s) - a dependency version line and two theme overlays, no data migration or persisted state. Restore `app_v2/build.gradle.kts` from the `temp/` backup if needed.
