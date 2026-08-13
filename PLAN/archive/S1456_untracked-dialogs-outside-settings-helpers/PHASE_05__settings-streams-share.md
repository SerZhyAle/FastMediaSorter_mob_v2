# Phase 05 - Settings, streams and share

**Strategic spec:** [`../S1456_untracked-dialogs-outside-settings-helpers.md`](../S1456_untracked-dialogs-outside-settings-helpers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 4 / 4
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Bind the dialogs of the settings surfaces outside `ui/settings/helpers/`, of the streams screen and of the share pipeline (15 files, 29 sites measured 2026-08-09).

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `assert-untracked-dialogs.ps1 -List` re-read; edit what it prints.
- [ ] `CODE.LOCK` acquired immediately before each step and released right after.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/*.kt` (4 files) | Modified | ≤ 1500 each |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListFragment.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigManager.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/*.kt` (3 files) | Modified | ≤ 1500 each |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/*.kt` (5 files) | Modified | ≤ 1500 each |

---

## Steps

### Step 05.1 - Bind the settings fragment dialogs

**Files:** `ui/settings/fragments/MediaSettingsFragment.kt`, `OperationsSettingsFragment.kt`, `PlaybackSettingsFragment.kt`, `StreamsSettingsFragment.kt`, `ui/settings/LauncherSettingsDialogFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Bind the seven builder chains in these five fragments with the `showBoundTo(fragment)` overload, passing `this`, so the dialog follows the view lifecycle rather than the fragment's own.

**Why:**

Strategic §4 records that the fragment overload binds to the view lifecycle, which dies on every configuration change and is therefore the owner a dialog raised from a fragment view must follow.

**Verification:**

- `assert-untracked-dialogs.ps1 -List` prints no row for these five files.
- `Grep` - `showBoundTo(this)` matches in each of the five files.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 05.2 - Bind the auth-session and edge-gesture dialogs

**Files:** `ui/settings/auth/AuthSessionsListFragment.kt`, `ui/settings/gesture/EdgeGestureConfigManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Bind the seven builder chains in these two files. The gesture configuration manager is not a fragment: take the owner it was constructed with, or add it as a constructor parameter and update the construction site.

**Why:**

Strategic §1 measures seven sites here, and §7 names the manager without a host in scope as the case that changes a signature.

**Verification:**

- `assert-untracked-dialogs.ps1 -List` prints no row for these two files.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 05.3 - Bind the streams dialogs

**Files:** `ui/streams/StreamsActivity.kt`, `ui/streams/StreamRemoveConfirmation.kt`, `ui/streams/helpers/StreamAtlasPromptManager.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Bind the six builder chains in these three files: `this` inside `StreamsActivity`, the activity passed in for the confirmation and the prompt manager.
> Re-read all three from disk immediately before editing - a sibling session was working the streams area on 2026-08-09.

**Why:**

Strategic §7 warns that concurrent sessions are editing neighbouring files, so an edit written against a stale read would silently drop their work.

**Verification:**

- `assert-untracked-dialogs.ps1 -List` prints no row under `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 05.4 - Bind the share pipeline dialogs

**Files:** `ui/share/LinkAutoDownloadProgressDialog.kt`, `LinkAutoDownloadResultPresenter.kt`, `ReceiveShareActivity.kt`, `ui/share/auth/WebViewAuthDialogFragment.kt`, `ui/share/helpers/AccountSelectionManager.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Bind the nine builder chains in these five files. The progress dialog and the result presenter keep their dialog reference for updates: keep the reference, and let the binding own only the dismissal.

**Why:**

Strategic §4 records that `showBoundTo` returns the shown dialog, so a site that needs the reference keeps it while still getting the lifecycle dismissal.

**Verification:**

- `assert-untracked-dialogs.ps1 -List` prints no row under `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `assert-untracked-dialogs.ps1 -List` prints no row under `ui/settings/`, `ui/streams/` or `ui/share/`.
- [ ] `pwsh -NoProfile -File scripts/quality/assert-untracked-dialogs.ps1 -UpdateBaseline` ratchets the baseline down.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Dev log entry added for the phase via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

## Step Log

- 2026-08-09 - 05.1 to 05.4 PASS. 29 edits across 15 files through `temp/S1456/apply-binding.ps1` with `temp/S1456/step-05.csv`; three import blocks reordered. Fragment hosts take `showBoundTo(this@Fragment)` so the dialog follows the view lifecycle; everything else takes `showBoundToHost`.
- 2026-08-09 - One compile error: `ReceiveShareActivity.showLoadingDialog()` declares a non-null `AlertDialog` return. Rewritten to `create()` then bind then return, which keeps the signature and every caller untouched.
- 2026-08-09 - `.\a.ps1 fk` BUILD SUCCESSFUL. Baseline ratcheted 64 -> 35.
- 2026-08-09 - Phase-boundary audit, Layers 1-3: no P0/P1. The settings fragments now bind to the view lifecycle rather than the fragment's, which is the stricter of the two and matches what `LifecycleDialogExt` documents.
- 2026-08-09 - `post-change.ps1 -ScopeToFile` over the 15-file set: `post-change: PASS`, exit 0.

---
## Handoff Notes to Next Phase

The nullable return of `showBoundTo` is handled at every site that needs the dialog reference; Phase 06 meets the same shape in the tooltip and picker dialogs.

---

## Rollback Plan

Revert the phase commits - dialog ownership only, no data migration and no user-facing surface change.
